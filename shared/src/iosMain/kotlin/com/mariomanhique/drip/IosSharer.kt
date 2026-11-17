package com.mariomanhique.drip

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import com.mariomanhique.drip.domain.ShareContent
import com.mariomanhique.drip.domain.ShareTarget
import com.mariomanhique.drip.ui.Sharer
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSString
import platform.Foundation.NSDate
import platform.Foundation.NSURL
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.UIKit.NSFontAttributeName
import platform.UIKit.NSForegroundColorAttributeName
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.UIKit.UIPasteboard
import platform.UIKit.UIPasteboardOptionExpirationDate
import platform.UIKit.UIRectFill
import platform.UIKit.UIViewController
import platform.UIKit.drawInRect

// Replace with the App ID registered at developers.facebook.com; Instagram Stories requires it.
private const val FACEBOOK_APP_ID = "REPLACE_WITH_FACEBOOK_APP_ID"

private const val CARD_W = 1080.0
private const val CARD_H = 1920.0

/**
 * - Instagram story: hands the card to Instagram as the story background.
 * - WhatsApp (chat or status), Facebook, Instagram feed, more: system sheet with the card image
 *   (WhatsApp's share extension offers chats and "Status"; the URL scheme can only send text).
 * Each falls back to the system sheet when the app is not installed.
 */
internal val IosSharer = Sharer { target, content ->
    when (target) {
        ShareTarget.INSTAGRAM_STORY -> openInstagramStory(content) || presentSheet(content, withImage = true)
        else -> presentSheet(content, withImage = true)
    }
}

private fun openInstagramStory(content: ShareContent): Boolean {
    val app = UIApplication.sharedApplication
    val url = NSURL.URLWithString("instagram-stories://share?source_application=$FACEBOOK_APP_ID") ?: return false
    if (!app.canOpenURL(url)) return false
    val png = renderCard(content)?.let { UIImagePNGRepresentation(it) } ?: return false
    UIPasteboard.generalPasteboard.setItems(
        listOf(mapOf<Any?, Any?>("com.instagram.sharedSticker.backgroundImage" to png)),
        mapOf<Any?, Any?>(UIPasteboardOptionExpirationDate to NSDate.dateWithTimeIntervalSinceNow(300.0)),
    )
    app.openURL(url, emptyMap<Any?, Any>(), null)
    return true
}

private fun presentSheet(content: ShareContent, withImage: Boolean): Boolean {
    val items = mutableListOf<Any>()
    val card = if (withImage) renderCard(content) else null
    // Image only when we have it: apps turn a link in the text into a link post and drop the image.
    if (card != null) items += card else items += content.text
    val top = topViewController() ?: return false
    top.presentViewController(UIActivityViewController(items, null), true, null)
    return true
}

private fun topViewController(): UIViewController? {
    var vc = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (vc?.presentedViewController != null) vc = vc.presentedViewController
    return vc
}

private fun color(argb: Long) = UIColor(
    red = ((argb shr 16) and 0xFF) / 255.0,
    green = ((argb shr 8) and 0xFF) / 255.0,
    blue = (argb and 0xFF) / 255.0,
    alpha = 1.0,
)

/** 1080x1920 card: palette bands, emoji, headline, caption and the DRIP mark. */
@OptIn(ExperimentalForeignApi::class)
private fun renderCard(content: ShareContent): UIImage? {
    val palette = content.palette.ifEmpty { listOf(0xFF1F2937) }
    UIGraphicsBeginImageContextWithOptions(CGSizeMake(CARD_W, CARD_H), true, 1.0)
    val bandH = CARD_H / palette.size
    palette.forEachIndexed { i, c ->
        color(c).setFill()
        UIRectFill(CGRectMake(0.0, i * bandH, CARD_W, bandH + 1))
    }
    fun text(s: String, size: Double, y: Double, h: Double, bold: Boolean = true) {
        val attrs = mapOf<Any?, Any?>(
            NSFontAttributeName to if (bold) UIFont.boldSystemFontOfSize(size) else UIFont.systemFontOfSize(size),
            NSForegroundColorAttributeName to UIColor.whiteColor,
        )
        (s as NSString).drawInRect(CGRectMake(80.0, y, CARD_W - 160.0, h), withAttributes = attrs)
    }
    val photo = content.photoUri?.let { NSURL.URLWithString(it)?.path }?.let { UIImage.imageWithContentsOfFile(it) }
    if (photo != null) {
        val pw = photo.size.useContents { width }
        val ph = photo.size.useContents { height }
        val scale = maxOf(CARD_W / pw, CARD_H / ph)
        val dw = pw * scale
        val dh = ph * scale
        photo.drawInRect(CGRectMake((CARD_W - dw) / 2, CARD_H - dh, dw, dh))
        UIColor.blackColor.colorWithAlphaComponent(0.55).setFill()
        UIRectFill(CGRectMake(0.0, 1100.0, CARD_W, CARD_H - 1100.0))
    } else {
        text(content.emoji, 260.0, 380.0, 340.0)
    }
    text(content.headline, 84.0, 760.0, 300.0)
    text(content.caption, 52.0, 1100.0, 360.0, bold = false)
    text("DRIP", 64.0, 1700.0, 100.0)
    val image = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()
    return image
}