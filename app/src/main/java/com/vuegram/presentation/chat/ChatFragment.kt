package com.vuegram.presentation.chat

import android.app.AlertDialog
import android.os.Bundle
import android.os.SystemClock
import android.text.BidiFormatter
import android.text.format.DateUtils
import android.text.format.Formatter
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.view.doOnAttach
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vuegram.R
import com.vuegram.app.BackHandler
import com.vuegram.app.MainActivity
import com.vuegram.app.VoiceSearchHost
import com.vuegram.data.history.WatchHistory
import com.vuegram.data.library.ChatInfo
import com.vuegram.data.library.ChatKind
import com.vuegram.data.library.ChatOrder
import com.vuegram.data.library.FeedCursor
import com.vuegram.data.library.FeedItem
import com.vuegram.data.library.FeedKind
import com.vuegram.data.library.GlobalCursor
import com.vuegram.data.library.GroupChat
import com.vuegram.data.library.LibraryRepository
import com.vuegram.data.library.VideoCursor
import com.vuegram.data.settings.AppSettings
import com.vuegram.domain.model.MediaItem
import com.vuegram.presentation.player.Player
import com.vuegram.presentation.ui.Backdrop
import com.vuegram.presentation.ui.ExpandingSearch
import com.vuegram.presentation.ui.FocusHighlight
import com.vuegram.presentation.ui.GlowView
import com.vuegram.presentation.ui.Images
import com.vuegram.presentation.ui.SkeletonView
import com.vuegram.presentation.ui.StatusText
import com.vuegram.presentation.ui.VoiceSearch
import com.vuegram.presentation.ui.alignWithTitle
import com.vuegram.presentation.ui.keepSidewaysInBar

/**
 * One chat. By default its videos, newest first, as a 16:9 grid; with Settings › Show messages on, all its messages
 * as a list of cards (text, photos, videos, files). More load as focus nears the end. The search field in the header
 * narrows either view with Telegram's search.
 *
 * Opened with [newGlobal], the same screen shows video search results from every chat in the library instead.
 */
class ChatFragment : Fragment(R.layout.fragment_chat), BackHandler, VoiceSearchHost {

    private var videos: VideoCardAdapter? = null
    private var feed: MessageFeedAdapter? = null
    private lateinit var status: TextView
    private lateinit var retry: Button
    private lateinit var search: EditText
    private var searchBox: ExpandingSearch? = null
    private var cursor = VideoCursor()
    private var globalCursor = GlobalCursor()
    private var feedCursor = FeedCursor()
    private var query = ""
    /** Bumped on every new search, so results of an older one that arrive late are ignored. */
    private var generation = 0
    private var loading = false
    private var emptyPages = 0
    private var focusedPosition = -1
    /** The channel's description and follower count, once Telegram has said (null until then, and for people). */
    private var info: ChatInfo? = null
    private var backdrop: Backdrop? = null
    private val onHistory: () -> Unit = { view?.post { refreshProgress() } }
    /** Spoken words fill the search field and search this chat (or every chat, on the global results screen). */
    private val voice = VoiceSearch(this) { words ->
        search.setText(words)
        searchBox?.update()
        runSearch(words)
    }

    private val chatId get() = requireArguments().getLong(ARG_ID)
    private val chatTitle get() = requireArguments().getString(ARG_TITLE).orEmpty()
    private val kind get() = ChatKind.entries.getOrNull(requireArguments().getInt(ARG_KIND)) ?: ChatKind.Group
    private val global get() = requireArguments().getBoolean(ARG_GLOBAL)
    /** All messages instead of only videos; fixed when the chat opens, so a page never mixes the two. */
    private var feedMode = false
    private val isDone get() = when {
        global -> globalCursor.isDone
        feedMode -> feedCursor.done
        else -> cursor.isDone
    }
    private val itemCount get() = feed?.itemCount ?: videos?.itemCount ?: 0

