package com.vuegram.presentation.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale
import com.vuegram.R
import com.vuegram.data.history.WatchHistory
import com.vuegram.domain.model.MediaItem
import com.vuegram.presentation.ui.Images
import com.vuegram.presentation.ui.Motion

/** 16:9 video cards: Telegram's thumbnail, a duration pill, how far it was watched, and the title underneath. */
class VideoCardAdapter(
    private val span: Int,
    private val onFocus: (Int, MediaItem, View, Boolean) -> Unit,
    private val onClick: (MediaItem) -> Unit,
    private val onLongClick: (MediaItem) -> Unit = {}
) : RecyclerView.Adapter<VideoCardAdapter.Holder>() {

    val videos = mutableListOf<MediaItem>()
    private var frameWidth = 0

    fun append(more: List<MediaItem>) {
        val known = videos.mapTo(HashSet()) { it.messageId }
        val fresh = more.filter { it.messageId !in known }
        if (fresh.isEmpty()) return
        val start = videos.size
        videos.addAll(fresh)
        notifyItemRangeInserted(start, fresh.size)
    }

    fun clear() {
        val count = videos.size
        videos.clear()
        notifyItemRangeRemoved(0, count)
    }

    /** Redraws only the progress bars and badges, so focus and thumbnails stay as they are. */
    fun refreshProgress() = notifyItemRangeChanged(0, videos.size, PROGRESS)

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val frame: View = view.findViewById(R.id.frame)
        val thumb: ImageView = view.findViewById(R.id.thumb)
        val duration: TextView = view.findViewById(R.id.duration)
        val watched: View = view.findViewById(R.id.watched)
        val track: View = view.findViewById(R.id.progress_track)
        val bar: View = view.findViewById(R.id.progress)
        val name: TextView = view.findViewById(R.id.name)
    }

    override fun getItemCount() = videos.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_video, parent, false)
        val holder = Holder(view)
        val cell = (parent.width - parent.paddingStart - parent.paddingEnd) / span
        frameWidth = cell - view.paddingStart - view.paddingEnd
        holder.frame.layoutParams.height = frameWidth * 9 / 16
        holder.frame.clipToOutline = true
        return holder
    }

    override fun onBindViewHolder(holder: Holder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PROGRESS)) bindProgress(holder, videos[position]) else onBindViewHolder(holder, position)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = videos[position]
        val dim = ContextCompat.getColor(holder.itemView.context, R.color.tv_text_dim)
        val white = ContextCompat.getColor(holder.itemView.context, R.color.tv_text)
        holder.name.text = item.title
        holder.name.setTextColor(if (holder.itemView.hasFocus()) white else dim)
        holder.duration.text = formatDuration(item.durationSeconds)
        holder.duration.visibility = if (item.durationSeconds > 0) View.VISIBLE else View.GONE
        Images.load(holder.thumb, item.thumbnailFileId, item.miniThumbnailBytes)
        bindProgress(holder, item)
        holder.itemView.setOnClickListener { onClick(item) }
        holder.itemView.setOnLongClickListener {
            onLongClick(item)
            true
        }
        holder.itemView.setOnFocusChangeListener { v, hasFocus ->
            val scale = if (hasFocus) FOCUS_SCALE else 1f
            holder.frame.animate().scaleX(scale).scaleY(scale).setDuration(Motion.FAST).start()
            v.translationZ = if (hasFocus) 8f else 0f
            holder.name.setTextColor(if (hasFocus) white else dim)
            val current = holder.bindingAdapterPosition
            if (current != RecyclerView.NO_POSITION) onFocus(current, videos[current], holder.frame, hasFocus)
        }
    }

    private fun bindProgress(holder: Holder, item: MediaItem) {
        val entry = WatchHistory.get(item.chatId, item.messageId)
        val watched = entry?.watched == true
        holder.watched.visibility = if (watched) View.VISIBLE else View.GONE
        holder.thumb.alpha = if (watched) 0.55f else 1f
        val progress = entry?.takeIf { !it.watched }?.progress ?: 0f
        if (progress > 0f) {
            holder.track.visibility = View.VISIBLE
            holder.bar.layoutParams.width = (frameWidth * progress).toInt().coerceAtLeast(1)
            holder.bar.requestLayout()
        } else {
            holder.track.visibility = View.GONE
        }
    }

    companion object {
        const val FOCUS_SCALE = 1.06f
        private const val PROGRESS = "progress"

        fun formatDuration(seconds: Int): String {
            if (seconds <= 0) return ""
            val h = seconds / 3600
            val m = seconds % 3600 / 60
            val s = seconds % 60
            return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
        }
    }
}
