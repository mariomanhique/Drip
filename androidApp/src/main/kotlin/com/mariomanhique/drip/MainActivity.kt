package com.mariomanhique.drip

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.mariomanhique.drip.di.appModules
import org.koin.core.KoinApplication
import org.koin.dsl.koinConfiguration

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App(sharer = remember { AndroidSharer(applicationContext) })
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    org.koin.compose.KoinApplication(configuration = koinConfiguration(declaration = {
        modules(
            appModules
        )
    }), content = { App() })
}