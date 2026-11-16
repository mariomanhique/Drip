package com.mariomanhique.drip.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mariomanhique.drip.domain.ShareContent
import com.mariomanhique.drip.domain.ShareTarget

/** Platform hook. Returns false when the target app is unavailable. */
fun interface Sharer {
    fun share(target: ShareTarget, content: ShareContent): Boolean
}

val LocalSharer = compositionLocalOf { Sharer { _, _ -> false } }

@Composable
fun ShareRow(content: ShareContent, onResult: (String) -> Unit = {}) {
    val sharer = LocalSharer.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Share this result", style = MaterialTheme.typography.labelMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ShareTarget.entries.forEach { t ->
                AssistChip(
                    onClick = { if (!sharer.share(t, content)) onResult("${t.label} isn't available on this device") },
                    label = { Text(t.label) },
                )
            }
        }
    }
}
