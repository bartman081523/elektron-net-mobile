package org.elektronnet.mobile.ui.security

import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Biometry first.
 *
 * The prompt offers a fingerprint or a face, and nothing else, whenever the phone has an
 * enrolled biometric. A PIN, a pattern or a password can be entered by anybody who knows it,
 * including spyware that reads it off the screen, so the screen lock is only the fallback for
 * phones without a sensor. This mirrors the security model of the wallet: the app lock keeps
 * someone else holding the unlocked phone out, it is not a second password.
 */
object Biometric {
    private const val STRONG = BiometricManager.Authenticators.BIOMETRIC_STRONG
    private const val WEAK = BiometricManager.Authenticators.BIOMETRIC_WEAK
    private const val CREDENTIAL = BiometricManager.Authenticators.DEVICE_CREDENTIAL

    private fun canAuthenticate(context: Context, authenticators: Int): Boolean =
        BiometricManager.from(context).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

    /** True when a fingerprint or a face is enrolled and usable. */
    fun hasBiometric(context: Context): Boolean {
        if (canAuthenticate(context, STRONG)) return true
        // BIOMETRIC_WEAK can only be queried from Android 11; older versions answer through STRONG.
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && canAuthenticate(context, WEAK)
    }

    /** True when a PIN, a pattern or a password protects the lock screen. */
    fun hasDeviceCredential(context: Context): Boolean {
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return keyguard?.isDeviceSecure == true
    }

    fun available(context: Context): Boolean = hasBiometric(context) || hasDeviceCredential(context)

    /**
     * The authenticators the prompt may offer. Biometrics alone when one is enrolled, the
     * screen lock only on phones without a sensor.
     */
    private fun authenticators(context: Context): Int = when {
        canAuthenticate(context, STRONG) -> STRONG
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && canAuthenticate(context, WEAK) -> WEAK
        else -> CREDENTIAL
    }

    fun confirmDeviceCredentialIntent(context: Context, title: String? = null, description: String? = null): Intent? {
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager ?: return null
        return keyguard.createConfirmDeviceCredentialIntent(title, description)
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String,
        negative: String,
        onSuccess: () -> Unit,
        onFailure: () -> Unit = {},
        onFallbackToCredential: (() -> Unit)? = null,
    ) {
        val authenticators = authenticators(activity)
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // Only a phone without a usable biometric falls back to the screen lock.
                    // A temporary lockout is waited out instead of opening the PIN path.
                    if (onFallbackToCredential != null && (
                        errorCode == BiometricPrompt.ERROR_LOCKOUT_PERMANENT ||
                        errorCode == BiometricPrompt.ERROR_NO_BIOMETRICS ||
                        errorCode == BiometricPrompt.ERROR_HW_NOT_PRESENT
                    )) {
                        onFallbackToCredential()
                    } else {
                        onFailure()
                    }
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setAllowedAuthenticators(authenticators)
                } else if (authenticators == CREDENTIAL) {
                    @Suppress("DEPRECATION")
                    setDeviceCredentialAllowed(true)
                } else {
                    setNegativeButtonText(negative)
                }
            }
            .build()
        try {
            prompt.authenticate(info)
        } catch (e: Exception) {
            if (onFallbackToCredential != null) {
                onFallbackToCredential()
            } else {
                onFailure()
            }
        }
    }
}

fun Context.findFragmentActivity(): FragmentActivity? {
    var ctx: Context? = this
    while (ctx != null) {
        if (ctx is FragmentActivity) return ctx
        ctx = (ctx as? ContextWrapper)?.baseContext
    }
    return null
}
