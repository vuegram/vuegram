package com.vuegram.presentation.library

import android.app.AlertDialog
import android.graphics.Rect
import android.os.Bundle
import android.text.BidiFormatter
import android.view.FocusFinder
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.doOnNextLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vuegram.R
import com.vuegram.app.MainActivity
import com.vuegram.app.VoiceSearchHost
import com.vuegram.data.history.WatchHistory
import com.vuegram.data.library.ChatKind
import com.vuegram.data.library.Folders
import com.vuegram.data.library.GroupChat
import com.vuegram.data.library.HiddenChats
import com.vuegram.data.library.LibraryRepository
import com.vuegram.data.library.PinnedChats
import com.vuegram.data.settings.AppSettings
import com.vuegram.data.stream.TdStreamServer
import com.vuegram.data.update.Updates
import com.vuegram.presentation.update.UpdateDialogs
import com.vuegram.presentation.chat.ChatFragment
import com.vuegram.presentation.chat.VideoCardAdapter
import com.vuegram.presentation.player.Player
import com.vuegram.presentation.ui.FocusGlass
import com.vuegram.presentation.settings.SettingsFragment
import com.vuegram.presentation.ui.GlowView
import com.vuegram.presentation.ui.StatusText
import com.vuegram.presentation.ui.VoiceSearch
import com.vuegram.presentation.ui.alignWithTitle

/**
 * The first screen: Continue watching (videos started but not finished) and New in your channels (from pinned chats),
 * each one row that scrolls sideways; then pinned chats, then every other group and channel (and person, with
 * Settings › Show people on), most recently active first. Hold OK on a chat to pin or hide it; the Hidden card at the
 * end shows hidden chats again.
 */
class LibraryFragment : Fragment(R.layout.fragment_library), VoiceSearchHost {

    private val colors = HashMap<Long, Int>()
    private var chats: List<GroupChat> = emptyList()
    private var continueRows: List<LibraryRow.Continue> = emptyList()
    private var recentRows: List<LibraryRow.Recent> = emptyList()
    private val onFolders: () -> Unit = { view?.post { if (isAdded) buildFolderTabs() } }
    private var showHidden = false
    private var focusedKey: String? = null
    /** The card focus started on; until the viewer moves away from it, a rebuild may move focus to the new top. */
    private var startKey: String? = null
    private var viewerMoved = false
    /** The card [focusRow] is waiting to focus once the grid has laid out its new rows (and, in a shelf, its key). */
    private var focusTarget = 0
    private var focusTargetKey: String? = null
    private var focusWaiting = false
    /** The viewer went up to the top bar or the folder tabs: rows that arrive late leave focus there. */
    private var inTopBar = false
    private var topBarFocus: View? = null
    private val onGlobalFocus = ViewTreeObserver.OnGlobalFocusChangeListener { previous, focused ->
        val root = view ?: return@OnGlobalFocusChangeListener
        if (focused == null || !holds(root, focused)) return@OnGlobalFocusChangeListener
        if (grid?.let { holds(it, focused) } == true) {
            inTopBar = false
        } else if (isResumed && !focusWaiting && previous != null && holds(root, previous)) {
            // Only the viewer's own moves on this screen count: not Android's first focus at start (nothing, or a
            // view of another screen, had it before), nor focus placed while a rebuild lays out the grid.
            inTopBar = true
            topBarFocus = focused
        }
    }
    /**
     * A TV screen must never be left without focus (the remote would do nothing), nor with focus Android put on the
     * first button (Settings) when the viewer never went there. Should every other path miss, as when Android brings
     * VueGram back after ending it, the first card takes focus after the next layout.
     */
    private val keepSomethingFocused = ViewTreeObserver.OnGlobalLayoutListener {
        val root = view ?: return@OnGlobalLayoutListener
        val rows = adapter?.rows.orEmpty()
        val focused = root.findFocus()
        val onGrid = focused != null && grid?.let { holds(it, focused) } == true
        val viewersChoice = onGrid || (focused != null && inTopBar)
        if (viewersChoice || rows.isEmpty() || isHidden || !isResumed || focusWaiting) return@OnGlobalLayoutListener
        val position = firstFocusable(rows, 0)
        focusRow(position, (rows.getOrNull(position) as? LibraryRow.Shelf)?.items?.firstOrNull()?.key)
    }
    private var focusedPosition = 0
    /** Bumped by each load of Continue watching / New in your channels; an answer to an older load is dropped. */
    private var continueLoad = 0
    private var recentLoad = 0
    private var grid: RecyclerView? = null
    private var adapter: LibraryAdapter? = null
    private var glass: FocusGlass? = null
    private var glow: GlowView? = null
    private var title: TextView? = null
    private var sub: TextView? = null
    private val onHistory: () -> Unit = {
        view?.post {
            // A video just started or finished is no longer new: it moves to Continue watching, or is done.
            recentRows = recentRows.filter { WatchHistory.get(it.media.chatId, it.media.messageId) == null }
            loadContinue()
        }
    }
    /** Spoken words become a search across every chat, like typed ones. */
    private val voice = VoiceSearch(this) { words -> openSearch(words) }

