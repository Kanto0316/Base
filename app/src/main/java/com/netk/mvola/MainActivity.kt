package com.netk.mvola

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.netk.mvola.ui.BaseAndroidApp
import com.netk.mvola.ui.theme.BaseAndroidTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BaseAndroidTheme {
                BaseAndroidApp()
            }
        }
    }
}

