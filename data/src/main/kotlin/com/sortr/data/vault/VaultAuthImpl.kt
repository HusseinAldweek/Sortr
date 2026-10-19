package com.sortr.data.vault

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sortr.data.crypto.PinHasher
import com.sortr.domain.repository.VaultAuth
import com.sortr.domain.vault.PinCheck
import com.sortr.domain.vault.PinPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

private val Context.vaultAuthStore by preferencesDataStore(name = "vault_auth")

/**
 * Stores only a salted verifier of the PIN (see [PinHasher]), never the PIN itself, plus the
 * wrong-guess counter that drives [PinPolicy] lockouts.
 */
@Singleton
class VaultAuthImpl @Inject constructor(
    @ApplicationContext context: Context,
    private val pinHasher: PinHasher,
) : VaultAuth {

    private val store = context.vaultAuthStore
    private val mutex = Mutex()
    private val random = SecureRandom()

    private val preferences: Flow<Preferences> = store.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    override val isSetUp: Flow<Boolean> = preferences.map { it[VERIFIER] != null }

    override val lastBackupAt: Flow<Long?> = preferences.map { it[LAST_BACKUP_AT] }

    override val backupReminderDismissedAt: Flow<Long?> = preferences.map { it[REMINDER_DISMISSED_AT] }

    override suspend fun setUp(pin: String) {
        require(PinPolicy.isValid(pin)) { "Invalid PIN" }
        mutex.withLock { savePin(pin) }
    }

    override suspend fun verify(pin: String): PinCheck = mutex.withLock { check(pin) }

    override suspend fun changePin(current: String, new: String): PinCheck {
        require(PinPolicy.isValid(new)) { "Invalid PIN" }
        return mutex.withLock {
            val result = check(current)
            if (result == PinCheck.Correct) savePin(new)
            result
        }
    }

    override suspend fun markBackedUp(at: Long) {
        store.edit { it[LAST_BACKUP_AT] = at }
    }

    override suspend fun dismissBackupReminder(at: Long) {
        store.edit { it[REMINDER_DISMISSED_AT] = at }
    }

    private suspend fun check(pin: String): PinCheck {
        val prefs = preferences.first()
        val now = System.currentTimeMillis()
        val lockedUntil = prefs[LOCKED_UNTIL] ?: 0L
        if (lockedUntil > now) return PinCheck.LockedOut(lockedUntil)

        val salt = prefs[SALT]?.decodeBase64() ?: return PinCheck.Wrong(PinPolicy.FREE_ATTEMPTS)
        val verifier = prefs[VERIFIER]?.decodeBase64() ?: return PinCheck.Wrong(PinPolicy.FREE_ATTEMPTS)
        val candidate = withContext(Dispatchers.Default) { pinHasher.hash(salt, pin) }

        if (MessageDigest.isEqual(candidate, verifier)) {
            store.edit {
                it.remove(FAILED_ATTEMPTS)
                it.remove(LOCKED_UNTIL)
            }
            return PinCheck.Correct
        }

        val failures = (prefs[FAILED_ATTEMPTS] ?: 0) + 1
        val lockout = PinPolicy.lockoutAfter(failures)
        store.edit {
            it[FAILED_ATTEMPTS] = failures
            if (lockout > 0) it[LOCKED_UNTIL] = now + lockout
        }
        return if (lockout > 0) PinCheck.LockedOut(now + lockout) else PinCheck.Wrong(PinPolicy.attemptsBeforeLockout(failures))
    }

    private suspend fun savePin(pin: String) {
        val salt = ByteArray(16).also(random::nextBytes)
        val verifier = withContext(Dispatchers.Default) { pinHasher.hash(salt, pin) }
        store.edit {
            it[SALT] = salt.encodeBase64()
            it[VERIFIER] = verifier.encodeBase64()
            it.remove(FAILED_ATTEMPTS)
            it.remove(LOCKED_UNTIL)
        }
    }

    private fun ByteArray.encodeBase64(): String = Base64.encodeToString(this, Base64.NO_WRAP)

    private fun String.decodeBase64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)

    private companion object {
        val SALT = stringPreferencesKey("pin_salt")
        val VERIFIER = stringPreferencesKey("pin_verifier")
        val FAILED_ATTEMPTS = intPreferencesKey("failed_attempts")
        val LOCKED_UNTIL = longPreferencesKey("locked_until")
        val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
        val REMINDER_DISMISSED_AT = longPreferencesKey("backup_reminder_dismissed_at")
    }
}