    /** The settings the chat list was loaded with; coming back from Settings with a change reloads it. */
    private var settingsVersion = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Nothing is kept between sessions: drop videos left over from earlier streams (once per process, so also
        // when Android restores VueGram after killing it in the background).
        TdStreamServer.clearVideoCacheOnce()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val list = view.findViewById<RecyclerView>(R.id.grid).also { grid = it }
        val container = view.findViewById<FrameLayout>(R.id.grid_container)
        title = view.findViewById(R.id.hero_title)
        sub = view.findViewById(R.id.hero_sub)
        glow = view.findViewById(R.id.glow)
        val density = resources.displayMetrics.density

        val focusGlass = FocusGlass(container, list, 18f * density).also { glass = it }
        val libraryAdapter = LibraryAdapter(
            span = SPAN,
            onFocus = { position, row, frame, scale, hasFocus ->
                focusGlass.onFocus(frame, hasFocus, scale)
                if (hasFocus) onRowFocused(position, row)
            },
            onClick = ::onRowClicked,
            onLongClick = ::onRowMenu,
            onColor = { chatId, color ->
                colors[chatId] = color
                if (focusedKey == "chat:$chatId") applyColor(color)
            },
            onVertical = ::moveVertically,
            onShelf = focusGlass::follow
        ).also { adapter = it }
        view.viewTreeObserver.addOnGlobalFocusChangeListener(onGlobalFocus)
        view.viewTreeObserver.addOnGlobalLayoutListener(keepSomethingFocused)
        list.layoutManager = FocusLineLayoutManager(requireContext()).apply { spanSizeLookup = libraryAdapter.spanSizes }
        list.itemAnimator = null
        list.adapter = libraryAdapter

        val search = view.findViewById<EditText>(R.id.search)
        search.setOnEditorActionListener { _, actionId, event ->
            val enter = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE || enter) {
                val text = search.text.toString().trim()
                requireContext().getSystemService(InputMethodManager::class.java)
                    ?.hideSoftInputFromWindow(search.windowToken, 0)
                if (text.isNotEmpty()) openSearch(text)
                true
            } else {
                false
            }
        }

        checkForUpdate(view.findViewById(R.id.update_pill))
        StatusText.followConnection(view.findViewById(R.id.connection))
        view.findViewById<Button>(R.id.settings).setOnClickListener { openSettings() }
        voice.attach(view.findViewById(R.id.voice))
        view.findViewById<Button>(R.id.retry).setOnClickListener { loadChats() }

