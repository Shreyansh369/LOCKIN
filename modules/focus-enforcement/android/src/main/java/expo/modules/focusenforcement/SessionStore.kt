package expo.modules.focusenforcement

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Native-owned session state. JS may be dead when enforcement fires (service, accessibility
 * service, boot receiver), so the source of truth lives in SharedPreferences, not in JS.
 * commit() is used for writes that must survive an immediate process kill.
 */
data class SessionState(
  val sessionId: String,
  val task: String,
  val startedAt: Long,
  val endsAt: Long,
  val allowlist: Set<String>,
  val mode: String,
  val breaksUsed: Int,
  val breakUntil: Long,
  val breakPackages: Set<String>,
  val emergencyUnlockAt: Long,
  val blockCount: Int
) {
  fun isBreakActive(now: Long) = breakUntil > now
  fun toMap(now: Long): Map<String, Any?> = mapOf(
    "active" to true,
    "sessionId" to sessionId,
    "task" to task,
    "startedAt" to startedAt.toDouble(),
    "endsAt" to endsAt.toDouble(),
    "remainingMs" to (endsAt - now).coerceAtLeast(0).toDouble(),
    "allowlist" to allowlist.toList(),
    "mode" to mode,
    "breaksUsed" to breaksUsed,
    "breaksRemaining" to (MAX_BREAKS - breaksUsed).coerceAtLeast(0),
    "breakUntil" to if (isBreakActive(now)) breakUntil.toDouble() else null,
    "breakPackages" to breakPackages.toList(),
    "emergencyUnlockAt" to if (emergencyUnlockAt > 0) emergencyUnlockAt.toDouble() else null,
    "blockCount" to blockCount
  )

  companion object {
    const val MAX_BREAKS = 2
    const val MAX_BREAK_MINUTES = 15
    const val EMERGENCY_UNLOCK_MS = 15 * 60 * 1000L
  }
}

class SessionStore private constructor(context: Context) {
  private val prefs: SharedPreferences =
    context.getSharedPreferences("lockin_session", Context.MODE_PRIVATE)
  private val events: SharedPreferences =
    context.getSharedPreferences("lockin_events", Context.MODE_PRIVATE)

  @Synchronized
  fun load(): SessionState? {
    if (!prefs.getBoolean(K_ACTIVE, false)) return null
    return SessionState(
      sessionId = prefs.getString(K_ID, "") ?: "",
      task = prefs.getString(K_TASK, "") ?: "",
      startedAt = prefs.getLong(K_STARTED, 0),
      endsAt = prefs.getLong(K_ENDS, 0),
      allowlist = prefs.getStringSet(K_ALLOW, emptySet())?.toSet() ?: emptySet(),
      mode = prefs.getString(K_MODE, "standard") ?: "standard",
      breaksUsed = prefs.getInt(K_BREAKS_USED, 0),
      breakUntil = prefs.getLong(K_BREAK_UNTIL, 0),
      breakPackages = prefs.getStringSet(K_BREAK_PKGS, emptySet())?.toSet() ?: emptySet(),
      emergencyUnlockAt = prefs.getLong(K_EMERGENCY_AT, 0),
      blockCount = prefs.getInt(K_BLOCKS, 0)
    )
  }

  @Synchronized
  fun start(s: SessionState) {
    prefs.edit()
      .putBoolean(K_ACTIVE, true)
      .putString(K_ID, s.sessionId)
      .putString(K_TASK, s.task)
      .putLong(K_STARTED, s.startedAt)
      .putLong(K_ENDS, s.endsAt)
      .putStringSet(K_ALLOW, s.allowlist)
      .putString(K_MODE, s.mode)
      .putInt(K_BREAKS_USED, 0)
      .putLong(K_BREAK_UNTIL, 0)
      .putStringSet(K_BREAK_PKGS, emptySet())
      .putLong(K_EMERGENCY_AT, 0)
      .putInt(K_BLOCKS, 0)
      .commit()
  }

  @Synchronized
  fun startBreak(until: Long, packages: Set<String>) {
    prefs.edit()
      .putInt(K_BREAKS_USED, prefs.getInt(K_BREAKS_USED, 0) + 1)
      .putLong(K_BREAK_UNTIL, until)
      .putStringSet(K_BREAK_PKGS, packages)
      .commit()
  }

