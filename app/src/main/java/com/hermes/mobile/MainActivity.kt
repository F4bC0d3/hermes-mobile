package com.hermes.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.hermes.mobile.ui.HermesAppRoot
import com.hermes.mobile.ui.theme.HermesTheme
import com.hermes.mobile.ui.theme.HermesTheme as ThemePalette

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.Transparent.value.toInt()),
            navigationBarStyle = SystemBarStyle.dark(Color.Transparent.value.toInt()),
        )
        super.onCreate(savedInstanceState)
        setContent {
            val app = HermesApp.instance
            val store = app.sessionStore
            var theme by remember {
                mutableStateOf(runCatching { ThemePalette.valueOf(store.theme) }.getOrDefault(ThemePalette.DARK))
            }

            HermesTheme(theme = theme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HermesAppRoot(
                        sessionStore = store,
                        currentTheme = theme,
                        onThemeChange = {
                            theme = it
                            store.theme = it.name
                        },
                    )
                }
            }
        }
    }
}
