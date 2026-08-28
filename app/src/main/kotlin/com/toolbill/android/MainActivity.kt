package com.toolbill.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.toolbill.android.core.design.ThemeMode
import com.toolbill.android.core.design.ToolbillTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            // Theme mode and dynamic colour move into DataStore-backed settings in week 15.
            ToolbillTheme(themeMode = ThemeMode.SYSTEM, dynamicColor = false) {
                ToolbillApp()
            }
        }
    }
}
