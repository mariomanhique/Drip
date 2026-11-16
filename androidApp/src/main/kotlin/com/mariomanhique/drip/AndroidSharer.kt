package com.mariomanhique.drip

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.mariomanhique.drip.domain.ShareContent
import com.mariomanhique.drip.domain.ShareTarget
import com.mariomanhique.drip.ui.Sharer
import java.io.File

/**
 * Renders a 1080x1920 card (palette, hero emoji, caption, DRIP mark) and hands it to the chosen app.
 * WhatsApp Status has no direct intent: the user picks "My status" inside WhatsApp's share sheet.
 */
class AndroidSharer(private val context: Context) : Sharer {

    private companion object {
        // Replace with the App ID registered at developers.facebook.com; Instagram Stories requires it.
        const val FACEBOOK_APP_ID = "REPLACE_WITH_FACEBOOK_APP_ID"
    }

    override fun share(target: ShareTarget, content: ShareContent): Boolean {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", renderCard(content))
        return when (target) {
            ShareTarget.WHATSAPP -> send("com.whatsapp", content, uri, withText = true)
            ShareTarget.WHATSAPP_STATUS -> send("com.whatsapp", content, uri, withText = false)
            ShareTarget.FACEBOOK -> send("com.facebook.katana", content, uri, withText = false)
            ShareTarget.INSTAGRAM_FEED -> send("com.instagram.android", content, uri, withText = false)
            ShareTarget.INSTAGRAM_STORY -> story(content, uri)
            ShareTarget.MORE -> chooser(content, uri)
        }
    }

    // Status, Facebook and Instagram turn a link in EXTRA_TEXT into a text/link post and drop the image,
    // so those targets get the image only.
    private fun send(pkg: String, content: ShareContent, uri: android.net.Uri, withText: Boolean): Boolean {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            setPackage(pkg)
            putExtra(Intent.EXTRA_STREAM, uri)
            if (withText) putExtra(Intent.EXTRA_TEXT, content.text)
            clipData = ClipData.newRawUri("DRIP", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return launch(intent)
    }

    private fun story(content: ShareContent, uri: android.net.Uri): Boolean {
        val intent = Intent("com.instagram.share.ADD_TO_STORY").apply {
            setPackage("com.instagram.android")
            setDataAndType(uri, "image/png")
            putExtra("source_application", FACEBOOK_APP_ID)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.grantUriPermission("com.instagram.android", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return launch(intent)
    }

    private fun chooser(content: ShareContent, uri: android.net.Uri): Boolean {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, content.text)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return launch(Intent.createChooser(send, "Share your DRIP").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun launch(intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

    private fun renderCard(c: ShareContent): File {
        val w = 1080
        val h = 1920
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val colors = c.palette.map { it.toInt() }.ifEmpty { listOf(Color.DKGRAY, Color.BLACK) }.let {
            if (it.size == 1) it + it else it
        }.toIntArray()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), colors, null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 420f
        canvas.drawText(c.emoji, w / 2f, 860f, paint)
        paint.color = Color.WHITE
        paint.setShadowLayer(8f, 0f, 2f, Color.argb(160, 0, 0, 0))
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = 64f
        wrap(c.headline, paint, w - 160).forEachIndexed { i, l -> canvas.drawText(l, w / 2f, 1240f + i * 80f, paint) }
        paint.typeface = Typeface.DEFAULT
        paint.textSize = 44f
        wrap("“${c.caption}”", paint, w - 200).take(3).forEachIndexed { i, l -> canvas.drawText(l, w / 2f, 1480f + i * 60f, paint) }
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
        paint.textSize = 96f
        canvas.drawText("DRIP", w / 2f, 1780f, paint)
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "drip_share.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }

    private fun wrap(text: String, paint: Paint, maxWidth: Int): List<String> {
        val lines = mutableListOf<String>()
        var line = ""
        for (word in text.split(" ")) {
            val next = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(next) > maxWidth && line.isNotEmpty()) {
                lines += line
                line = word
            } else line = next
        }
        if (line.isNotEmpty()) lines += line
        return lines
    }
}