        Folders.addListener(onFolders)
        buildFolderTabs()
        loadChats()
        WatchHistory.addListener(onHistory)
    }

    /**
     * The viewer's Telegram folders as tabs above the grid, "All chats" first; hidden when they have none. Choosing
     * one shows that folder's chats (Continue watching stays on top).
     */
    private fun buildFolderTabs() {
        val root = view ?: return
        val tabs = root.findViewById<LinearLayout>(R.id.folder_tabs)
        val folders = Folders.all
        if (currentFolder != null && folders.none { it.id == currentFolder }) {
            currentFolder = null
            loadChats()
        }
        root.findViewById<View>(R.id.folder_scroll).visibility = if (folders.isEmpty()) View.GONE else View.VISIBLE
        tabs.removeAllViews()
        if (folders.isEmpty()) return
        val entries = listOf<Pair<Int?, String>>(null to getString(R.string.folders_all)) + folders.map { it.id to it.name }
        val density = resources.displayMetrics.density
        for ((id, name) in entries) {
            val tab = Button(requireContext(), null, 0, R.style.TvButton).apply {
                text = name
                textSize = 16f
                isAllCaps = false
                setBackgroundResource(R.drawable.tv_tab_bg)
                minHeight = 0
                minimumHeight = 0
                setPadding((22 * density).toInt(), 0, (22 * density).toInt(), 0)
                isSelected = id == currentFolder
                setOnClickListener {
                    if (currentFolder == id) return@setOnClickListener
                    currentFolder = id
                    for (i in 0 until tabs.childCount) tabs.getChildAt(i).isSelected = i == entries.indexOfFirst { it.first == id }
                    chats = emptyList()
                    loadChats()
                }
            }
            tabs.addView(tab, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, (44 * density).toInt()).apply {
                marginEnd = (12 * density).toInt()
            })
        }
    }

    /** Loads the chat list (with people when Settings › Show people is on); on failure says why and offers Retry. */
    private fun loadChats() {
        val root = view ?: return
        val status = root.findViewById<TextView>(R.id.status)
        val retry = root.findViewById<Button>(R.id.retry)
        retry.visibility = View.GONE
        settingsVersion = AppSettings.version
        if (chats.isEmpty()) status.setText(R.string.library_loading)
        LibraryRepository.loadGroups(withPeople = AppSettings.showPeople, folderId = currentFolder) { loaded, error ->
            root.post {
                if (!isAdded) return@post
                chats = loaded
                when {
                    error != null && loaded.isEmpty() -> {
                        status.text = StatusText.error(requireContext(), error)
                        retry.visibility = View.VISIBLE
                        retry.requestFocus()
                    }
                    loaded.isEmpty() -> status.setText(R.string.library_empty)
                    else -> status.text = null
                }
                // One rebuild once Continue Watching is known too, so the first screen opens at its top row.
                loadContinue()
                loadRecent()
            }
        }
    }

    /** "New in your channels": the newest unwatched videos of the pinned chats, so the viewer chooses what shows. */
    private fun loadRecent() {
        val hidden = HiddenChats.ids()
        // Pinned chats that are in the list (not a person pinned while people are turned off).
        val listed = chats.mapTo(HashSet()) { it.id }
        val ids = PinnedChats.ids().filter { it !in hidden && it in listed }.take(RECENT_CHATS)
        if (ids.isEmpty()) {
            recentLoad++
            recentRows = emptyList()
            rebuild()
            return
        }
        val titles = chats.associate { it.id to it.title }
        val load = ++recentLoad
        // Started or finished videos are left out: they are in Continue watching, or already seen.
        LibraryRepository.recentVideos(ids, isWatched = { WatchHistory.get(it.chatId, it.messageId) != null }) { items ->
            view?.post {
                if (!isAdded || load != recentLoad) return@post
                recentRows = items.map { LibraryRow.Recent(it, titles[it.chatId].orEmpty()) }
                rebuild()
            }
        }
    }

    override fun onDestroyView() {
        view?.viewTreeObserver?.removeOnGlobalFocusChangeListener(onGlobalFocus)
        view?.viewTreeObserver?.removeOnGlobalLayoutListener(keepSomethingFocused)
        Folders.removeListener(onFolders)
        WatchHistory.removeListener(onHistory)
        super.onDestroyView()
    }

    /** Looks up the videos on Continue Watching again (titles and thumbnails come from Telegram). */
    private fun loadContinue() {
        val hidden = HiddenChats.ids()
        val entries = WatchHistory.continueWatching(CONTINUE_MAX).filter { it.chatId !in hidden }
        val load = ++continueLoad
        if (entries.isEmpty()) {
            continueRows = emptyList()
            rebuild()
            return
        }
        LibraryRepository.loadMessages(entries.map { it.chatId to it.messageId }) { found ->
            view?.post {
                if (!isAdded || load != continueLoad) return@post
                continueRows = entries.mapNotNull { e -> found[e.chatId to e.messageId]?.let { LibraryRow.Continue(e, it) } }
                rebuild()
            }
        }
    }

    /** Rebuilds the grid from chats, Continue Watching and the hidden list, keeping focus on the same item. */
    private fun rebuild() {
        val list = adapter ?: return
        val hidden = HiddenChats.ids()
        val rows = mutableListOf<LibraryRow>()
        // Each a single row that scrolls sideways, so the first screen stays calm however many videos there are.
        if (continueRows.isNotEmpty()) {
            rows += LibraryRow.Header(getString(R.string.section_continue))
            rows += LibraryRow.Shelf("continue", continueRows)
        }
        if (recentRows.isNotEmpty()) {
            rows += LibraryRow.Header(getString(R.string.section_recent))
            rows += LibraryRow.Shelf("recent", recentRows)
        }
        // Pinned chats in the order they were pinned; they leave the main list so nothing shows twice.
        val byId = chats.associateBy { it.id }
        val pinned = PinnedChats.ids().filter { it !in hidden }.mapNotNull { byId[it] }
        if (pinned.isNotEmpty()) {
            rows += LibraryRow.Header(getString(R.string.section_pinned))
            rows += pinned.map { LibraryRow.Chat(it, hidden = false, pinned = true) }
        }
        val pinnedIds = pinned.mapTo(HashSet()) { it.id }
        if (chats.isNotEmpty()) {
            rows += LibraryRow.Header(getString(if (AppSettings.showPeople) R.string.section_all_chats else R.string.section_chats))
        }
        rows += chats.filter { it.id !in hidden && it.id !in pinnedIds }.map { LibraryRow.Chat(it, hidden = false) }
        val hiddenChats = chats.filter { it.id in hidden }
        if (hiddenChats.isNotEmpty()) {
            rows += LibraryRow.HiddenToggle(hiddenChats.size, showHidden)
            if (showHidden) rows += hiddenChats.map { LibraryRow.Chat(it, hidden = true) }
        }
        // Not moved yet: rows that arrive late (Continue watching, New in your channels) go on top and focus follows,
        // so the viewer sees them. Once the viewer has moved, focus stays on their card.
        val firstShow = focusedKey == null || !viewerMoved
        val hadFocus = grid?.hasFocus() == true || (firstShow && !inTopBar)
        list.rows = rows
        if (!hadFocus || isHidden || rows.isEmpty()) return
        // First time on screen: start at the very top, so the first section heading shows too.
        if (firstShow) grid?.scrollToPosition(0)
        val keyIndex = if (firstShow) -1 else indexOfKey(rows, focusedKey)
        val target = when {
            firstShow -> 0
            keyIndex >= 0 -> keyIndex
            else -> focusedPosition.coerceAtMost(rows.lastIndex)
        }
        val position = firstFocusable(rows, target)
        val row = rows.getOrNull(position)
        // In a shelf, the first card (on first show) or the card the viewer was on.
        val key = if (firstShow) (row as? LibraryRow.Shelf)?.items?.firstOrNull()?.key ?: row?.key else focusedKey
        if (firstShow) startKey = key
        focusRow(position, key)
    }

    /** The row with [key], or the shelf holding the card with [key]; -1 when neither is there. */
    private fun indexOfKey(rows: List<LibraryRow>, key: String?): Int =
        rows.indexOfFirst { it.key == key || (it is LibraryRow.Shelf && it.indexOf(key) >= 0) }

    private fun firstFocusable(rows: List<LibraryRow>, from: Int): Int {
        if (rows.isEmpty()) return 0
        val start = from.coerceIn(0, rows.lastIndex)
        for (i in start until rows.size) if (rows[i] !is LibraryRow.Header) return i
        for (i in start downTo 0) if (rows[i] !is LibraryRow.Header) return i
        return 0
    }

    private fun onRowFocused(position: Int, row: LibraryRow) {
        focusedPosition = position
        focusedKey = row.key
        if (startKey != null && row.key != startKey) viewerMoved = true
        when (row) {
            is LibraryRow.Chat -> {
                title?.text = row.chat.title
                val kind = getString(
                    when (row.chat.kind) {
                        ChatKind.Channel -> R.string.kind_channel
                        ChatKind.Group -> R.string.kind_group
                        ChatKind.Person -> R.string.kind_person
                        ChatKind.Saved -> R.string.kind_saved
                    }
                )
                sub?.text = when {
                    row.hidden -> getString(R.string.hidden_kind, kind)
                    row.pinned -> getString(R.string.pinned_kind, kind)
                    else -> kind
                }
                applyColor(colors[row.chat.id] ?: 0)
            }
            is LibraryRow.Continue -> {
                title?.text = row.entry.title.ifBlank { row.media.title }
                // Isolate the chat name: a Hebrew name next to "72 min left" otherwise swaps the two parts around.
                sub?.text = getString(
                    R.string.continue_sub, BidiFormatter.getInstance().unicodeWrap(row.entry.chatTitle),
                    ChatFragment.minutes(row.entry.remainingMs).let { resources.getQuantityString(R.plurals.meta_left, it, it) }
                )
                applyColor(colors[row.entry.chatId] ?: 0)
            }
            is LibraryRow.Recent -> {
                title?.text = row.media.title
                val duration = VideoCardAdapter.formatDuration(row.media.durationSeconds)
                sub?.text = listOf(BidiFormatter.getInstance().unicodeWrap(row.chatTitle), duration).filter { it.isNotEmpty() }.joinToString("  ·  ")
                applyColor(colors[row.media.chatId] ?: 0)
            }
            is LibraryRow.HiddenToggle -> {
                title?.setText(R.string.hidden_title)
                sub?.text = resources.getQuantityString(R.plurals.hidden_sub, row.count, row.count)
            }
            // A shelf itself never has focus, only its cards (reported as Continue or Recent).
            is LibraryRow.Header, is LibraryRow.Shelf -> Unit
        }
        val heroTitle = title ?: return
        sub?.let { alignWithTitle(heroTitle, it) }
    }

    private fun onRowClicked(row: LibraryRow) {
        when (row) {
            is LibraryRow.Chat -> openChat(row.chat)
            is LibraryRow.Continue -> (activity as? MainActivity)?.let {
                Player.play(it, it.playerLauncher, row.media, row.entry.chatTitle)
            }
            is LibraryRow.Recent -> (activity as? MainActivity)?.let {
                Player.play(it, it.playerLauncher, row.media, row.chatTitle)
            }
            is LibraryRow.HiddenToggle -> {
                showHidden = !showHidden
                rebuild()
            }
            is LibraryRow.Header, is LibraryRow.Shelf -> Unit
        }
    }

    /** Hold OK: hide or unhide a chat, or take a video off Continue Watching. */
    private fun onRowMenu(row: LibraryRow) {
        val context = requireContext()
        when (row) {
            is LibraryRow.Chat -> {
                val chatId = row.chat.id
                // A hidden chat can only be shown again; a visible one can be pinned/unpinned or hidden.
                val actions = if (row.hidden) {
                    listOf(getString(R.string.menu_unhide) to {
                        HiddenChats.setHidden(chatId, false)
                    })
                } else {
                    listOf(
                        getString(if (row.pinned) R.string.menu_unpin else R.string.menu_pin) to {
                            PinnedChats.setPinned(chatId, !row.pinned)
                        },
                        getString(R.string.menu_hide) to {
                            HiddenChats.setHidden(chatId, true)
                            PinnedChats.setPinned(chatId, false)
                        }
                    )
                }
                val labels = actions.map { it.first } + getString(R.string.menu_cancel)
                AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(row.chat.title)
                    .setItems(labels.toTypedArray()) { _, which ->
                        actions.getOrNull(which)?.let { (_, action) ->
                            action()
                            rebuild()
                            loadContinue()
                            loadRecent()
                        }
                    }
                    .show()
            }
            is LibraryRow.Continue -> {
                AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(row.entry.title)
                    .setItems(
                        arrayOf(getString(R.string.menu_mark_watched), getString(R.string.menu_remove), getString(R.string.menu_cancel))
                    ) { _, which ->
                        when (which) {
                            0 -> WatchHistory.markWatched(row.entry.chatId, row.entry.messageId)
                            1 -> WatchHistory.forget(row.entry.chatId, row.entry.messageId)
                        }
                    }
                    .show()
            }
            else -> Unit
        }
    }

    private fun applyColor(color: Int) {
        glow?.setGlowColor(color)
        glass?.setAccent(color)
    }

    private fun openChat(chat: GroupChat) {
        // Hide rather than replace, so Back returns to the same card with the grid exactly as it was.
        parentFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, android.R.anim.fade_in, android.R.anim.fade_out)
            .hide(this)
            .add(R.id.main_browse_fragment, ChatFragment.newInstance(chat, colors[chat.id] ?: 0))
            .addToBackStack(null)
            .commit()
    }

    /**
     * Updates, the quiet way (like SmartTube): on the first start of a new version, "What's new"; then release builds
     * look for a newer VueGram on GitHub at most every 12 hours and download it in the background. Once it is ready the
     * header's pill says so and VueGram asks once: Install now or Later. With automatic updates off (or a metered
     * network) the pill and the question offer the download instead.
     */
    private fun checkForUpdate(pill: Button) {
        if (!startChecked) {
            startChecked = true
            Updates.afterStart(requireContext())?.let { notes -> UpdateDialogs.whatsNew(requireContext(), notes) }
        }
        Updates.checkQuietly(requireContext(),
            onReady = { update ->
                if (!isAdded) return@checkQuietly
                pill.text = getString(R.string.update_ready_pill, update.version)
                pill.visibility = View.VISIBLE
                pill.setOnClickListener { UpdateDialogs.ready(requireContext(), update) }
                if (!Updates.wasDismissed(requireContext(), update.version)) UpdateDialogs.ready(requireContext(), update)
            },
            onAvailable = { release ->
                if (!isAdded) return@checkQuietly
                pill.text = getString(R.string.update_pill, release.version)
                pill.visibility = View.VISIBLE
                pill.setOnClickListener { UpdateDialogs.offer(requireContext(), release) }
                if (!Updates.wasDismissed(requireContext(), release.version)) UpdateDialogs.offer(requireContext(), release)
            })
    }

    override fun startVoiceSearch() = voice.listen(requireContext())

    private fun openSettings() {
        parentFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, android.R.anim.fade_in, android.R.anim.fade_out)
            .hide(this)
            .add(R.id.main_browse_fragment, SettingsFragment())
            .addToBackStack(null)
            .commit()
    }

    /** Results from every group and channel, on the chat screen's layout; Back returns here. */
    private fun openSearch(query: String) {
        parentFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, android.R.anim.fade_in, android.R.anim.fade_out)
            .hide(this)
            .add(R.id.main_browse_fragment, ChatFragment.newGlobal(query))
            .addToBackStack(null)
            .commit()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            // Back from Settings with a change: the list itself may differ (people shown or not).
            if (settingsVersion != AppSettings.version) loadChats()
            // Videos may have been played inside the chat: refresh Continue Watching, then refocus the same card.
            loadContinue()
            // Back to where the viewer was: the top bar button they used (Settings, search), or the same card.
            val top = topBarFocus
            if (inTopBar && top != null && top.isShown) {
                top.requestFocus()
            } else {
                val rows = adapter?.rows.orEmpty()
                val index = indexOfKey(rows, focusedKey)
                // The card is gone (watched, hidden): the nearest card where it was.
                focusRow(if (index >= 0) index else firstFocusable(rows, focusedPosition), focusedKey)
            }
        }
    }

    /**
     * Up or Down from a card at [position] (a shelf's position for its cards). RecyclerView's own search fails here:
     * it offers a section heading (which can't take focus), scrolls to it and keeps focus where it was, and shelves
     * scroll sideways inside the grid. So: the next line of cards up or down, and in it the card nearest the one left;
     * from the first line, Up goes to the top bar.
     */
    private fun moveVertically(position: Int, card: View, down: Boolean): Boolean {
        val rows = adapter?.rows ?: return false
        val lookup = adapter?.spanSizes ?: return false
        val list = grid ?: return false
        if (position !in rows.indices) return false
        val line = lookup.getSpanGroupIndex(position, SPAN)
        val step = if (down) 1 else -1
        var targetLine = -1
        val candidates = mutableListOf<Int>()
        var i = position + step
        while (i in rows.indices) {
            val rowLine = lookup.getSpanGroupIndex(i, SPAN)
            if (targetLine != -1 && rowLine != targetLine) break
            if (rowLine != line && rows[i] !is LibraryRow.Header) {
                targetLine = rowLine
                candidates += i
            }
            i += step
        }
        if (candidates.isEmpty()) return if (down) true else toTopBar(card)
        val x = IntArray(2).also { card.getLocationOnScreen(it) }[0] + card.width / 2
        val gridX = IntArray(2).also { list.getLocationOnScreen(it) }[0] + list.paddingStart
        val cell = (list.width - list.paddingStart - list.paddingEnd) / SPAN.toFloat()
        val target = candidates.minByOrNull {
            val center = gridX + (lookup.getSpanIndex(it, SPAN) + lookup.getSpanSize(it) / 2f) * cell
            kotlin.math.abs(center - x)
        } ?: return false
        val holder = list.findViewHolderForAdapterPosition(target)
        if (holder != null && rows[target] is LibraryRow.Shelf && adapter?.focusNearestInShelf(holder, x) == true) return true
        if (holder != null && rows[target] !is LibraryRow.Shelf) return holder.itemView.requestFocus()
        focusRow(target)
        return true
    }

    /** From the grid's first line: the nearest folder tab, or the nearest of Settings, the microphone and search. */
    private fun toTopBar(card: View): Boolean {
        val root = view as? ViewGroup ?: return false
        val target: ViewGroup = if (root.findViewById<View>(R.id.folder_scroll).visibility == View.VISIBLE) {
            root.findViewById(R.id.folder_tabs)
        } else {
            root.findViewById(R.id.top_bar)
        }
        val rect = Rect()
        card.getDrawingRect(rect)
        root.offsetDescendantRectToMyCoords(card, rect)
        root.offsetRectIntoDescendantCoords(target, rect)
        val next = FocusFinder.getInstance().findNextFocusFromRect(target, rect, View.FOCUS_UP)
            ?: target.getFocusables(View.FOCUS_UP).firstOrNull()
        return next?.requestFocus() == true
    }

    /**
     * Puts focus on the item at [position], scrolling it into view first if it is off screen; for a shelf, on its card
     * with [key] when it has one, else its first card.
     */
    private fun focusRow(position: Int, key: String? = null, attempt: Int = 0) {
        val list = grid ?: return
        // Nothing to focus (no rows yet, or a position from before the rows changed), or tried enough: leave it.
        if (position !in (adapter?.rows ?: return).indices || attempt > MAX_FOCUS_ATTEMPTS) return
        focusTarget = position
        focusTargetKey = key
        // New rows have no cards until the grid's next layout, and under the player that layout waits until this screen
        // shows again. Wait for it: giving up leaves focus to Android, which puts it on the first button, Settings.
        if (list.isLayoutRequested || !list.isLaidOut) {
            if (!focusWaiting) {
                focusWaiting = true
                list.doOnNextLayout {
                    focusWaiting = false
                    list.post { if (grid === list && !inTopBar) focusRow(focusTarget, focusTargetKey, attempt + 1) }
                }
            }
            return
        }
        val holder = list.findViewHolderForAdapterPosition(position)
        if (holder != null) {
            if (adapter?.focusInShelf(holder, key) != true) holder.itemView.requestFocus()
        } else {
            list.scrollToPosition(position)
            focusWaiting = true
            list.doOnNextLayout {
                focusWaiting = false
                list.post { if (grid === list && !inTopBar) focusRow(focusTarget, focusTargetKey, attempt + 1) }
            }
        }
    }

    /**
     * The grid scrolls the way TV apps do: the focused line always comes to the same height, one section heading's
     * room below the grid's top edge, so its heading shows above it and the line before leaves the screen whole instead of
     * leaving its labels behind; the screen also stops shifting by a different amount on every Down.
     */
    private inner class FocusLineLayoutManager(context: android.content.Context) : GridLayoutManager(context, SPAN) {
        private var headingHeight = 0

        override fun requestChildRectangleOnScreen(
            parent: RecyclerView, child: View, rect: Rect, immediate: Boolean, focusedChildVisible: Boolean
        ): Boolean {
            val rows = adapter?.rows ?: return false
            for (i in 0 until childCount) {
                val view = getChildAt(i) ?: continue
                if (rows.getOrNull(getPosition(view)) is LibraryRow.Header && view.height > 0) {
                    headingHeight = view.height
                    break
                }
            }
            // The heading flush with the grid's clipped top edge: nothing of the line before shows above it.
            val dy = getDecoratedTop(child) - headingHeight
            if (dy == 0) return false
            if (immediate) parent.scrollBy(0, dy) else parent.smoothScrollBy(0, dy)
            return true
        }
    }

    /** True when [view] is [ancestor] or inside it. */
    private fun holds(ancestor: View, view: View): Boolean {
        var current: Any? = view
        while (current is View) {
            if (current === ancestor) return true
            current = current.parent
        }
        return false
    }

    companion object {
        /** "What's new" and the cleanup after an update run once per process, not on every return to the library. */
        private var startChecked = false
        /** The Telegram folder shown (null: all chats); kept while VueGram runs. */
        private var currentFolder: Int? = null
        /** How many of the first chats "New in your channels" looks at (one request each, one after the other). */
        private const val RECENT_CHATS = 12
        private const val SPAN = 6
        /** Continue watching is one sideways row, so it can hold more than fits on screen. */
        private const val CONTINUE_MAX = 10
        /** How many layouts [focusRow] waits for its card before giving up. */
        private const val MAX_FOCUS_ATTEMPTS = 3
    }
}