  @Synchronized
  fun clearBreak() {
    prefs.edit().putLong(K_BREAK_UNTIL, 0).putStringSet(K_BREAK_PKGS, emptySet()).commit()
  }

  @Synchronized
  fun setEmergencyUnlockAt(at: Long) {
    prefs.edit().putLong(K_EMERGENCY_AT, at).commit()
  }

  @Synchronized
  fun incrementBlocks(): Int {
    val n = prefs.getInt(K_BLOCKS, 0) + 1
    prefs.edit().putInt(K_BLOCKS, n).apply()
    return n
  }

  @Synchronized
  fun clear() {
    prefs.edit()
      .putBoolean(K_ACTIVE, false)
      .remove(K_ID).remove(K_TASK).remove(K_STARTED).remove(K_ENDS).remove(K_ALLOW)
      .remove(K_MODE).remove(K_BREAKS_USED).remove(K_BREAK_UNTIL).remove(K_BREAK_PKGS)
      .remove(K_EMERGENCY_AT).remove(K_BLOCKS)
      .commit()
  }

  // Packages this app suspended as Device Owner, so revert never depends on recomputation.
  @Synchronized
  fun suspendedPackages(): Set<String> = prefs.getStringSet(K_SUSPENDED, emptySet())?.toSet() ?: emptySet()

  @Synchronized
  fun setSuspendedPackages(pkgs: Set<String>) {
    prefs.edit().putStringSet(K_SUSPENDED, pkgs).commit()
  }

  var profanityEnabled: Boolean
    @Synchronized get() = prefs.getBoolean(K_PROFANITY, false)
    @Synchronized set(value) {
      prefs.edit().putBoolean(K_PROFANITY, value).apply()
    }

  /** Append-only queue that JS drains into SQLite on next open. */
  @Synchronized
  fun enqueueEvent(type: String, fields: Map<String, Any?> = emptyMap()) {
    val queue = JSONArray(events.getString(K_QUEUE, "[]"))
    val e = JSONObject().put("type", type).put("ts", System.currentTimeMillis())
    load()?.let { e.put("sessionId", it.sessionId) }
    for ((k, v) in fields) e.put(k, v ?: JSONObject.NULL)
    queue.put(e)
    // Hard cap so a runaway loop can't grow prefs without bound; oldest entries go first.
    val trimmed = if (queue.length() > MAX_QUEUE) {
      JSONArray().also { out -> for (i in queue.length() - MAX_QUEUE until queue.length()) out.put(queue.get(i)) }
    } else queue
    events.edit().putString(K_QUEUE, trimmed.toString()).commit()
  }

  @Synchronized
  fun drainEvents(): List<Map<String, Any?>> {
    val queue = JSONArray(events.getString(K_QUEUE, "[]"))
    events.edit().putString(K_QUEUE, "[]").commit()
    return (0 until queue.length()).map { i ->
      val o = queue.getJSONObject(i)
      o.keys().asSequence().associateWith { k ->
        when (val v = o.get(k)) {
          JSONObject.NULL -> null
          is Long -> v.toDouble()
          is Int -> v.toDouble()
          else -> v
        }
      }
    }
  }

  companion object {
    private const val K_ACTIVE = "active"
    private const val K_ID = "sessionId"
    private const val K_TASK = "task"
    private const val K_STARTED = "startedAt"
    private const val K_ENDS = "endsAt"
    private const val K_ALLOW = "allowlist"
    private const val K_MODE = "mode"
    private const val K_BREAKS_USED = "breaksUsed"
    private const val K_BREAK_UNTIL = "breakUntil"
    private const val K_BREAK_PKGS = "breakPackages"
    private const val K_EMERGENCY_AT = "emergencyUnlockAt"
    private const val K_BLOCKS = "blockCount"
    private const val K_SUSPENDED = "suspendedPackages"
    private const val K_PROFANITY = "profanity"
    private const val K_QUEUE = "queue"
    private const val MAX_QUEUE = 5000

    @Volatile private var instance: SessionStore? = null

    fun get(context: Context): SessionStore =
      instance ?: synchronized(this) {
        instance ?: SessionStore(context.applicationContext).also { instance = it }
      }
  }
}
