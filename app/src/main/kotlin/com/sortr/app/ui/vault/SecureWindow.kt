package com.sortr.app.ui.vault

import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import com.sortr.app.vault.findFragmentActivity

/**
 * Sets FLAG_SECURE while on screen: no screenshots or screen recording, and a blank preview in
 * the recent apps list. Counted, so overlapping secure screens don't clear it early.
 */
@Composable
fun SecureWindowEffect() {
    val window = LocalContext.current.findFragmentActivity()?.window
    DisposableEffect(window) {
        if (window != null) SecureFlag.acquire(window)
        onDispose { if (window != null) SecureFlag.release(window) }
    }
}

private object SecureFlag {
    private var holders = 0

    fun acquire(window: Window) {
        if (holders++ == 0) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    fun release(window: Window) {
        if (--holders == 0) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
