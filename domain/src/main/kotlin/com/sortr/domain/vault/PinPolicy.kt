package com.sortr.domain.vault

/** Rules for the vault PIN and how wrong guesses are slowed down. */
object PinPolicy {
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 12

    /** Wrong guesses allowed before the first wait. */
    const val FREE_ATTEMPTS = 5

    private const val FIRST_LOCKOUT_MS = 30_000L
    private const val MAX_LOCKOUT_MS = 15 * 60_000L

    fun isValid(pin: String): Boolean = pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it in '0'..'9' }

    /**
     * How long to block guessing after [failures] wrong PINs in a row: nothing for the first
     * few, then 30 seconds, doubling each time up to 15 minutes.
     */
    fun lockoutAfter(failures: Int): Long {
        if (failures < FREE_ATTEMPTS) return 0
        val doublings = (failures - FREE_ATTEMPTS).coerceAtMost(10)
        return (FIRST_LOCKOUT_MS shl doublings).coerceAtMost(MAX_LOCKOUT_MS)
    }

    /** Wrong guesses left before the next wait. */
    fun attemptsBeforeLockout(failures: Int): Int = (FREE_ATTEMPTS - failures).coerceAtLeast(0)
}

sealed interface PinCheck {
    data object Correct : PinCheck

    data class Wrong(val attemptsBeforeLockout: Int) : PinCheck

    data class LockedOut(val untilMillis: Long) : PinCheck
}
