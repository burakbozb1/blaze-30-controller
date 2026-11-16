package dev.blaze.controller.ui

/**
 * Remembers what a delay/reverb panel was set to before it was switched "off" (master level 0), so switching it
 * back on restores that level. The amp has no separate on/off flag: off simply is level 0.
 *
 * A toggle also remembers the level it just asked for for [pendingMs], because the amp state only updates after the
 * write is acknowledged; without that a quick second tap would read the stale state and repeat the first tap.
 */
class PowerMemory(
    private val defaultLevel: Int = 20,
    private val pendingMs: Long = 1_000,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private var remembered: Int? = null
    private var requested: Int? = null
    private var requestedAt = 0L

    /** Feed every level the amp state shows (set by the app, the amp's knobs or a reconnect dump). */
    fun observe(level: Int) {
        if (level > 0) remembered = level
        if (requested == level) requested = null
    }

    /** Level to write when the user taps the power button, given the level currently shown. */
    fun levelAfterToggle(current: Int): Int {
        val pending = requested?.takeIf { now() - requestedAt <= pendingMs }
        val effective = pending ?: current
        val next = if (effective > 0) 0 else (remembered ?: defaultLevel)
        requested = next
        requestedAt = now()
        return next
    }
}
