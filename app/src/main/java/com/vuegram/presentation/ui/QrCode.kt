package com.vuegram.presentation.ui

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Draws a link (a tg://login link, or the key-drop page) as a QR code: black modules on white, with sharp edges at any size. */
object QrCode {
    /** The code's modules: medium error correction and a one-module quiet zone (the white card adds more). */
    fun matrix(text: String, sizePx: Int): BitMatrix {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1
        )
        return QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    }

    fun drawable(resources: Resources, text: String, sizePx: Int): BitmapDrawable {
        val matrix = matrix(text, sizePx)
        val pixels = IntArray(matrix.width * matrix.height)
        for (y in 0 until matrix.height) {
            val row = y * matrix.width
            for (x in 0 until matrix.width) pixels[row + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
        }
        val bitmap = Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.RGB_565)
        // No smoothing when the ImageView scales it: blurred module edges make phones slower to read the code.
        return BitmapDrawable(resources, bitmap).apply { setFilterBitmap(false) }
    }
}
