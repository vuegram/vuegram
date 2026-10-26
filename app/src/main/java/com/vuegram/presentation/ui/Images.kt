package com.vuegram.presentation.ui

import android.app.Activity
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import androidx.core.view.doOnAttach
import com.bumptech.glide.Glide
import com.bumptech.glide.request.transition.DrawableCrossFadeFactory
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.vuegram.R
import com.vuegram.data.library.LibraryRepository
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** Telegram thumbnails and chat photos into ImageViews, plus the colour each one glows in. */
object Images {
    private val main = Handler(Looper.getMainLooper())
    private val colors = ConcurrentHashMap<Int, Int>()
    private val colorWorker = Executors.newSingleThreadExecutor()
    private const val PREVIEWS = 200
    /** The sharp image fades in over the blurred preview; cross-fading keeps the preview under it until it has. */
    private val FADE = DrawableCrossFadeFactory.Builder(Motion.NORMAL.toInt()).setCrossFadeEnabled(true).build()

    /**
     * Shows [miniBytes] (Telegram's tiny blurred preview) at once, then the real image for [fileId] when it has
     * downloaded. [onColor] gets the image's average colour, for the glow.
     */
    fun load(view: ImageView, fileId: Int?, miniBytes: ByteArray?, onColor: ((Int) -> Unit)? = null) {
        // The same image, already shown (the grid rebuilt): keep it rather than flash the preview and load again.
        if (fileId != null && view.tag == fileId && view.getTag(R.id.image_shown) == fileId) return
        view.tag = fileId
        view.setTag(R.id.image_shown, null)
        Glide.with(view).clear(view)
        view.setImageBitmap(miniBytes?.let { previewOf(fileId, it) })
        if (fileId == null) return
        LibraryRepository.downloadPath(fileId) { path ->
            if (path == null) return@downloadPath
            main.post { show(view, fileId, path) }
            if (onColor != null) colorOf(fileId, path) { color -> main.post { onColor(color) } }
        }
    }

    /** Telegram's tiny preview, decoded once per picture rather than on every bind (rows of cards bind often). */
    private val previews = LruCache<Int, Bitmap>(PREVIEWS)

    private fun previewOf(fileId: Int?, bytes: ByteArray): Bitmap? {
        val key = fileId ?: bytes.contentHashCode()
        previews.get(key)?.let { return it }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.also { previews.put(key, it) }
    }

    /** A card scrolled away: drop its picture (Glide's cache keeps it for a quick return). */
    fun release(view: ImageView) {
        if (!canLoad(view)) return
        Glide.with(view).clear(view)
        view.setImageDrawable(null)
        view.tag = null
        view.setTag(R.id.image_shown, null)
    }

    private fun show(view: ImageView, fileId: Int, path: String) {
        if (view.tag != fileId) return   // the card was reused for another item meanwhile
        // A card the grid prepared just before it scrolls into view is not attached yet, and an image already on disk
        // arrives before it is: show it once the card is on screen. (Giving up here left those cards blurred.)
        if (!view.isAttachedToWindow) {
            view.doOnAttach { show(view, fileId, path) }
            return
        }
        if (!canLoad(view)) return   // the screen closed while the file downloaded
        Glide.with(view).load(File(path)).placeholder(view.drawable).centerCrop()
            .transition(DrawableTransitionOptions.withCrossFade(FADE))
            .into(view)
        view.setTag(R.id.image_shown, fileId)
    }

    /** Glide refuses (and throws) when the view's activity is finishing or gone. */
    private fun canLoad(view: ImageView): Boolean {
        var context = view.context
        while (context is ContextWrapper) {
            if (context is Activity) return !context.isFinishing && !context.isDestroyed
            context = context.baseContext
        }
        return true
    }

    /** The average colour of an image, nudged brighter so it reads as light on black. Cached per file. */
    fun colorOf(fileId: Int, path: String, onColor: (Int) -> Unit) {
        colors[fileId]?.let {
            onColor(it)
            return
        }
        colorWorker.execute {
            val options = BitmapFactory.Options().apply { inSampleSize = 16 }
            val bitmap = BitmapFactory.decodeFile(path, options) ?: return@execute
            val pixel = Bitmap.createScaledBitmap(bitmap, 1, 1, true).getPixel(0, 0)
            val hsv = FloatArray(3)
            Color.colorToHSV(pixel, hsv)
            hsv[1] = (hsv[1] * 1.3f).coerceAtMost(1f)
            hsv[2] = hsv[2].coerceIn(0.55f, 0.9f)
            val color = Color.HSVToColor(hsv)
            colors[fileId] = color
            onColor(color)
        }
    }
}
