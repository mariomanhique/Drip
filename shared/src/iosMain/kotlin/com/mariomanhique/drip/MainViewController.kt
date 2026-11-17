package com.mariomanhique.drip

import androidx.compose.ui.window.ComposeUIViewController
import com.mariomanhique.drip.di.initKoin

private val koin by lazy { initKoin() }

fun MainViewController() = ComposeUIViewController(configure = { koin }) { App(sharer = IosSharer, photoPicker = IosPhotoPicker) }
