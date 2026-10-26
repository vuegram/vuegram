package com.vuegram.presentation.chat

import android.text.BidiFormatter
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.vuegram.R
import com.vuegram.data.history.WatchHistory
import com.vuegram.data.library.FeedItem
import com.vuegram.data.library.FeedKind
import com.vuegram.presentation.ui.Images

/**
 * A chat's messages as a vertical list of cards, newest first (Settings › Show messages). Photos and videos show
 * their thumbnail; OK opens them, or the full text of a long message.
 */
class MessageFeedAdapter(
    private val onFocus: (Int, FeedItem, View, Boolean) -> Unit,
    private val onClick: (FeedItem) -> Unit,
    private val onLongClick: (FeedItem) -> Unit = {}
) : RecyclerView.Adapter<MessageFeedAdapter.Holder>() {

    val items = mutableListOf<FeedItem>()

    fun append(more: List<FeedItem>) {
        val known = items.mapTo(HashSet()) { it.messageId }
        val fresh = more.filter { it.messageId !in known }
        if (fresh.isEmpty()) return
        val start = items.size
        items.addAll(fresh)
        notifyItemRangeInserted(start, fresh.size)
    }

    fun clear() {
        val count = items.size
        items.clear()
        notifyItemRangeRemoved(0, count)
    }

    fun refreshProgress() = notifyItemRangeChanged(0, items.size, PROGRESS)

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val card: View = view.findViewById(R.id.card)
        val media: View = view.findViewById(R.id.media)
        val thumb: ImageView = view.findViewById(R.id.thumb)
        val play: View = view.findViewById(R.id.play)
        val duration: TextView = view.findViewById(R.id.duration)
        val meta: TextView = view.findViewById(R.id.meta)
        val text: TextView = view.findViewById(R.id.text)
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false)
        return Holder(view).also { it.media.clipToOutline = true }
    }

    override fun onBindViewHolder(holder: Holder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PROGRESS)) bindMeta(holder, items[position]) else onBindViewHolder(holder, position)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        val media = item.media
        holder.media.visibility = if (media != null) View.VISIBLE else View.GONE
        if (media != null) {
            Images.load(holder.thumb, media.thumbnailFileId, media.miniThumbnailBytes)
            holder.play.visibility = if (item.kind == FeedKind.Video) View.VISIBLE else View.GONE
            val duration = VideoCardAdapter.formatDuration(media.durationSeconds)
            holder.duration.text = duration
            holder.duration.visibility = if (duration.isNotEmpty()) View.VISIBLE else View.GONE
        }
        holder.text.text = item.text.ifBlank {
            holder.itemView.context.getString(if (item.kind == FeedKind.Photo) R.string.feed_photo else R.string.feed_video)
        }
        bindMeta(holder, item)
        holder.itemView.setOnClickListener { onClick(item) }
        holder.itemView.setOnLongClickListener {
            onLongClick(item)
            true
        }
        holder.itemView.setOnFocusChangeListener { _, hasFocus ->
            val scale = if (hasFocus) FOCUS_SCALE else 1f
            holder.card.animate().scaleX(scale).scaleY(scale).setDuration(180).start()
            holder.itemView.translationZ = if (hasFocus) 8f else 0f
            val current = holder.bindingAdapterPosition
            if (current != RecyclerView.NO_POSITION) onFocus(current, items[current], holder.card, hasFocus)
        }
    }

    /** "Sender · 12 Mar, 21:40 · ✓ Watched": the sender only in groups, the badge only for watched videos. */
    private fun bindMeta(holder: Holder, item: FeedItem) {
        val context = holder.itemView.context
        val parts = mutableListOf<String>()
        if (item.sender.isNotBlank()) parts += BidiFormatter.getInstance().unicodeWrap(item.sender)
        if (item.date > 0) {
            parts += DateUtils.formatDateTime(
                context, item.date * 1000L,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_SHOW_TIME
            )
        }
        if (item.kind == FeedKind.Video && WatchHistory.get(item.chatId, item.messageId)?.watched == true) {
            parts += context.getString(R.string.watched_badge)
        }
        holder.meta.text = parts.joinToString("  ·  ")
    }

    companion object {
        const val FOCUS_SCALE = 1.02f
        private const val PROGRESS = "progress"
    }
}
