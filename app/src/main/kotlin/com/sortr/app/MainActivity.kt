package com.sortr.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.sortr.app.launch.LaunchRequests
import com.sortr.app.ui.SortrApp
import com.sortr.app.ui.theme.SortrTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** A FragmentActivity because the fingerprint prompt (BiometricPrompt) needs one. */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var launchRequests: LaunchRequests

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // After a configuration change the request was already handled.
        if (savedInstanceState == null) launchRequests.handle(intent)
        setContent {
            SortrTheme {
                SortrApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        launchRequests.handle(intent)
    }
}
