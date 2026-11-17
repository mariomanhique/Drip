package com.mariomanhique.drip

import com.mariomanhique.drip.ui.PhotoPicker
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDate
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile
import platform.Photos.PHPhotoLibrary
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

private class PickerDelegate(val onPicked: (String?) -> Unit) : NSObject(), PHPickerViewControllerDelegateProtocol {
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, null)
        val result = didFinishPicking.firstOrNull() as? PHPickerResult
        if (result == null) {
            onPicked(null)
            return
        }
        result.itemProvider.loadDataRepresentationForTypeIdentifier("public.image") { data, _ ->
            val path = data?.let { UIImage.imageWithData(it) }?.let { save(it) }
            dispatch_async(dispatch_get_main_queue()) { onPicked(path) }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun save(image: UIImage): String? {
        val data = UIImageJPEGRepresentation(image, 0.85) ?: return null
        val dir = (NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).firstOrNull() as? String) ?: return null
        val path = "$dir/outfit_${(NSDate().timeIntervalSince1970 * 1000).toLong()}.jpg"
        return if (data.writeToFile(path, true)) NSURL.fileURLWithPath(path).absoluteString else null
    }
}

// The delegate is only weakly held by the picker, so keep a reference alive.
private var activeDelegate: PickerDelegate? = null

object IosPhotoPicker : PhotoPicker {
    override fun pick(onPicked: (String?) -> Unit) {
        val config = PHPickerConfiguration(PHPhotoLibrary.sharedPhotoLibrary()).apply {
            filter = PHPickerFilter.imagesFilter
            selectionLimit = 1
        }
        val picker = PHPickerViewController(config)
        val delegate = PickerDelegate { activeDelegate = null; onPicked(it) }
        activeDelegate = delegate
        picker.delegate = delegate
        var vc: UIViewController? = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (vc?.presentedViewController != null) vc = vc.presentedViewController
        vc?.presentViewController(picker, true, null)
    }
}
