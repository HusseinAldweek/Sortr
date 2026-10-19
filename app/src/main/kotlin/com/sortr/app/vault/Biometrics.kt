package com.sortr.app.vault

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Fingerprint (or other strong biometric) unlock. Only strong biometrics are accepted, since
 * weaker face unlock on some phones can be fooled by a photo. Without one, the PIN is used.
 */
object Biometrics {

    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Shows the system prompt. [onUsePin] runs when the user taps the PIN button or cancels;
     * [onError] gets the system's message for anything else (e.g. too many attempts).
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String,
        usePinLabel: String,
        onSuccess: () -> Unit,
        onUsePin: () -> Unit,
        onError: (CharSequence) -> Unit,
    ) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    when (errorCode) {
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_CANCELED,
                        -> onUsePin()
                        else -> onError(errString)
                    }
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setNegativeButtonText(usePinLabel)
                .setAllowedAuthenticators(BIOMETRIC_STRONG)
                .build(),
        )
    }
}

fun Context.findFragmentActivity(): FragmentActivity? {
    var context: Context? = this
    while (context is ContextWrapper) {
        if (context is FragmentActivity) return context
        context = context.baseContext
    }
    return null
}
