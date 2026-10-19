package com.sortr.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.sortr.app.ui.SortrApp
import com.sortr.app.ui.theme.SortrTheme
import dagger.hilt.android.AndroidEntryPoint

/** A FragmentActivity because the fingerprint prompt (BiometricPrompt) needs one. */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SortrTheme {
                SortrApp()
            }
        }
    }
}
