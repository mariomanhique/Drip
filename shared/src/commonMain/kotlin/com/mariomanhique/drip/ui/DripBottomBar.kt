package com.mariomanhique.drip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** True on iOS, where the bar follows the Instagram style: icon-only, hairline divider, filled when selected. */
val LocalIosStyle = staticCompositionLocalOf { false }

/** Platform-native tab bar supplied by the host (UITabBar on iOS). */
typealias NativeTabBar = @Composable (selected: Tab?, onSelect: (Tab) -> Unit) -> Unit

val LocalNativeTabBar = staticCompositionLocalOf<NativeTabBar?> { null }

/** Space screens must leave at the bottom when the tab bar floats over the content. */
val LocalBottomBarInset = androidx.compose.runtime.compositionLocalOf { 0.dp }

@Composable
fun bottomBarListPadding() = androidx.compose.foundation.layout.PaddingValues(16.dp, 16.dp, 16.dp, 16.dp + LocalBottomBarInset.current)

@Composable
fun DripBottomBar(selected: Tab?, onSelect: (Tab) -> Unit) {
    val native = LocalNativeTabBar.current
    if (native != null) native(selected, onSelect)
    else if (LocalIosStyle.current) IosBottomBar(selected, onSelect) else AndroidBottomBar(selected, onSelect)
}

@Composable
private fun AndroidBottomBar(selected: Tab?, onSelect: (Tab) -> Unit) {
    NavigationBar {
        Tab.entries.forEach { tab ->
            val isSelected = tab == selected
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(tab) },
                icon = { Icon(TabIcons.of(tab, isSelected), contentDescription = tab.label) },
            )
        }
    }
}

@Composable
private fun IosBottomBar(selected: Tab?, onSelect: (Tab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)).height(49.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEach { tab ->
                val isSelected = tab == selected
                Box(
                    Modifier.weight(1f).fillMaxWidth().height(49.dp).clickable(
                        interactionSource = null, indication = null,
                    ) { onSelect(tab) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        TabIcons.of(tab, isSelected),
                        contentDescription = tab.label,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
    }
}
