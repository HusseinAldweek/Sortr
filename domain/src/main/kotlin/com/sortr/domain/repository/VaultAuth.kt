package com.sortr.domain.repository

import com.sortr.domain.vault.PinCheck
import kotlinx.coroutines.flow.Flow

/** The vault PIN, wrong-guess throttling and backup bookkeeping. */
interface VaultAuth {

    /** True once a PIN has been chosen. */
    val isSetUp: Flow<Boolean>

    val lastBackupAt: Flow<Long?>

    val backupReminderDismissedAt: Flow<Long?>

    suspend fun setUp(pin: String)

    suspend fun verify(pin: String): PinCheck

    /** Changes the PIN if [current] is right. */
    suspend fun changePin(current: String, new: String): PinCheck

    suspend fun markBackedUp(at: Long)

    suspend fun dismissBackupReminder(at: Long)
}
