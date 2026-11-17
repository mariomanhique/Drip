package com.mariomanhique.drip.ui

import androidx.compose.runtime.compositionLocalOf

/** Opens the system gallery. [onPicked] gets a local file URI of the stored copy, or null if cancelled. */
fun interface PhotoPicker {
    fun pick(onPicked: (String?) -> Unit)
}

val LocalPhotoPicker = compositionLocalOf { PhotoPicker { it(null) } }
