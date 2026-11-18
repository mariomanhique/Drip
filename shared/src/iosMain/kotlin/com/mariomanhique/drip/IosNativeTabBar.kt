package com.mariomanhique.drip

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.mariomanhique.drip.ui.Tab
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIColor
import platform.UIKit.UIImage
import platform.UIKit.UITabBarAppearance
import platform.UIKit.UITabBar
import platform.UIKit.UITabBarDelegateProtocol
import platform.UIKit.UITabBarItem
import platform.darwin.NSObject

private class TabBarDelegate : NSObject(), UITabBarDelegateProtocol {
    var onSelect: (Tab) -> Unit = {}

    override fun tabBar(tabBar: UITabBar, didSelectItem: UITabBarItem) {
        Tab.entries.getOrNull(didSelectItem.tag.toInt())?.let(onSelect)
    }
}

private fun Tab.symbol() = when (this) {
    Tab.FEED -> "house"
    Tab.BATTLES -> "figure.fencing"
    Tab.CHALLENGES -> "trophy"
    Tab.RANKS -> "chart.bar"
    Tab.PROFILE -> "person"
}

/** Native UITabBar, which the system renders with Liquid Glass on iOS 26+. */
@OptIn(ExperimentalForeignApi::class, ExperimentalComposeUiApi::class)
@Composable
fun IosNativeTabBar(selected: Tab?, onSelect: (Tab) -> Unit) {
    val delegate = remember { TabBarDelegate() }
    delegate.onSelect = onSelect
    val bottomInset = with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }
    UIKitView(
        factory = {
            UITabBar().apply {
                this.delegate = delegate
                val clear = UITabBarAppearance().apply { configureWithTransparentBackground() }
                standardAppearance = clear
                scrollEdgeAppearance = clear
                backgroundColor = UIColor.clearColor
                opaque = false
                items = Tab.entries.map {
                    UITabBarItem(
                        title = null,
                        image = UIImage.systemImageNamed(it.symbol()),
                        tag = it.ordinal.toLong(),
                    )
                }
            }
        },
        modifier = Modifier.fillMaxWidth().height(49.dp + bottomInset),
        properties = UIKitInteropProperties(placedAsOverlay = true),
        update = { bar ->
            bar.selectedItem = selected?.let { bar.items?.getOrNull(it.ordinal) as? UITabBarItem }
        },
    )
}
