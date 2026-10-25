package com.vuegram.presentation.ui

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Test

/** The QR code the login screen draws must read back as exactly the tg://login link TDLib gave. */
class QrCodeTest {

    private fun decode(text: String, size: Int): String {
        val matrix = QrCode.matrix(text, size)
        val pixels = IntArray(matrix.width * matrix.height) { i ->
            if (matrix[i % matrix.width, i / matrix.width]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(matrix.width, matrix.height, pixels)))
        return QRCodeReader().decode(bitmap).text
    }

    @Test
    fun loginLinkRoundTrips() {
        // The shape of a real link: tg://login?token= and a base64url token.
        val link = "tg://login?token=AQKj7bJlZGQAAAAA2xB7xQh0nN3t4aK_9r8VtRZp0F7v3y1wq-cMbQ"
        assertEquals(link, decode(link, 760))
    }

    @Test
    fun smallRenderStillReads() {
        val link = "tg://login?token=" + "x".repeat(64)
        assertEquals(link, decode(link, 200))
    }
}
