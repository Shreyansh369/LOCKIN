package expo.modules.focusenforcement

import android.content.Context
import expo.modules.kotlin.exception.CodedException
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.kotlin.records.Field
import expo.modules.kotlin.records.Record
import java.util.UUID

class StartSessionOptions : Record {
  @Field val task: String = ""
  @Field val durationMin: Int = 0
  @Field val allowlist: List<String> = emptyList()
  @Field val mode: String = "standard"
}

class LockinException(message: String) : CodedException(message)

/**
 * JS bridge. Expo Modules API: https://docs.expo.dev/modules/module-api/
 * (verified against expo-modules-core 58 sources in node_modules).
 * Session state lives natively (SessionStore); JS only reads it and sends commands.
 */
class FocusEnforcementModule : Module() {
  private val context: Context
    get() = appContext.reactContext ?: throw LockinException("React context unavailable")

  private val listener = object : EventBus.Listener {
    override fun onBlocked(pkg: String, ts: Long) {
      sendEvent("onBlocked", mapOf("pkg" to pkg, "ts" to ts.toDouble()))
    }

    override fun onSessionEnded(type: String, ts: Long) {
      sendEvent("onSessionEnded", mapOf("type" to type, "ts" to ts.toDouble()))
    }
  }

  override fun definition() = ModuleDefinition {
    Name("FocusEnforcement")

    Events("onBlocked", "onSessionEnded")

    OnCreate {
      EventBus.listener = listener
      // Recover after process death: if a session is still running, make sure it is enforced.
      appContext.reactContext?.let { ctx ->
        val state = SessionStore.get(ctx).load()
        if (state != null) {
          if (System.currentTimeMillis() >= state.endsAt) Enforcer.endSession(ctx, "expired", null)
          else EnforcementService.start(ctx)
        }
      }
    }

    OnDestroy {
      if (EventBus.listener === listener) EventBus.listener = null
    }

    Function("getVariant") { Variant.get(context) }

    AsyncFunction("getPermissionStatus") { Permissions.status(context) }

    AsyncFunction("requestPermission") { kind: String ->
      Permissions.request(appContext.currentActivity, context, kind)
    }

    AsyncFunction("listLaunchableApps") { AppList.list(context) }

    AsyncFunction("startSession") { options: StartSessionOptions ->
      val ctx = context
      if (SessionStore.get(ctx).load() != null) throw LockinException("A session is already running")
      val task = options.task.trim()
      if (task.split(Regex("\\s+")).size < 3) throw LockinException("Task needs at least 3 words")
      if (options.durationMin !in 1..720) throw LockinException("Duration must be 1-720 minutes")
      if (options.mode !in setOf("standard", "strict")) throw LockinException("Unknown mode ${options.mode}")
      val st = Permissions.status(ctx)
      val detector = st["usageAccess"] == true || st["accessibility"] == true
      if (!detector || st["overlay"] != true) {
        throw LockinException("Required permissions missing (usage access or accessibility, and overlay)")
      }
      val now = System.currentTimeMillis()
      val state = SessionState(
        sessionId = UUID.randomUUID().toString(),
        task = task,
        startedAt = now,
        endsAt = now + options.durationMin * 60_000L,
        allowlist = options.allowlist.toSet(),
        mode = options.mode,
        breaksUsed = 0,
        breakUntil = 0,
        breakPackages = emptySet(),
        emergencyUnlockAt = 0,
        blockCount = 0
      )
      Enforcer.startSession(ctx, state)
      state.toMap(now)
    }

    AsyncFunction("requestBreak") { minutes: Int, packages: List<String> ->
      try {
        Enforcer.requestBreak(context, minutes, packages.toSet())
      } catch (e: IllegalArgumentException) {
        throw LockinException(e.message ?: "Break refused")
      } catch (e: IllegalStateException) {
        throw LockinException(e.message ?: "Break refused")
      }
    }

    AsyncFunction("endSession") { reason: String ->
      Enforcer.endSession(context, "user", reason)
    }

    AsyncFunction("startEmergencyUnlock") {
      try {
        Enforcer.startEmergencyUnlock(context).toDouble()
      } catch (e: IllegalStateException) {
        throw LockinException(e.message ?: "No active session")
      }
    }

    AsyncFunction("getSessionState") {
      val ctx = context
      Enforcer.tick(ctx)
      SessionStore.get(ctx).load()?.toMap(System.currentTimeMillis()) ?: mapOf("active" to false)
    }

    AsyncFunction("drainEvents") { SessionStore.get(context).drainEvents() }

    AsyncFunction("setProfanityEnabled") { enabled: Boolean ->
      SessionStore.get(context).profanityEnabled = enabled
    }

    // Hardline only: clears Device Owner and deactivates device admin. Refused during a session.
    AsyncFunction("releaseDeviceControls") {
      try {
        DeviceOwnerControls.releaseAll(context)
      } catch (e: IllegalStateException) {
        throw LockinException(e.message ?: "Refused")
      }
    }

    AsyncFunction("getProfanityEnabled") { SessionStore.get(context).profanityEnabled }
  }
}
