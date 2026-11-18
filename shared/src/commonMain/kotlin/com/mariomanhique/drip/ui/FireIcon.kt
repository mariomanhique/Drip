package com.mariomanhique.drip.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

private fun flame(filled: Boolean): ImageVector = ImageVector.Builder(
    name = if (filled) "FireFilled" else "FireOutlined",
    defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
).path(
    fill = if (filled) SolidColor(Color.Black) else null,
    stroke = if (filled) null else SolidColor(Color.Black),
    strokeLineWidth = 1.8f,
    strokeLineCap = StrokeCap.Round,
    strokeLineJoin = StrokeJoin.Round,
) {
    moveTo(12f, 2.5f)
    curveTo(12.5f, 5f, 13f, 7.5f, 13.2f, 10f)
    curveTo(14.2f, 9.6f, 15.1f, 8.8f, 15.6f, 7.4f)
    curveTo(17.4f, 9.4f, 18.8f, 11.8f, 18.8f, 14.4f)
    arcTo(6.8f, 6.8f, 0f, false, true, 5.2f, 14.4f)
    curveTo(5.2f, 8.8f, 9.6f, 5.2f, 12f, 2.5f)
    close()
}.build()

val FireFilled: ImageVector = flame(true)
val FireOutlined: ImageVector = flame(false)

private fun glyph(name: String, filled: Boolean, body: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).path(
        fill = if (filled) SolidColor(Color.Black) else null,
        stroke = if (filled) null else SolidColor(Color.Black),
        strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = body,
    ).build()

private fun bubble(filled: Boolean) = glyph("Comment", filled) {
    moveTo(12f, 3.5f)
    arcTo(8.5f, 8.5f, 0f, true, true, 7.4f, 19.2f)
    lineTo(3.5f, 20.5f)
    lineTo(4.8f, 16.6f)
    arcTo(8.5f, 8.5f, 0f, false, true, 12f, 3.5f)
    close()
}

private fun bookmark(filled: Boolean) = glyph("Bookmark", filled) {
    moveTo(6f, 3.5f); lineTo(18f, 3.5f); lineTo(18f, 20.5f)
    lineTo(12f, 15.5f); lineTo(6f, 20.5f); close()
}

val CommentOutlined: ImageVector = bubble(false)
val BookmarkOutlined: ImageVector = bookmark(false)
val BookmarkFilled: ImageVector = bookmark(true)
