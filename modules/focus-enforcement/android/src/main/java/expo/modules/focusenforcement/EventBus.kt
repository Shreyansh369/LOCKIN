package expo.modules.focusenforcement

/**
 * In-process bridge from enforcement components to the JS module, used only while the
 * React context is alive. Everything is also written to SessionStore's queue, so a missing
 * listener never loses data.
 */
object EventBus {
  interface Listener {
    fun onBlocked(pkg: String, ts: Long)
    fun onSessionEnded(type: String, ts: Long)
  }

  @Volatile var listener: Listener? = null

  fun blocked(pkg: String, ts: Long) = listener?.onBlocked(pkg, ts)
  fun sessionEnded(type: String, ts: Long) = listener?.onSessionEnded(type, ts)
}
