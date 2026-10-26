package com.vuegram.presentation.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.ImageView
import com.vuegram.data.library.LibraryRepository
import java.util.concurrent.Executors

/**
 * The picture behind the top of a screen, like a streaming app's billboard: the focused video's thumbnail (or chat
 * photo), heavily softened and faded out towards the reading side and the bottom (the picture itself turns
 * transparent, so the glow behind it still shows), keeping the title over it easy to read. Telegram's thumbnails are
 * small; softening them on purpose looks calm where stretching them would look blurry. It takes the top right
 * [WIDTH] x [HEIGHT] of its parent and crossfades from one picture to the next.
 *
 * The fade is baked into the small picture (a few thousand pixels) once, when it arrives: an offscreen layer with
 * masks would be drawn again on every frame the screen moves, which made the home screen drop frames on a TV.
 */
class Backdrop @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {

    private val front = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_XY; alpha = 0f }
    private val back = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_XY; alpha = 0f }
    private val main = Handler(Looper.getMainLooper())
    /** What the latest [show] asked for; a picture that finishes after a newer request is dropped. */
    @Volatile private var wanted: Int? = null
    private var shown: Int? = null
    private var pending: Runnable? = null

    init {
        addView(back, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(front, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = (MeasureSpec.getSize(widthMeasureSpec) * WIDTH).toInt()
        val h = (MeasureSpec.getSize(heightMeasureSpec) * HEIGHT).toInt()
        super.onMeasure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
    }

    /**
     * Shows the picture for [fileId] (a thumbnail or chat photo), or [mini] (Telegram's tiny preview) when there is no
     * file. Focus can race across a row, so the work starts only once it has rested on one item for a moment.
     */
    fun show(fileId: Int?, mini: ByteArray?) {
        val key = fileId ?: mini?.contentHashCode()
        if (key == null) return clear()
        if (key == wanted) return
        wanted = key
        pending?.let(main::removeCallbacks)
        pending = Runnable { load(key, fileId, mini) }.also { main.postDelayed(it, SETTLE_MS) }
    }

    /** Fades the picture out (a card with nothing to show, like "Show hidden"). */
    fun clear() {
        wanted = null
        shown = null
        pending?.let(main::removeCallbacks)
        front.animate().alpha(0f).setDuration(Motion.SLOW).start()
        back.animate().alpha(0f).setDuration(Motion.SLOW).start()
    }

    override fun onDetachedFromWindow() {
        pending?.let(main::removeCallbacks)
        wanted = null
        super.onDetachedFromWindow()
    }

    private fun load(key: Int, fileId: Int?, mini: ByteArray?) {
        if (fileId == null) {
            work(key) { mini?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } }
            return
        }
        LibraryRepository.downloadPath(fileId) { path ->
            work(key) { path?.let { decodeSmall(it) } ?: mini?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } }
        }
    }

    /**
     * Decodes and softens off the main thread, skipping a picture focus has already left. Nothing usable (a broken
     * file): the request is forgotten, so focusing the card again tries again. A failure here never crashes VueGram.
     */
    private fun work(key: Int, decode: () -> Bitmap?) = worker.execute {
        if (key != wanted) return@execute
        val soft = try {
            soften(decode())
        } catch (e: Throwable) {
            null
        }
        if (soft != null) post(key, soft) else main.post { if (wanted == key) wanted = null }
    }

    private fun post(key: Int, soft: Bitmap) = main.post {
        if (key != wanted || key == shown || !isAttachedToWindow || width == 0 || height == 0) return@post
        shown = key
        val bitmap = bake(soft, rtl = layoutDirection == LAYOUT_DIRECTION_RTL)
        // The new picture comes in on top; the old one fades under it.
        back.setImageDrawable(front.drawable)
        back.alpha = front.alpha
        front.setImageDrawable(BitmapDrawable(resources, bitmap).apply { isFilterBitmap = true })
        front.alpha = 0f
        front.animate().alpha(STRENGTH).setDuration(Motion.SLOW).setInterpolator(Motion.ease()).start()
        back.animate().alpha(0f).setDuration(Motion.SLOW).start()
    }

    /**
     * The softened picture cropped to this view's shape (as centre-crop would) at [SOFT_WIDTH] pixels wide, with the
     * fade in its own transparency: clear at the reading side, fully there from 60% across; clear at the bottom, fully
     * there from 65% up. Drawn stretched with smoothing, it looks the same as masking the full-size view would.
     */
    private fun bake(soft: Bitmap, rtl: Boolean): Bitmap {
        val w = SOFT_WIDTH
        val h = (SOFT_WIDTH * height / width).coerceAtLeast(8)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val scale = maxOf(w.toFloat() / soft.width, h.toFloat() / soft.height)
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate((w - soft.width * scale) / 2f, (h - soft.height * scale) / 2f)
        }
        Canvas(out).drawBitmap(soft, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        val pixels = IntArray(w * h)
        out.getPixels(pixels, 0, w, 0, 0, w, h)
        for (y in 0 until h) {
            val up = ramp((h - 1 - y) / (h * 0.65f))
            for (x in 0 until w) {
                val across = ramp((if (rtl) w - 1 - x else x) / (w * 0.6f))
                val i = y * w + x
                val alpha = ((pixels[i] ushr 24) * across * up).toInt()
                pixels[i] = (alpha shl 24) or (pixels[i] and 0xFFFFFF)
            }
        }
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    /** 0 to 1 over [t] in 0..1, eased at both ends so the fade has no visible edge. */
    private fun ramp(t: Float): Float {
        val c = t.coerceIn(0f, 1f)
        return c * c * (3 - 2 * c)
    }

    private fun decodeSmall(path: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= SOFT_WIDTH * 2) sample *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private companion object {
        const val WIDTH = 0.68f
        const val HEIGHT = 0.62f
        /** How strongly the picture shows through; the glow and the black around it do the rest. */
        const val STRENGTH = 0.45f
        const val SETTLE_MS = 160L
        /** The picture is softened at this width (in pixels) and then drawn large with smoothing. */
        const val SOFT_WIDTH = 64
        val worker = Executors.newSingleThreadExecutor()

        /** A small copy, box-blurred three times: smooth enough to draw at full screen size without blocks. */
        fun soften(source: Bitmap?): Bitmap? {
            source ?: return null
            if (source.width <= 0 || source.height <= 0) return null
            val w = SOFT_WIDTH
            val h = (SOFT_WIDTH * source.height / source.width).coerceIn(8, SOFT_WIDTH)
            val small = Bitmap.createScaledBitmap(source, w, h, true)
            val pixels = IntArray(w * h)
            small.getPixels(pixels, 0, w, 0, 0, w, h)
            repeat(3) { boxBlur(pixels, w, h, radius = 2) }
            return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        }

        private fun boxBlur(px: IntArray, w: Int, h: Int, radius: Int) {
            val tmp = IntArray(px.size)
            pass(px, tmp, w, h, radius, horizontal = true)
            pass(tmp, px, w, h, radius, horizontal = false)
        }

        private fun pass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
            val lines = if (horizontal) h else w
            val length = if (horizontal) w else h
            for (line in 0 until lines) {
                for (i in 0 until length) {
                    var a = 0; var red = 0; var g = 0; var b = 0; var n = 0
                    for (k in -r..r) {
                        val j = (i + k).coerceIn(0, length - 1)
                        val c = if (horizontal) src[line * w + j] else src[j * w + line]
                        a += c ushr 24; red += c shr 16 and 0xFF; g += c shr 8 and 0xFF; b += c and 0xFF; n++
                    }
                    val out = (a / n shl 24) or (red / n shl 16) or (g / n shl 8) or (b / n)
                    if (horizontal) dst[line * w + i] = out else dst[i * w + line] = out
                }
            }
        }
    }
}
