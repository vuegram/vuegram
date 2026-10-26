package com.vuegram.presentation.library

import android.content.Context
import android.graphics.Rect
import android.os.Parcelable
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnNextLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vuegram.R
import com.vuegram.data.history.WatchEntry
import com.vuegram.data.history.WatchHistory
import com.vuegram.data.library.GroupChat
import com.vuegram.domain.model.MediaItem
import com.vuegram.presentation.chat.ChatFragment
import com.vuegram.presentation.chat.VideoCardAdapter
import com.vuegram.presentation.ui.Images
import com.vuegram.presentation.ui.Motion
import kotlin.math.abs

/** One entry of the library grid. [key] identifies it across rebuilds, so focus can stay on it. */
sealed class LibraryRow(val key: String) {
    /** A section heading; [id] keeps keys unique when two headings read the same (two pinned chats' names). */
    class Header(val text: String, id: String = text) : LibraryRow("header:$id")
    class Continue(val entry: WatchEntry, val media: MediaItem) : LibraryRow("continue:${entry.chatId}:${entry.messageId}")
    /** A new video from one of the first chats ("New in your channels"). */
    class Recent(val media: MediaItem, val chatTitle: String) : LibraryRow("recent:${media.chatId}:${media.messageId}")
    /** One of a pinned channel's latest videos, in that channel's own row. */
    class ChannelVideo(val media: MediaItem, val chat: GroupChat, val row: List<MediaItem>) :
        LibraryRow("channel:${media.chatId}:${media.messageId}")
    /** The last card of a pinned channel's row: opens the channel's page with all its videos. */
    class SeeAll(val chat: GroupChat) : LibraryRow("all:${chat.id}")
    class Chat(val chat: GroupChat, val hidden: Boolean, val pinned: Boolean = false) : LibraryRow("chat:${chat.id}")
    /** A row of video cards ([Continue] or [Recent]) that scrolls sideways, so a long list still takes one row. */
    class Shelf(id: String, val items: List<LibraryRow>) : LibraryRow("shelf:$id") {
        fun indexOf(key: String?) = items.indexOfFirst { it.key == key }
    }
    class HiddenToggle(val count: Int, val showing: Boolean) : LibraryRow("hidden-toggle")
}

/**
 * The library grid: section headings across the full width, shelves of video cards (Continue watching, New in your
 * channels) that scroll sideways, chat cards one column wide, and the Hidden card at the end. A shelf's cards come
 * from a second LibraryAdapter with a fixed [cardWidth].
 */
