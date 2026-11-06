package com.hkiptv.lab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.hkiptv.lab.ui.HkIptvApp
import com.hkiptv.lab.ui.theme.HkIptvTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HkIptvTheme { HkIptvApp(application as HkIptvApplication) } }
    }
}
