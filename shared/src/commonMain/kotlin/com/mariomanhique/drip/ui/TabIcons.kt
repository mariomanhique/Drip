package com.mariomanhique.drip.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Monochrome tab icons: outlined when idle, filled (or bolder) when selected. Tinted by the theme. */
object TabIcons {
    fun of(tab: Tab, selected: Boolean): ImageVector = cache.getOrPut(tab to selected) {
        when (tab) {
            Tab.FEED -> home(selected)
            Tab.BATTLES -> swords(selected)
            Tab.CHALLENGES -> trophy(selected)
            Tab.RANKS -> podium(selected)
            Tab.PROFILE -> person(selected)
        }
    }

    private val cache = mutableMapOf<Pair<Tab, Boolean>, ImageVector>()

    private fun ImageVector.Builder.shape(
        filled: Boolean,
        fillable: Boolean = true,
        width: Float = 1.8f,
        body: PathBuilder.() -> Unit,
    ) = path(
        fill = if (filled && fillable) SolidColor(Color.Black) else null,
        stroke = if (filled && fillable) null else SolidColor(Color.Black),
        strokeLineWidth = width,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = body,
    )

    private fun build(name: String, body: ImageVector.Builder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(body).build()

    private fun home(f: Boolean) = build("Home") {
        shape(f) {
            moveTo(3.5f, 11f); lineTo(12f, 3.5f); lineTo(20.5f, 11f)
            lineTo(20.5f, 20.5f); lineTo(14.5f, 20.5f); lineTo(14.5f, 14.5f)
            lineTo(9.5f, 14.5f); lineTo(9.5f, 20.5f); lineTo(3.5f, 20.5f); close()
        }
    }

    private fun swords(f: Boolean) = build("Swords") {
        val w = if (f) 2.6f else 1.8f
        shape(f, false, w) { moveTo(5f, 5f); lineTo(19f, 19f) }
        shape(f, false, w) { moveTo(19f, 5f); lineTo(5f, 19f) }
        shape(f, false, w) { moveTo(14f, 18f); lineTo(18f, 14f) }
        shape(f, false, w) { moveTo(6f, 14f); lineTo(10f, 18f) }
    }

    private fun trophy(f: Boolean) = build("Trophy") {
        shape(f) {
            moveTo(7f, 3.5f); lineTo(17f, 3.5f); lineTo(17f, 9f)
            arcTo(5f, 5f, 0f, false, true, 7f, 9f); close()
        }
        shape(f, false) { moveTo(7f, 5.5f); lineTo(4f, 5.5f); lineTo(4f, 8f); arcTo(3f, 3f, 0f, false, false, 7f, 11f) }
        shape(f, false) { moveTo(17f, 5.5f); lineTo(20f, 5.5f); lineTo(20f, 8f); arcTo(3f, 3f, 0f, false, true, 17f, 11f) }
        shape(f, false) { moveTo(12f, 14f); lineTo(12f, 18f) }
        shape(f, false) { moveTo(8f, 20.5f); lineTo(16f, 20.5f) }
    }

    private fun podium(f: Boolean) = build("Podium") {
        shape(f) { moveTo(3.5f, 11f); lineTo(8.5f, 11f); lineTo(8.5f, 20.5f); lineTo(3.5f, 20.5f); close() }
        shape(f) { moveTo(9.5f, 3.5f); lineTo(14.5f, 3.5f); lineTo(14.5f, 20.5f); lineTo(9.5f, 20.5f); close() }
        shape(f) { moveTo(15.5f, 14f); lineTo(20.5f, 14f); lineTo(20.5f, 20.5f); lineTo(15.5f, 20.5f); close() }
    }

    private fun person(f: Boolean) = build("Person") {
        shape(f) {
            moveTo(8f, 8f)
            arcTo(4f, 4f, 0f, true, true, 16f, 8f)
            arcTo(4f, 4f, 0f, true, true, 8f, 8f); close()
        }
        shape(f) {
            moveTo(4.5f, 20.5f)
            curveTo(4.5f, 16.5f, 8f, 14.5f, 12f, 14.5f)
            curveTo(16f, 14.5f, 19.5f, 16.5f, 19.5f, 20.5f); close()
        }
    }
}
