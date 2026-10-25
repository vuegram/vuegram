package com.vuegram.presentation.chat

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.BidiFormatter
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.vuegram.R
import com.vuegram.data.library.LibraryRepository
import com.vuegram.domain.model.MediaItem
import java.io.File

/**
 * A chat's photos, one at a time and full screen. Left/right step through the photos the chat screen has loaded;
 * Back returns to the chat (MainActivity pops it off the back stack). The photo is downloaded at full size and Glide
 * scales it to the screen, so a large photo never has to fit in memory whole.
 */
class PhotoViewerFragment : Fragment(R.layout.fragment_photo) {

    private lateinit var photos: List<MediaItem>
    private var index = 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        @Suppress("DEPRECATION", "UNCHECKED_CAST")
        photos = (requireArguments().getSerializable(ARG_PHOTOS) as? ArrayList<MediaItem>).orEmpty()
        index = savedInstanceState?.getInt(ARG_INDEX) ?: requireArguments().getInt(ARG_INDEX)
        if (photos.isEmpty()) {
            parentFragmentManager.popBackStack()
            return
        }
        view.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> step(if (rtl) 1 else -1)
                KeyEvent.KEYCODE_DPAD_RIGHT -> step(if (rtl) -1 else 1)
                else -> false
            }
        }
        view.requestFocus()
        show()
    }

    private val rtl get() = view?.layoutDirection == View.LAYOUT_DIRECTION_RTL

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(ARG_INDEX, index)
    }

    private fun step(delta: Int): Boolean {
        val next = index + delta
        if (next !in photos.indices) return true
        index = next
        show()
        return true
    }

    private fun show() {
        val root = view ?: return
        val image = root.findViewById<ImageView>(R.id.photo)
        val loading = root.findViewById<View>(R.id.photo_loading)
        val item = photos[index]
        root.findViewById<TextView>(R.id.photo_caption).text = item.caption
        val chat = requireArguments().getString(ARG_CHAT).orEmpty()
        root.findViewById<TextView>(R.id.photo_meta).text = getString(
            R.string.photo_meta, BidiFormatter.getInstance().unicodeWrap(chat), index + 1, photos.size
        )
        image.tag = item.messageId
        Glide.with(this).clear(image)
        loading.visibility = View.VISIBLE
        val fileId = item.fileId ?: return
        LibraryRepository.downloadPath(fileId) { path ->
            root.post {
                if (!isAdded || image.tag != item.messageId) return@post   // the viewer moved on meanwhile
                if (path == null) {
                    loading.visibility = View.GONE
                    root.findViewById<TextView>(R.id.photo_caption).setText(R.string.photo_failed)
                    return@post
                }
                Glide.with(this).load(File(path)).fitCenter().listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, first: Boolean): Boolean {
                        loading.visibility = View.GONE
                        return false
                    }

                    override fun onResourceReady(
                        resource: Drawable, model: Any, target: Target<Drawable>?, source: DataSource, first: Boolean
                    ): Boolean {
                        loading.visibility = View.GONE
                        return false
                    }
                }).into(image)
            }
        }
    }

    companion object {
        private const val ARG_PHOTOS = "photos"
        private const val ARG_INDEX = "index"
        private const val ARG_CHAT = "chat"

        fun newInstance(photos: List<MediaItem>, index: Int, chatTitle: String) = PhotoViewerFragment().apply {
            arguments = bundleOf(ARG_PHOTOS to ArrayList(photos), ARG_INDEX to index, ARG_CHAT to chatTitle)
        }
    }
}
