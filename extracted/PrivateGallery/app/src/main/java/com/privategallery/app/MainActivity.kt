package com.privategallery.app

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.privategallery.app.security.SecurePreferences
import com.privategallery.app.ui.navigation.PrivateGalleryNavGraph
import com.privategallery.app.ui.theme.PrivateGalleryTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var securePreferences: SecurePreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        applySecureModeFlag()

        setContent {
            PrivateGalleryTheme {
                PrivateGalleryNavGraph()
            }
        }
    }

    /** FLAG_SECURE per SECURITY_REQUIREMENTS: "Add optional FLAG_SECURE mode to prevent
     *  screenshots if privacy mode is enabled." Re-checked onResume in case the setting changed
     *  in Security Settings while backgrounded. */
    override fun onResume() {
        super.onResume()
        applySecureModeFlag()
    }

    private fun applySecureModeFlag() {
        if (securePreferences.isScreenshotProtectionEnabled()) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
