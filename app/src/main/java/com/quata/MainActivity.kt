package com.quata

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.navigation.AppNavGraph

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appContainer = (application as QuataApp).container

        setContent {
            QuataTheme {
                AppNavGraph(container = appContainer)
            }
        }
    }
}
