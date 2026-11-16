package com.mariomanhique.drip

import androidx.compose.ui.window.ComposeUIViewController
/**
 * WhatsApp (chat and status) opens directly when installed. Facebook, Instagram and
 * "more" use the system share sheet, which lists those apps when they are installed.
 */

fun MainViewController() = ComposeUIViewController { App(sharer = IosSharer) }
