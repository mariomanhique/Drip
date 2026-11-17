package com.mariomanhique.drip

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.mariomanhique.drip.di.appModules
import org.koin.android.ext.android.get
import org.koin.core.KoinApplication
import org.koin.dsl.koinConfiguration

class MainActivity : ComponentActivity() {
    private var onPicked: ((String?) -> Unit)? = null

    private val gallery = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val callback = onPicked
        onPicked = null
        callback?.invoke(uri?.let { copyToAppStorage(it) })
    }

    private fun copyToAppStorage(uri: android.net.Uri): String? = runCatching {
        val file = java.io.File(filesDir, "outfit_${System.currentTimeMillis()}.jpg")
        contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
        android.net.Uri.fromFile(file).toString()
    }.getOrNull()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        get<AndroidAuthService>().attach(this)

        setContent {
            App(
                sharer = remember { AndroidSharer(applicationContext) },
                photoPicker = remember {
                    com.mariomanhique.drip.ui.PhotoPicker { cb ->
                        onPicked = cb
                        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                },
            )
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    org.koin.compose.KoinApplication(configuration = koinConfiguration(declaration = {
        modules(
            org.koin.dsl.module { single<com.mariomanhique.drip.auth.AuthService> { com.mariomanhique.drip.auth.NoAuthService } },
            *appModules.toTypedArray(),
        )
    }), content = { App() })
}