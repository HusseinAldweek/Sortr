package com.sortr.app.vault

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the vault is open. It locks whenever the whole app goes to the background (observed
 * through ProcessLifecycleOwner), so returning to Sortr always asks for the PIN or fingerprint.
 */
@Singleton
class VaultLock @Inject constructor() : DefaultLifecycleObserver {

    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    val isUnlocked: Boolean get() = _unlocked.value

    fun unlock() {
        _unlocked.value = true
    }

    fun lock() {
        _unlocked.value = false
    }

    override fun onStop(owner: LifecycleOwner) = lock()
}