class LibraryAdapter(
    private val span: Int,
    private val onFocus: (Int, LibraryRow, View, Float, Boolean) -> Unit,
    private val onClick: (LibraryRow) -> Unit,
    private val onLongClick: (LibraryRow) -> Unit,
    private val onColor: (Long, Int) -> Unit,
    /** Up (false) or Down (true) pressed on the card at a position; true when it moved focus itself. */
    private val onVertical: (Int, View, Boolean) -> Boolean,
    /** Each shelf's own sideways list, as it is made (the focus ring follows its scrolling too). */
    private val onShelf: (RecyclerView) -> Unit = {},
    /** In a shelf: every card this wide, side by side. 0 in the grid, where cards take their columns. */
    private val cardWidth: Int = 0
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    /** Where each shelf was scrolled to, so a rebuild, or a shelf's view reused for the other, keeps it. */
    private val shelfStates = HashMap<String, Parcelable?>()

    /** All shelves share their cards: a shelf reached for the first time reuses cards instead of building new ones. */
    private val shelfPool = RecyclerView.RecycledViewPool().apply { setMaxRecycledViews(TYPE_CONTINUE, 20) }

    init {
        // Every row has a stable key: after a rebuild each shelf and card keeps its own view (no swapped shelves that
        // reload all their thumbnails), and RecyclerView only rebinds what changed.
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = rows[position].key.fold(1125899906842597L) { h, c -> 31 * h + c.code }

    var rows: List<LibraryRow> = emptyList()
        set(value) {
            field = value
            spanSizes.invalidateSpanIndexCache()
            spanSizes.invalidateSpanGroupIndexCache()
            notifyDataSetChanged()
        }

    val spanSizes = object : GridLayoutManager.SpanSizeLookup() {
        override fun getSpanSize(position: Int) = when (rows.getOrNull(position)) {
            is LibraryRow.Header, is LibraryRow.Shelf -> span
            is LibraryRow.Continue, is LibraryRow.Recent, is LibraryRow.ChannelVideo, is LibraryRow.SeeAll -> CONTINUE_SPAN
            else -> 1
        }
    }.apply {
        // Up and Down ask for lines often (see LibraryFragment.moveVertically); cleared whenever the rows change.
        isSpanIndexCacheEnabled = true
        isSpanGroupIndexCacheEnabled = true
    }

    override fun getItemCount() = rows.size

    override fun getItemViewType(position: Int) = when (rows[position]) {
        is LibraryRow.Header -> TYPE_HEADER
        is LibraryRow.Continue, is LibraryRow.Recent, is LibraryRow.ChannelVideo, is LibraryRow.SeeAll -> TYPE_CONTINUE
        is LibraryRow.Chat, is LibraryRow.HiddenToggle -> TYPE_CHAT
        is LibraryRow.Shelf -> TYPE_SHELF
    }

    private class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.header)
    }

    private class ContinueHolder(view: View) : RecyclerView.ViewHolder(view) {
        val frame: View = view.findViewById(R.id.frame)
        val thumb: ImageView = view.findViewById(R.id.thumb)
        val pill: TextView = view.findViewById(R.id.duration)
        val track: View = view.findViewById(R.id.progress_track)
        val bar: View = view.findViewById(R.id.progress)
        val name: TextView = view.findViewById(R.id.name)
        val center: TextView = view.findViewById(R.id.center_label)
        var frameWidth = 0
    }

    private class ShelfHolder(val list: RecyclerView, val cards: LibraryAdapter) : RecyclerView.ViewHolder(list) {
        var shelfKey: String? = null
    }

    private class ChatHolder(view: View) : RecyclerView.ViewHolder(view) {
        val frame: View = view.findViewById(R.id.frame)
        val photo: ImageView = view.findViewById(R.id.photo)
        val initial: TextView = view.findViewById(R.id.initial)
        val name: TextView = view.findViewById(R.id.name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val cell = (parent.width - parent.paddingStart - parent.paddingEnd) / span
        return when (viewType) {
            TYPE_HEADER -> HeaderHolder(inflater.inflate(R.layout.item_header, parent, false))
            TYPE_SHELF -> createShelf(inflater, parent, cell)
            TYPE_CONTINUE -> {
                val view = inflater.inflate(R.layout.item_video, parent, false)
                if (cardWidth > 0) view.layoutParams.width = cardWidth
                val width = if (cardWidth > 0) cardWidth else cell * CONTINUE_SPAN
                ContinueHolder(view).also {
                    it.frameWidth = width - view.paddingStart - view.paddingEnd
                    it.frame.layoutParams.height = it.frameWidth * 9 / 16
                    it.frame.clipToOutline = true
                }
            }
            else -> {
                val view = inflater.inflate(R.layout.item_chat, parent, false)
                ChatHolder(view).also {
                    it.frame.layoutParams.height = cell - view.paddingStart - view.paddingEnd
                    it.frame.clipToOutline = true
                }
            }
        }
    }

    /**
     * A shelf: a sideways list whose cards report focus, clicks and Up as if they were this grid's own card at the
     * shelf's position. Cards are a little narrower than two grid columns, so three fit and the fourth peeks in.
     */
    private fun createShelf(inflater: LayoutInflater, parent: ViewGroup, cell: Int): ShelfHolder {
        val list = inflater.inflate(R.layout.item_shelf, parent, false) as RecyclerView
        var holder: ShelfHolder? = null
        fun shelfPosition() = holder?.bindingAdapterPosition?.takeIf { it != RecyclerView.NO_POSITION }
        val cards = LibraryAdapter(
            span = span,
            onFocus = { _, row, frame, scale, hasFocus -> shelfPosition()?.let { onFocus(it, row, frame, scale, hasFocus) } },
            onClick = onClick,
            onLongClick = onLongClick,
            onColor = onColor,
            onVertical = { _, card, down -> shelfPosition()?.let { onVertical(it, card, down) } ?: false },
            cardWidth = (cell * CONTINUE_SPAN * SHELF_CARD).toInt()
        )
        val card = (cell * CONTINUE_SPAN * SHELF_CARD).toInt()
        list.layoutManager = ShelfLayoutManager(parent.context, card).apply { initialPrefetchItemCount = FULL_CARDS + 1 }
        list.setRecycledViewPool(shelfPool)
        // Room after the last card, the peek's width, so it too can move into a full place instead of the shelf
        // stopping short with its first card cut in half.
        list.setPaddingRelative(0, 0, (cell * span - card * FULL_CARDS).coerceAtLeast(0), 0)
        list.itemAnimator = null
        list.adapter = cards
        onShelf(list)
        return ShelfHolder(list, cards).also { holder = it }
    }

    private fun bindShelf(holder: ShelfHolder, row: LibraryRow.Shelf) {
        val layout = holder.list.layoutManager ?: return
        if (holder.shelfKey == row.key) {
            holder.cards.rows = row.items
            return
        }
        holder.shelfKey?.let { shelfStates[it] = layout.onSaveInstanceState() }
        holder.shelfKey = row.key
        holder.cards.rows = row.items
        val saved = shelfStates[row.key]
        if (saved != null) layout.onRestoreInstanceState(saved) else holder.list.scrollToPosition(0)
    }

    /** Focuses the card of the shelf shown by [holder] nearest [x] (on screen); false when it shows none. */
    fun focusNearestInShelf(holder: RecyclerView.ViewHolder, x: Int): Boolean {
        val list = (holder as? ShelfHolder)?.list ?: return false
        val location = IntArray(2)
        val card = (0 until list.childCount).map { list.getChildAt(it) }.minByOrNull {
            it.getLocationOnScreen(location)
            abs(location[0] + it.width / 2 - x)
        } ?: return false
        return card.requestFocus()
    }

    /** Focuses the card with [key] in the shelf shown by [holder]; false when it is not there. */
    fun focusInShelf(holder: RecyclerView.ViewHolder, key: String?): Boolean {
        val shelf = holder as? ShelfHolder ?: return false
        val index = shelf.cards.rows.indexOfFirst { it.key == key }
        if (index < 0) return false
        val list = shelf.list
        val card = list.findViewHolderForAdapterPosition(index)
        if (card != null && !list.isLayoutRequested) return card.itemView.requestFocus()
        list.scrollToPosition(index)
        list.doOnNextLayout { list.post { list.findViewHolderForAdapterPosition(index)?.itemView?.requestFocus() } }
        return true
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val row = rows[position]
        when (holder) {
            is HeaderHolder -> holder.text.text = (row as LibraryRow.Header).text
            is ShelfHolder -> {
                bindShelf(holder, row as LibraryRow.Shelf)
                return
            }
            is ContinueHolder -> when (row) {
                is LibraryRow.Continue -> bindContinue(holder, row)
                is LibraryRow.Recent -> bindRecent(holder, row)
                is LibraryRow.ChannelVideo -> bindChannelVideo(holder, row)
                is LibraryRow.SeeAll -> bindSeeAll(holder, row)
                else -> Unit
            }
            is ChatHolder -> when (row) {
                is LibraryRow.Chat -> bindChat(holder, row)
                is LibraryRow.HiddenToggle -> bindToggle(holder, row)
                else -> Unit
            }
        }
        if (row is LibraryRow.Header) return
        val frame = when (holder) {
            is ContinueHolder -> holder.frame
            is ChatHolder -> holder.frame
            else -> return
        }
        val label = when (holder) {
            is ContinueHolder -> holder.name
            is ChatHolder -> holder.name
            else -> return
        }
        val scale = if (holder is ContinueHolder) CONTINUE_SCALE else CHAT_SCALE
        val dim = ContextCompat.getColor(holder.itemView.context, R.color.tv_text_dim)
        val white = ContextCompat.getColor(holder.itemView.context, R.color.tv_text)
        label.setTextColor(if (holder.itemView.hasFocus()) white else dim)
        holder.itemView.setOnClickListener { onClick(row) }
        holder.itemView.setOnLongClickListener {
            onLongClick(row)
            true
        }
        holder.itemView.setOnKeyListener { v, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            val at = holder.bindingAdapterPosition
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> onVertical(at, v, false)
                KeyEvent.KEYCODE_DPAD_DOWN -> onVertical(at, v, true)
                // A shelf's first and last cards stop there, rather than letting focus jump to another row.
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> cardWidth > 0 && atShelfEnd(v, keyCode, at)
                else -> false
            }
        }
        holder.itemView.setOnFocusChangeListener { v, hasFocus ->
            val s = if (hasFocus) scale else 1f
            frame.animate().scaleX(s).scaleY(s).setDuration(Motion.FAST).start()
            v.translationZ = if (hasFocus) 8f else 0f
            label.setTextColor(if (hasFocus) white else dim)
            val current = holder.bindingAdapterPosition
            if (current != RecyclerView.NO_POSITION) onFocus(current, rows[current], frame, scale, hasFocus)
        }
    }

    /** A card leaving the screen lets go of its picture (Glide keeps it cached), so off-screen cards don't hold memory. */
    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        when (holder) {
            is ContinueHolder -> Images.release(holder.thumb)
            is ChatHolder -> Images.release(holder.photo)
        }
    }

    /** True when [keyCode] points past the shelf's first or last card (mirrored when the layout is right-to-left). */
    private fun atShelfEnd(card: View, keyCode: Int, at: Int): Boolean {
        val rtl = card.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val forward = (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) != rtl
        return if (forward) at == rows.lastIndex else at == 0
    }

    /**
     * A shelf scrolls a whole card at a time: the focused card goes to the first or last full place, so the cards on
     * screen are never cut, only the peeks at the edges (the scrolled-past card's and the next one's).
     */
    private class ShelfLayoutManager(context: Context, private val cardWidth: Int) :
        LinearLayoutManager(context, HORIZONTAL, false) {
        override fun requestChildRectangleOnScreen(
            parent: RecyclerView, child: View, rect: Rect, immediate: Boolean, focusedChildVisible: Boolean
        ): Boolean {
            val full = cardWidth * FULL_CARDS
            val start = if (layoutDirection == View.LAYOUT_DIRECTION_RTL) width - paddingRight - full else paddingLeft
            val left = getDecoratedLeft(child)
            val right = getDecoratedRight(child)
            val dx = when {
                left < start -> left - start
                right > start + full -> right - start - full
                else -> 0
            }
            if (dx == 0) return false
            if (immediate) parent.scrollBy(dx, 0) else parent.smoothScrollBy(dx, 0)
            return true
        }
    }

    private fun bindContinue(holder: ContinueHolder, row: LibraryRow.Continue) {
        val context = holder.itemView.context
        plainCard(holder)
        holder.name.text = row.entry.title.ifBlank { row.media.title }
        holder.pill.visibility = View.VISIBLE
        holder.pill.text = ChatFragment.minutes(row.entry.remainingMs).let { context.resources.getQuantityString(R.plurals.meta_left, it, it) }
        holder.track.visibility = View.VISIBLE
        holder.bar.layoutParams.width = (holder.frameWidth * row.entry.progress).toInt().coerceAtLeast(1)
        holder.bar.requestLayout()
        Images.load(holder.thumb, row.media.thumbnailFileId, row.media.miniThumbnailBytes)
    }

    /** Undoes what [bindSeeAll] changes, since cards are reused for any kind of row. */
    private fun plainCard(holder: ContinueHolder) {
        holder.center.visibility = View.GONE
        holder.thumb.alpha = 1f
    }

    private fun bindRecent(holder: ContinueHolder, row: LibraryRow.Recent) {
        plainCard(holder)
        // Without a caption the title is only "Video" or a file name: the channel says more.
        holder.name.text = if (row.media.caption.isBlank() && row.chatTitle.isNotBlank()) row.chatTitle else row.media.title
        val duration = VideoCardAdapter.formatDuration(row.media.durationSeconds)
        holder.pill.visibility = if (duration.isEmpty()) View.GONE else View.VISIBLE
        holder.pill.text = duration
        holder.track.visibility = View.GONE
        Images.load(holder.thumb, row.media.thumbnailFileId, row.media.miniThumbnailBytes)
    }

    private fun bindChannelVideo(holder: ContinueHolder, row: LibraryRow.ChannelVideo) {
        plainCard(holder)
        // Without a caption Telegram's title is only "Video": in its channel's own row, when it was posted says more.
        holder.name.text = if (row.media.caption.isBlank() && row.media.date > 0) {
            android.text.format.DateUtils.getRelativeTimeSpanString(
                row.media.date * 1000L, System.currentTimeMillis(), android.text.format.DateUtils.DAY_IN_MILLIS
            )
        } else {
            row.media.title
        }
        val duration = VideoCardAdapter.formatDuration(row.media.durationSeconds)
        holder.pill.visibility = if (duration.isEmpty()) View.GONE else View.VISIBLE
        holder.pill.text = duration
        // Started: how far; watched: faded, as in the channel's own grid.
        val entry = WatchHistory.get(row.media.chatId, row.media.messageId)
        val progress = entry?.takeIf { !it.watched }?.progress ?: 0f
        holder.track.visibility = if (progress > 0f) View.VISIBLE else View.GONE
        if (progress > 0f) {
            holder.bar.layoutParams.width = (holder.frameWidth * progress).toInt().coerceAtLeast(1)
            holder.bar.requestLayout()
        }
        if (entry?.watched == true) holder.thumb.alpha = 0.55f
        Images.load(holder.thumb, row.media.thumbnailFileId, row.media.miniThumbnailBytes)
    }

    private fun bindSeeAll(holder: ContinueHolder, row: LibraryRow.SeeAll) {
        holder.name.text = row.chat.title
        holder.pill.visibility = View.GONE
        holder.track.visibility = View.GONE
        holder.center.visibility = View.VISIBLE
        holder.center.setText(R.string.row_see_all)
        holder.thumb.alpha = 0.3f
        Images.load(holder.thumb, row.chat.photoFileId, row.chat.miniPhoto)
    }

    private fun bindChat(holder: ChatHolder, row: LibraryRow.Chat) {
        val chat = row.chat
        holder.name.text = chat.title
        holder.initial.text = chat.title.trim().take(1).uppercase()
        holder.frame.alpha = if (row.hidden) 0.35f else 1f
        Images.load(holder.photo, chat.photoFileId, chat.miniPhoto) { color -> onColor(chat.id, color) }
    }

    private fun bindToggle(holder: ChatHolder, row: LibraryRow.HiddenToggle) {
        val context = holder.itemView.context
        holder.frame.alpha = 1f
        Images.load(holder.photo, null, null)
        holder.initial.text = row.count.toString()
        holder.name.text = context.getString(if (row.showing) R.string.hidden_card_hide else R.string.hidden_card_show)
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_CONTINUE = 1
        private const val TYPE_CHAT = 2
        private const val TYPE_SHELF = 3
        const val CONTINUE_SPAN = 2
        /** A shelf card's width against two grid columns: three cards and a sliver of the fourth, a hint to scroll. */
        private const val SHELF_CARD = 0.9f
        /** Cards a shelf shows whole; the space left after them is the next card's peek. */
        private const val FULL_CARDS = 3
        const val CHAT_SCALE = 1.08f
        const val CONTINUE_SCALE = 1.05f
    }
}
