package com.mariomanhique.drip

import androidx.compose.ui.window.ComposeUIViewController
import com.mariomanhique.drip.auth.AuthService
import com.mariomanhique.drip.di.initKoin

private var started = false

/** [auth] is the Swift Firebase implementation. */
fun MainViewController(auth: AuthService) = ComposeUIViewController(
    configure = {
        if (!started) {
            started = true
            initKoin(auth = auth)
        }
    },
) { App(sharer = IosSharer, photoPicker = IosPhotoPicker) }
