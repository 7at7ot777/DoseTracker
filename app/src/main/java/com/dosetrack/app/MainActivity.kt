package com.dosetrack.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.dosetrack.app.ui.navigation.DoseTrackNavHost
import com.dosetrack.app.ui.theme.DoseTrackTheme

// AppCompatActivity (not ComponentActivity) so per-app language and night mode apply.
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DoseTrackTheme {
                DoseTrackNavHost()
            }
        }
    }
}
