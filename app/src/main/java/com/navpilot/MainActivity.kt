package com.navpilot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.navpilot.core.ui.theme.NavPilotTheme
import org.osmdroid.config.Configuration

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        runCatching {
            Configuration.getInstance().load(
                applicationContext,
                applicationContext.getSharedPreferences("osmdroid", MODE_PRIVATE)
            )
            Configuration.getInstance().userAgentValue = packageName
        }

        setContent {
            NavPilotTheme {
                NavPilotApp()
            }
        }
    }
}