    /** The chat a video comes from: this chat, or (in global search) the result's own chat. */
    private fun titleOf(item: MediaItem) = if (global) LibraryRepository.chatTitle(item.chatId) else chatTitle

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val args = requireArguments()
        feedMode = !global && args.getBoolean(ARG_FEED)
        val grid = view.findViewById<RecyclerView>(R.id.grid)
        val container = view.findViewById<FrameLayout>(R.id.grid_container)
        val itemTitle = view.findViewById<TextView>(R.id.video_title)
        val itemMeta = view.findViewById<TextView>(R.id.video_meta)
        val glow = view.findViewById<GlowView>(R.id.glow)
        backdrop = view.findViewById(R.id.backdrop)
        status = view.findViewById(R.id.status)
        retry = view.findViewById(R.id.retry)
        search = view.findViewById(R.id.search)
        StatusText.followConnection(view.findViewById(R.id.connection))
        voice.attach(view.findViewById(R.id.voice))
        val density = resources.displayMetrics.density
        view.findViewById<View>(R.id.back).setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        keepSidewaysInBar(view.findViewById(R.id.header), R.id.back, R.id.order, R.id.voice, R.id.search)

        val avatar = view.findViewById<ImageView>(R.id.avatar)
        if (global) {
            view.findViewById<TextView>(R.id.chat_title).setText(R.string.search_all_title)
            view.findViewById<TextView>(R.id.chat_kind).setText(R.string.search_all_kind)
            avatar.visibility = View.GONE
            query = args.getString(ARG_QUERY).orEmpty()
            search.setText(query)
            search.setHint(R.string.search_all_hint)
        } else {
            view.findViewById<TextView>(R.id.chat_title).text = chatTitle
            view.findViewById<TextView>(R.id.chat_kind).text = kindLabel(kind)
            avatar.clipToOutline = true
            val photo = args.getInt(ARG_PHOTO).takeIf { it != 0 }
            Images.load(avatar, photo, null) { color -> glow.setGlowColor(color) }
            loadInfo(view.findViewById(R.id.chat_kind), itemTitle, itemMeta)
        }
        // Focus up in the header (Back, the order, search): the area below describes the channel instead of a video.
        val header = view.findViewById<View>(R.id.header)
        // Added to the window's own observer once attached, and taken off it while still attached: a listener added
        // before attaching, or removed after detaching, stays on the activity's window and keeps this screen alive.
        val listener = android.view.ViewTreeObserver.OnGlobalFocusChangeListener { _, newFocus ->
            if (isAdded && newFocus != null && isInside(header, newFocus)) describeChat(itemTitle, itemMeta)
        }
        view.doOnAttach { it.viewTreeObserver.addOnGlobalFocusChangeListener(listener) }
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) = v.viewTreeObserver.removeOnGlobalFocusChangeListener(listener)
        })

        grid.itemAnimator = null
        val highlight = FocusHighlight(container, grid, 18f * density)
        val color = args.getInt(ARG_COLOR)
        if (color != 0) {
            glow.setGlowColor(color)
            highlight.setAccent(color)
        }

        if (feedMode) {
            grid.layoutManager = LinearLayoutManager(requireContext())
            feed = MessageFeedAdapter(
                onFocus = { position, item, card, hasFocus ->
                    highlight.onFocus(card, hasFocus, MessageFeedAdapter.FOCUS_SCALE)
                    if (hasFocus) {
                        focusedPosition = position
                        itemTitle.text = feedTitle(item)
                        itemMeta.text = feedMeta(item)
                        alignWithTitle(itemTitle, itemMeta)
                        if (position >= itemCount - FEED_PRELOAD) loadMore()
                    }
                },
                onClick = ::openFeedItem,
                onLongClick = { item -> item.media?.takeIf { item.kind == FeedKind.Video }?.let(::showVideoMenu) }
            ).also { grid.adapter = it }
        } else {
            grid.layoutManager = GridLayoutManager(requireContext(), SPAN)
            videos = VideoCardAdapter(
                span = SPAN,
                onFocus = { position, item, frame, hasFocus ->
                    highlight.onFocus(frame, hasFocus, VideoCardAdapter.FOCUS_SCALE)
                    if (hasFocus) {
                        focusedPosition = position
                        backdrop?.show(item.thumbnailFileId, item.miniThumbnailBytes)
                        itemTitle.text = item.title
                        itemMeta.text = meta(item)
                        alignWithTitle(itemTitle, itemMeta)
                        if (position >= itemCount - SPAN * 2) loadMore()
                    }
                },
                onClick = { item -> play(item) },
                onLongClick = ::showVideoMenu
            ).also { grid.adapter = it }
        }
        if (feedMode && !global) search.setHint(R.string.search_messages_hint)
        setUpOrder(view.findViewById(R.id.order))
        searchBox = ExpandingSearch(search, (340 * density).toInt())

        search.setOnEditorActionListener { _, actionId, event ->
            val enter = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE || enter) {
                runSearch(search.text.toString().trim())
                true
            } else {
                false
            }
        }
        view.findViewById<SkeletonView>(R.id.skeleton).apply {
            columns = if (feedMode) 1 else SPAN
            aspect = if (feedMode) 0.12f else 9f / 16f
        }
        retry.setOnClickListener {
            retry.visibility = View.GONE
            loadMore(force = true)
        }
        WatchHistory.addListener(onHistory)
        loadMore()
    }

    override fun onDestroyView() {
        WatchHistory.removeListener(onHistory)
        searchBox = null
        backdrop = null
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        // Back from the player: progress may have changed, and focus goes back to the item that was playing.
        refreshProgress()
        if (focusedPosition >= 0) focusCard(focusedPosition)
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        // Back from the photo viewer: focus returns to the photo's card.
        if (!hidden && focusedPosition >= 0) focusCard(focusedPosition)
    }

    private fun refreshProgress() {
        videos?.refreshProgress()
        feed?.refreshProgress()
    }

    override fun startVoiceSearch() = voice.listen(requireContext())

    /** Back clears an active search first; only then does it leave the chat. */
    override fun onBack(): Boolean {
        if (global || query.isEmpty()) return false
        search.setText("")
        searchBox?.update()
        runSearch("")
        return true
    }

    /** Searches this chat (or every chat) for [text]; [force] loads again even when [text] is the current search. */
    private fun runSearch(text: String, force: Boolean = false) {
        hideKeyboard()
        if (global && text.isEmpty()) return
        if (!force && text == query && itemCount > 0) {
            focusCard(0)
            return
        }
        query = text
        generation++
        cursor = VideoCursor(oldestFirst = oldestFirst)
        globalCursor = GlobalCursor()
        feedCursor = FeedCursor()
        loading = false
        emptyPages = 0
        focusedPosition = -1
        videos?.clear()
        feed?.clear()
        retry.visibility = View.GONE
        loadMore()
    }

    /** After an error while paging, no automatic loading until then (Retry still works): Telegram may be limiting. */
    private var pausedUntil = 0L

    private fun loadMore(force: Boolean = false) {
        if (loading || isDone) return
        if (!force && SystemClock.elapsedRealtime() < pausedUntil) return
        loading = true
        val requested = generation
        val skeleton = view?.findViewById<SkeletonView>(R.id.skeleton)
        if (itemCount == 0) {
            // A search says what it is doing; the first page shows grey cards in the grid's shape.
            if (query.isNotEmpty()) status.setText(R.string.chat_searching) else {
                status.text = null
                skeleton?.show()
            }
        }
        // Runs on the main thread with the page that arrived; [append] adds it to whichever adapter is in use.
        fun onPage(error: String?, advance: () -> Unit, append: () -> Unit) {
            view?.post {
                if (!isAdded || requested != generation) return@post
                loading = false
                skeleton?.hide()
                advance()
                val before = itemCount
                append()
                val added = itemCount - before
                if (added > 0) emptyPages = 0
                when {
                    // Nothing new on this page (only service messages, or documents that aren't videos): look a few
                    // pages further by itself, or focus at the end of the list would never ask for more.
                    added == 0 && error == null && !isDone && ++emptyPages < MAX_EMPTY_PAGES -> loadMore()
                    itemCount > 0 -> {
                        status.text = null
                        if (before == 0) focusCard(0)
                        // A later page failed: the list keeps what it has and asks again on scrolling, after a pause.
                        if (error != null) pausedUntil = SystemClock.elapsedRealtime() + PAGING_PAUSE_MS
                    }
                    // Telegram refused or failed: say why, and offer to try again (it never loops by itself).
                    error != null -> {
                        status.text = StatusText.error(requireContext(), error)
                        retry.visibility = View.VISIBLE
                        retry.requestFocus()
                    }
                    query.isNotEmpty() -> {
                        status.text = getString(R.string.search_no_results, query)
                        search.requestFocus()
                    }
                    else -> status.setText(if (feedMode) R.string.feed_empty else R.string.chat_empty)
                }
            }
        }
        when {
            global -> LibraryRepository.searchAll(query, globalCursor) { items, next, error ->
                onPage(error, { globalCursor = next }) { videos?.append(items) }
            }
            feedMode -> LibraryRepository.loadFeed(chatId, feedCursor, query, withSenders = kind == ChatKind.Group) { items, next, error ->
                onPage(error, { feedCursor = next }) { feed?.append(items) }
            }
            else -> LibraryRepository.loadVideos(chatId, cursor, query) { items, next, error ->
                onPage(error, { cursor = next }) { videos?.append(items) }
            }
        }
    }

    /** The chat's videos oldest first (remembered per chat in [ChatOrder], and kept for searches in it); not in Show messages or "Search all channels". */
    private var oldestFirst = false

    /** The Newest first / Oldest first button: OK flips the order and loads the videos again from the other end. */
    private fun setUpOrder(button: Button) {
        if (global || feedMode) return
        oldestFirst = ChatOrder.isOldestFirst(chatId)
        cursor = cursor.copy(oldestFirst = oldestFirst)
        button.visibility = View.VISIBLE
        button.setText(if (oldestFirst) R.string.order_oldest else R.string.order_newest)
        button.setOnClickListener {
            // One order at a time: a second press while the first page loads would start a second chain of requests.
            if (loading) return@setOnClickListener
            oldestFirst = !oldestFirst
            ChatOrder.setOldestFirst(chatId, oldestFirst)
            button.setText(if (oldestFirst) R.string.order_oldest else R.string.order_newest)
            // Same search, other order: start over from the other end.
            runSearch(query, force = true)
            button.requestFocus()
        }
    }

    /** "Channel · 12,345 subscribers", or just "Channel" while the count is unknown. */
    private fun followersLine(): String {
        val count = info?.memberCount?.takeIf { it > 0 } ?: return kindLabel(kind)
        val followers = resources.getQuantityString(
            if (kind == ChatKind.Channel) R.plurals.chat_subscribers else R.plurals.chat_members, count,
            java.text.NumberFormat.getIntegerInstance().format(count)
        )
        return getString(R.string.chat_kind_count, kindLabel(kind), followers)
    }

    /** Who follows the chat, in the header's kind line ("CHANNEL · 12,345 SUBSCRIBERS"), once Telegram says. */
    private fun loadInfo(kindView: TextView, itemTitle: TextView, itemMeta: TextView) {
        LibraryRepository.chatInfo(chatId) { found ->
            view?.post {
                if (!isAdded || found == null) return@post
                info = found
                if (found.memberCount > 0) kindView.text = followersLine()
                if (itemCount == 0) describeChat(itemTitle, itemMeta)
            }
        }
    }

    /** The area under the header, while the header has focus (or before any video): the channel's own description. */
    private fun describeChat(itemTitle: TextView, itemMeta: TextView) {
        if (global) return
        backdrop?.clear()
        itemTitle.text = chatTitle
        // Its description; a chat without one shows who follows it instead ("Group · 338 members").
        val description = info?.description?.lineSequence()?.filter { it.isNotBlank() }?.joinToString("  ")?.trim().orEmpty()
        itemMeta.text = description.ifEmpty { followersLine() }
        alignWithTitle(itemTitle, itemMeta)
    }

    private fun isInside(ancestor: View, view: View): Boolean {
        var current: Any? = view
        while (current is View) {
            if (current === ancestor) return true
            current = current.parent
        }
        return false
    }

    private fun play(item: MediaItem) {
        (activity as? MainActivity)?.let { Player.play(it, it.playerLauncher, item, titleOf(item), upNext = upNext(item)) }
    }

    /** The videos loaded in this chat that were posted after [item], for the player's "Up next". */
    private fun upNext(item: MediaItem): List<MediaItem> {
        if (global) return emptyList()
        val loaded = videos?.videos ?: feed?.items.orEmpty().filter { it.kind == FeedKind.Video }.mapNotNull { it.media }
        return Player.upNext(item, loaded)
    }

    /** OK on a message: play a video, open a photo full screen, or read the whole text. */
    private fun openFeedItem(item: FeedItem) {
        val media = item.media
        when {
            item.kind == FeedKind.Video && media != null -> play(media)
            item.kind == FeedKind.Photo && media != null -> openPhoto(item)
            item.text.isNotBlank() -> {
                AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(feedMeta(item))
                    .setMessage(item.text)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }
    }

    /**
     * The photo viewer steps through the photos loaded so far around [item] (at most [PHOTO_WINDOW] of them). They are
     * passed without their tiny previews, so the viewer's saved state stays far below Android's limit.
     */
    private fun openPhoto(item: FeedItem) {
        val photos = feed?.items.orEmpty().filter { it.kind == FeedKind.Photo && it.media != null }
        val at = photos.indexOfFirst { it.messageId == item.messageId }.coerceAtLeast(0)
        val from = (at - PHOTO_WINDOW / 2).coerceAtLeast(0)
        val window = photos.subList(from, minOf(photos.size, from + PHOTO_WINDOW))
        val light = window.mapNotNull {
            it.media?.copy(miniThumbnailBytes = null, thumbnailPath = null, caption = it.media.caption.take(CAPTION_MAX))
        }
        parentFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, android.R.anim.fade_in, android.R.anim.fade_out)
            .hide(this)
            .add(R.id.main_browse_fragment, PhotoViewerFragment.newInstance(light, at - from, chatTitle))
            .addToBackStack(null)
            .commit()
    }

    /** Hold OK on a video: play it in mpv, from the start, or change whether it counts as watched. */
    private fun showVideoMenu(item: MediaItem) {
        val main = activity as? MainActivity ?: return
        val watched = WatchHistory.get(item.chatId, item.messageId)?.watched == true
        val actions = listOf(
            getString(R.string.menu_play_mpv) to {
                Player.play(main, main.playerLauncher, item, titleOf(item), useMpv = true)
            },
            getString(R.string.menu_from_start) to {
                Player.play(main, main.playerLauncher, item, titleOf(item), fromStart = true, upNext = upNext(item))
            },
            getString(if (watched) R.string.menu_mark_unwatched else R.string.menu_mark_watched) to {
                WatchHistory.setWatched(
                    item.chatId, item.messageId, item.title, titleOf(item), item.durationSeconds * 1000L, !watched
                )
            }
        )
        AlertDialog.Builder(main, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(item.title)
            .setItems((actions.map { it.first } + getString(R.string.menu_cancel)).toTypedArray()) { _, which ->
                actions.getOrNull(which)?.second?.invoke()
            }
            .show()
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(search.windowToken, 0)
    }

    private fun focusCard(position: Int) {
        val list = view?.findViewById<RecyclerView>(R.id.grid) ?: return
        list.post {
            val holder = list.findViewHolderForAdapterPosition(position)
            if (holder != null) {
                holder.itemView.requestFocus()
            } else {
                list.scrollToPosition(position)
                list.post { list.findViewHolderForAdapterPosition(position)?.itemView?.requestFocus() }
            }
        }
    }

    private fun kindLabel(kind: ChatKind) = getString(
        when (kind) {
            ChatKind.Channel -> R.string.kind_channel
            ChatKind.Group -> R.string.kind_group
            ChatKind.Person -> R.string.kind_person
            ChatKind.Saved -> R.string.kind_saved
        }
    )

    /** The header line for a focused message: its first line of text, or what it is. */
    private fun feedTitle(item: FeedItem): String =
        item.text.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
            ?: getString(if (item.kind == FeedKind.Photo) R.string.feed_photo else R.string.feed_video)

    private fun feedMeta(item: FeedItem): String {
        val parts = mutableListOf<String>()
        if (item.sender.isNotBlank()) parts += BidiFormatter.getInstance().unicodeWrap(item.sender)
        if (item.date > 0) {
            parts += DateUtils.formatDateTime(
                requireContext(), item.date * 1000L,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH
            )
        }
        item.media?.takeIf { item.kind == FeedKind.Video }?.let { parts += meta(it) }
        return parts.filter { it.isNotBlank() }.joinToString("  ·  ")
    }

    private fun meta(item: MediaItem): String {
        val parts = mutableListOf<String>()
        if (global) titleOf(item).takeIf { it.isNotEmpty() }?.let { parts += BidiFormatter.getInstance().unicodeWrap(it) }
        VideoCardAdapter.formatDuration(item.durationSeconds).takeIf { it.isNotEmpty() }?.let(parts::add)
        if (item.date > 0 && !feedMode) {
            parts += DateUtils.formatDateTime(
                requireContext(), item.date * 1000L,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH
            )
        }
        if (item.fileSizeBytes > 0) parts += Formatter.formatShortFileSize(requireContext(), item.fileSizeBytes)
        WatchHistory.get(item.chatId, item.messageId)?.let { entry ->
            parts += when {
                entry.watched -> getString(R.string.meta_watched)
                entry.durationMs > 0 -> minutes(entry.remainingMs).let { resources.getQuantityString(R.plurals.meta_left, it, it) }
                else -> getString(R.string.meta_stopped_at, Player.formatClock(entry.positionMs))
            }
        }
        return parts.joinToString("  ·  ")
    }

    companion object {
        private const val PAGING_PAUSE_MS = 10_000L
        private const val SPAN = 4
        private const val FEED_PRELOAD = 6
        private const val PHOTO_WINDOW = 100
        /** The photo viewer shows three lines of a caption; more would only weigh on its saved state. */
        private const val CAPTION_MAX = 400
        private const val MAX_EMPTY_PAGES = 5
        private const val ARG_ID = "id"
        private const val ARG_TITLE = "title"
        private const val ARG_KIND = "kind"
        private const val ARG_PHOTO = "photo"
        private const val ARG_COLOR = "color"
        private const val ARG_GLOBAL = "global"
        private const val ARG_QUERY = "query"
        private const val ARG_FEED = "feed"

        fun minutes(ms: Long) = ((ms + 59_999) / 60_000).toInt()

        /** Video search results for [query] from every chat in the library. */
        fun newGlobal(query: String) = ChatFragment().apply {
            arguments = bundleOf(ARG_GLOBAL to true, ARG_QUERY to query)
        }

        /** [chat]'s videos, or all its messages when Settings › Show messages is on. */
        fun newInstance(chat: GroupChat, color: Int) = ChatFragment().apply {
            arguments = bundleOf(
                ARG_ID to chat.id,
                ARG_TITLE to chat.title,
                ARG_KIND to chat.kind.ordinal,
                ARG_PHOTO to (chat.photoFileId ?: 0),
                ARG_COLOR to color,
                ARG_FEED to AppSettings.showMessages
            )
        }
    }
}
