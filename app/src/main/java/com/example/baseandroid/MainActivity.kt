package com.example.baseandroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.baseandroid.ui.BaseAndroidApp
import com.example.baseandroid.ui.theme.BaseAndroidTheme

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

