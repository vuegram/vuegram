package com.vuegram.data.library

import com.vuegram.core.Texts
import com.vuegram.core.tdlib.client.TdLibClient
import com.vuegram.domain.model.MediaItem
import org.drinkless.tdlib.TdApi
import java.util.concurrent.ConcurrentHashMap

/** What a chat in the library is. People and Saved Messages are listed only when Settings › Show people is on. */
enum class ChatKind { Group, Channel, Person, Saved }

/** A chat shown in the library: a group or channel, or (with Show people on) a person or Saved Messages. Never a bot. */
data class GroupChat(
    val id: Long,
    val title: String,
    val kind: ChatKind,
    val photoFileId: Int?,
    val miniPhoto: ByteArray?
) {
    val isChannel get() = kind == ChatKind.Channel
}

/**
 * Where the next page of a chat's videos starts: video messages and video files are searched separately. With
 * [oldestFirst] the pages run from the chat's first video forward (a series in the order it was posted).
 */
data class VideoCursor(
    val fromVideo: Long = 0L,
    val fromDocument: Long = 0L,
    val videosDone: Boolean = false,
    val documentsDone: Boolean = false,
    val oldestFirst: Boolean = false
) {
    val isDone get() = videosDone && documentsDone
}

/** A channel's or group's details for its page: how many follow it and what it says about itself. */
data class ChatInfo(val memberCount: Int, val description: String)

/** Where the next page of an all-chats search starts (Telegram's offsets for video messages and video files). */
data class GlobalCursor(
    val videoOffset: String = "",
    val documentOffset: String = "",
    val videosDone: Boolean = false,
    val documentsDone: Boolean = false
) {
    val isDone get() = videosDone && documentsDone
}

object LibraryRepository {
    private const val CHAT_LIMIT = 200
    private const val PAGE = 30
    /** The newest videos asked for each pinned chat: its row on the home screen, and its part of "New in your channels". */
    private const val LATEST_PER_CHAT = 12
    private const val LATEST_TTL_MS = 10 * 60 * 1000L
    /** The least VueGram waits after Telegram says slow down before asking for the rows again. */
    private const val FLOOD_PAUSE_MS = 60 * 1000L

    private val client get() = TdLibClient.client
    private val paths = ConcurrentHashMap<Int, String>()
    /** The chats from the last [loadGroups], to label and filter all-chats search results. */
    private val groups = ConcurrentHashMap<Long, GroupChat>()


    fun chatTitle(chatId: Long): String = groups[chatId]?.title.orEmpty()

    /**
     * The main chat list, most recently active first: groups and channels, plus people and Saved Messages when
     * [withPeople] is on. Bots and deleted accounts are always left out.
     */
    fun loadGroups(withPeople: Boolean = false, folderId: Int? = null, onResult: (List<GroupChat>, String?) -> Unit) {
        // Who "me" is must be known before the chats are sorted, or each private chat would ask at the same time.
        if (withPeople) myUserId { loadChatList(true, folderId, onResult) } else loadChatList(false, folderId, onResult)
    }

    /** [folderId] is one of the viewer's Telegram folders ([Folders]), or null for the whole main chat list. */
    private fun loadChatList(withPeople: Boolean, folderId: Int?, onResult: (List<GroupChat>, String?) -> Unit) {
        val list: TdApi.ChatList = if (folderId != null) TdApi.ChatListFolder(folderId) else TdApi.ChatListMain()
        client.send(TdApi.GetChats(list, CHAT_LIMIT)) { result ->
            val ids = (result as? TdApi.Chats)?.chatIds
            if (ids == null) {
                onResult(emptyList(), (result as? TdApi.Error)?.message ?: Texts.couldNotLoadChats())
                return@send
            }
            if (ids.isEmpty()) {
                onResult(emptyList(), null)
                return@send
            }
            val found = arrayOfNulls<GroupChat>(ids.size)
            var pending = ids.size
            val lock = Any()
            val fresh = HashMap<Long, GroupChat>()
            ids.forEachIndexed { index, id ->
                // GetChat and GetUser are answered from TDLib's local database, not the network.
                client.send(TdApi.GetChat(id)) { chatResult ->
                    classify(chatResult as? TdApi.Chat, withPeople) { group ->
                        synchronized(lock) {
                            found[index] = group
                            if (group != null) fresh[group.id] = group
                            pending--
                            if (pending == 0) {
                                // Swapped in whole: chats the list no longer shows (people, after Show people is
                                // turned off) leave "Search all channels" too.
                                // Only the whole list: a folder's chats are a part of it, and "Search all channels"
                                // still covers everything.
                                if (folderId == null) groups.clear()
                                groups.putAll(fresh)
                                onResult(found.filterNotNull(), null)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun classify(chat: TdApi.Chat?, withPeople: Boolean, onResult: (GroupChat?) -> Unit) {
        when (val type = chat?.type) {
            is TdApi.ChatTypeBasicGroup -> onResult(toGroup(chat, ChatKind.Group))
            is TdApi.ChatTypeSupergroup -> onResult(toGroup(chat, if (type.isChannel) ChatKind.Channel else ChatKind.Group))
            is TdApi.ChatTypePrivate -> {
                if (!withPeople) {
                    onResult(null)
                    return
                }
                if (type.userId == me) {
                    onResult(toGroup(chat, ChatKind.Saved).copy(title = Texts.savedMessages()))
                    return
                }
                client.send(TdApi.GetUser(type.userId)) { user ->
                    val kind = (user as? TdApi.User)?.type
                    val isPerson = kind is TdApi.UserTypeRegular || kind is TdApi.UserTypeUnknown
                    onResult(if (isPerson) toGroup(chat, ChatKind.Person) else null)
                }
            }
            else -> onResult(null)   // secret chats and anything new
        }
    }

    @Volatile private var me = 0L

    /** The logged-in account's user id (its private chat is Saved Messages); 0 if TDLib can't say. */
    private fun myUserId(onResult: (Long) -> Unit) {
        if (me != 0L) {
            onResult(me)
            return
        }
        client.send(TdApi.GetMe()) { result ->
            (result as? TdApi.User)?.id?.let { me = it }
            onResult(me)
        }
    }

    internal fun toGroup(chat: TdApi.Chat, kind: ChatKind) = GroupChat(
        id = chat.id,
        title = chat.title.orEmpty(),
        kind = kind,
        photoFileId = chat.photo?.big?.id ?: chat.photo?.small?.id,
        miniPhoto = chat.photo?.minithumbnail?.data
    )

    /**
     * The next page of a chat's videos, newest first, and the cursor for the page after it. A non-empty [query]
     * keeps only videos whose caption or file name matches it (Telegram's own search).
     */
    fun loadVideos(
        chatId: Long,
        cursor: VideoCursor,
        query: String = "",
        onResult: (List<MediaItem>, VideoCursor, String?) -> Unit
    ) {
        // One search after the other, never in parallel: Telegram answers bursts with FLOOD_WAIT.
        val old = cursor.oldestFirst
        search(chatId, query, cursor.fromVideo, cursor.videosDone, TdApi.SearchMessagesFilterVideo(), old) { videos, nextVideo, error ->
            search(chatId, query, cursor.fromDocument, cursor.documentsDone, TdApi.SearchMessagesFilterDocument(), old) { files, nextFile, error2 ->
                val merged = (videos + files).distinctBy { it.messageId }
                val items = if (old) merged.sortedWith(compareBy({ it.date }, { it.messageId })) else merged.sortedByDescending { it.date }
                val next = VideoCursor(
                    fromVideo = nextVideo ?: cursor.fromVideo,
                    fromDocument = nextFile ?: cursor.fromDocument,
                    // Done only on a real last page: after an error (FLOOD_WAIT, no network) the same page is asked again.
                    videosDone = cursor.videosDone || (nextVideo == null && error == null),
                    documentsDone = cursor.documentsDone || (nextFile == null && error2 == null),
                    oldestFirst = old
                )
                onResult(items, next, error ?: error2)
            }
        }
    }

    /**
     * [onResult] gets the mapped videos and the id to continue from, or null when the search is exhausted. Newest first,
     * pages go back in time from [from]; with [oldestFirst] they go forward from it instead (Telegram's negative offset:
     * the messages after [from], starting at the chat's beginning when [from] is 0).
     */
    private fun search(
        chatId: Long,
        query: String,
        from: Long,
        done: Boolean,
        filter: TdApi.SearchMessagesFilter,
        oldestFirst: Boolean = false,
        onResult: (List<MediaItem>, Long?, String?) -> Unit
    ) {
        if (done) {
            onResult(emptyList(), null, null)
            return
        }
        val request = if (oldestFirst) {
            TdApi.SearchChatMessages(chatId, null, query, null, from.coerceAtLeast(1L), -(PAGE - 1), PAGE, filter)
        } else {
            TdApi.SearchChatMessages(chatId, null, query, null, from, 0, PAGE, filter)
        }
        client.send(request) { result ->
            when (result) {
                is TdApi.FoundChatMessages -> {
                    // Forward pages include the message they start from (or, if it was deleted, an older one): keep only
                    // what comes after it.
                    val messages = result.messages.orEmpty().filter { !oldestFirst || it.id > from }
                    val items = messages.mapNotNull(::mapMessageToMedia)
                        .filter { it.type == com.vuegram.domain.model.MediaType.Video }
                    // Telegram may return a short page before the end: forward paging stops only on a page with nothing
                    // newer.
                    val next = if (oldestFirst) {
                        messages.maxOfOrNull { it.id }
                    } else {
                        result.nextFromMessageId.takeIf { it != 0L && messages.isNotEmpty() }
                    }
                    onResult(items, next, null)
                }
                is TdApi.Error -> onResult(emptyList(), null, result.message)
                else -> onResult(emptyList(), null, Texts.unexpectedAnswer())
            }
        }
    }

    /**
     * Videos matching [query] across every chat in the library (Telegram's global search), newest first. Results
     * from chats the library doesn't list (bots, and people unless Show people is on) and hidden chats are dropped. One request at a time, like [loadVideos].
     */
    fun searchAll(query: String, cursor: GlobalCursor, onResult: (List<MediaItem>, GlobalCursor, String?) -> Unit) {
        searchGlobal(query, cursor.videoOffset, cursor.videosDone, TdApi.SearchMessagesFilterVideo()) { videos, nextVideo, error ->
            searchGlobal(query, cursor.documentOffset, cursor.documentsDone, TdApi.SearchMessagesFilterDocument()) { files, nextFile, error2 ->
                val hidden = HiddenChats.ids()
                val items = (videos + files)
                    // Before the chat list has loaded (a voice search right after start) nothing is left out.
                    .filter { (groups.isEmpty() || groups.containsKey(it.chatId)) && it.chatId !in hidden }
                    .distinctBy { it.chatId to it.messageId }
                    .sortedByDescending { it.date }
                val next = GlobalCursor(
                    videoOffset = nextVideo ?: cursor.videoOffset,
                    documentOffset = nextFile ?: cursor.documentOffset,
                    videosDone = cursor.videosDone || (nextVideo == null && error == null),
                    documentsDone = cursor.documentsDone || (nextFile == null && error2 == null)
                )
                onResult(items, next, error ?: error2)
            }
        }
    }

    private fun searchGlobal(
        query: String,
        offset: String,
        done: Boolean,
        filter: TdApi.SearchMessagesFilter,
        onResult: (List<MediaItem>, String?, String?) -> Unit
    ) {
        if (done || query.isBlank()) {
            onResult(emptyList(), null, null)
            return
        }
        client.send(TdApi.SearchMessages(TdApi.ChatListMain(), query, offset, PAGE, filter, null, 0, 0)) { result ->
            when (result) {
                is TdApi.FoundMessages -> {
                    val messages = result.messages.orEmpty()
                    val items = messages.mapNotNull(::mapMessageToMedia)
                        .filter { it.type == com.vuegram.domain.model.MediaType.Video }
                    val next = result.nextOffset?.takeIf { it.isNotEmpty() && messages.isNotEmpty() }
                    onResult(items, next, null)
                }
                is TdApi.Error -> onResult(emptyList(), null, result.message)
                else -> onResult(emptyList(), null, Texts.unexpectedAnswer())
            }
        }
    }

    /**
     * The next page of a chat's messages for the "all messages" view, newest first. A non-empty [query] uses
     * Telegram's search over every message; otherwise the chat's history is read page by page. Sender names are
     * filled in for groups (channels and people don't need them). One request after the other, never in parallel.
     */
    fun loadFeed(
        chatId: Long,
        cursor: FeedCursor,
        query: String = "",
        withSenders: Boolean,
        onResult: (List<FeedItem>, FeedCursor, String?) -> Unit
    ) {
        if (cursor.done) {
            onResult(emptyList(), cursor, null)
            return
        }
        val request: TdApi.Function<*> = if (query.isBlank()) {
            TdApi.GetChatHistory(chatId, cursor.fromMessageId, 0, PAGE, false)
        } else {
            TdApi.SearchChatMessages(chatId, null, query, null, cursor.fromMessageId, 0, PAGE, null)
        }
        client.send(request) { result ->
            val (messages, next) = when (result) {
                is TdApi.Messages -> result.messages.orEmpty().let { it to it.lastOrNull()?.id }
                is TdApi.FoundChatMessages -> result.messages.orEmpty().let { it to result.nextFromMessageId.takeIf { id -> id != 0L } }
                is TdApi.Error -> {
                    onResult(emptyList(), cursor, result.message)
                    return@send
                }
                else -> {
                    onResult(emptyList(), cursor, Texts.unexpectedAnswer())
                    return@send
                }
            }
            // History can come back short (TDLib returns what it has locally first), so only an empty page ends it.
            val nextCursor = if (messages.isEmpty() || next == null) FeedCursor(cursor.fromMessageId, done = true) else FeedCursor(next)
            val items = messages.mapNotNull(::mapMessageToFeed)
            if (!withSenders) {
                onResult(items, nextCursor, null)
            } else {
                withSenderNames(items) { named -> onResult(named, nextCursor, null) }
            }
        }
    }

    private val senderNames = ConcurrentHashMap<Long, String>()

    /** Fills in each item's sender name, looking up unknown senders one at a time (TDLib answers from its cache). */
    private fun withSenderNames(items: List<FeedItem>, onResult: (List<FeedItem>) -> Unit) {
        val unknown = items.map { it.senderId }.filter { it != 0L && !senderNames.containsKey(it) }.distinct()
        fun next(index: Int) {
            if (index >= unknown.size) {
                onResult(items.map { it.copy(sender = senderNames[it.senderId].orEmpty()) })
                return
            }
            val id = unknown[index]
            // A positive id is a user; a negative one is a chat posting as itself (a channel or an anonymous admin).
            val request: TdApi.Function<*> = if (id > 0) TdApi.GetUser(id) else TdApi.GetChat(id)
            client.send(request) { result ->
                val name = when (result) {
                    is TdApi.User -> displayName(result)
                    is TdApi.Chat -> result.title.orEmpty()
                    else -> ""
                }
                senderNames[id] = name
                next(index + 1)
            }
        }
        next(0)
    }

    /** Each pinned chat's latest videos and when they were asked for, so a changed list asks only for what's new. */
    private val latest = ConcurrentHashMap<Long, Pair<Long, List<MediaItem>>>()
    /** Bumped by each [latestByChat]; an older round stops asking as soon as a newer one starts. */
    @Volatile private var latestRound = 0
    /** After Telegram said slow down: no new requests for these chats before this time (ms). */
    @Volatile private var latestNotBefore = 0L

    /**
     * The newest videos of each chat in [chatIds] (the pinned ones), newest first, at most [LATEST_PER_CHAT] each: the
     * home screen's row for each pinned channel and, merged, "New in your channels". Each chat's answer is kept for
     * [LATEST_TTL_MS]; only chats without a fresh one are asked, one request after the other (never a burst). A newer
     * call stops an older one that is still asking. When Telegram says slow down, the round stops, what is known is
     * shown, and nothing is asked again for [FLOOD_PAUSE_MS] or as long as Telegram said.
     */
    fun latestByChat(chatIds: List<Long>, onResult: (Map<Long, List<MediaItem>>) -> Unit) {
        val round = ++latestRound
        val now = System.currentTimeMillis()
        fun known() = LinkedHashMap<Long, List<MediaItem>>().apply { for (id in chatIds) latest[id]?.let { put(id, it.second) } }
        val stale = chatIds.filter { id -> latest[id]?.let { now - it.first >= LATEST_TTL_MS } ?: true }
        if (stale.isEmpty() || now < latestNotBefore) {
            onResult(known())
            return
        }
        fun next(index: Int) {
            if (round != latestRound) return   // a newer round took over
            if (index >= stale.size) return onResult(known())
            val chatId = stale[index]
            val filter = TdApi.SearchMessagesFilterVideo()
            client.send(TdApi.SearchChatMessages(chatId, null, "", null, 0, 0, LATEST_PER_CHAT, filter)) { result ->
                if (result is TdApi.Error && (result.code == 429 || result.code >= 500)) {
                    val wait = Regex("""(\d+)""").find(result.message.orEmpty())?.value?.toLongOrNull()?.times(1000) ?: 0L
                    latestNotBefore = System.currentTimeMillis() + maxOf(FLOOD_PAUSE_MS, wait)
                    return@send onResult(known())
                }
                // A chat that can't be searched (400, 403) counts as having none, until it is asked again later.
                latest[chatId] = System.currentTimeMillis() to (result as? TdApi.FoundChatMessages)?.messages.orEmpty()
                    .mapNotNull(::mapMessageToMedia)
                    .filter { it.type == com.vuegram.domain.model.MediaType.Video }
                next(index + 1)
            }
        }
        next(0)
    }

    /** "New in your channels" from [latestByChat]'s answer: the newest unwatched videos across those chats. */
    fun newest(byChat: Map<Long, List<MediaItem>>, isWatched: (MediaItem) -> Boolean, max: Int): List<MediaItem> =
        byChat.values.flatten()
            .distinctBy { it.chatId to it.messageId }
            .filterNot(isWatched)
            .sortedByDescending { it.date }
            .take(max)

    /**
     * How many follow a channel or group and its description, for the top of its page; null for people and Saved
     * Messages, or when Telegram can't say. Two requests, one after the other (TDLib usually answers from its cache).
     */
    fun chatInfo(chatId: Long, onResult: (ChatInfo?) -> Unit) {
        client.send(TdApi.GetChat(chatId)) { chat ->
            when (val type = (chat as? TdApi.Chat)?.type) {
                is TdApi.ChatTypeSupergroup -> client.send(TdApi.GetSupergroupFullInfo(type.supergroupId)) { full ->
                    onResult((full as? TdApi.SupergroupFullInfo)?.let { ChatInfo(it.memberCount, it.description.orEmpty()) })
                }
                is TdApi.ChatTypeBasicGroup -> client.send(TdApi.GetBasicGroupFullInfo(type.basicGroupId)) { full ->
                    onResult((full as? TdApi.BasicGroupFullInfo)?.let { ChatInfo(it.members.orEmpty().size, it.description.orEmpty()) })
                }
                else -> onResult(null)
            }
        }
    }

    /** Looks up saved videos again (for Continue Watching), one message at a time; missing ones are left out. */
    fun loadMessages(keys: List<Pair<Long, Long>>, onResult: (Map<Pair<Long, Long>, MediaItem>) -> Unit) {
        val found = LinkedHashMap<Pair<Long, Long>, MediaItem>()
        fun next(index: Int) {
            if (index >= keys.size) {
                onResult(found)
                return
            }
            val (chatId, messageId) = keys[index]
            client.send(TdApi.GetMessage(chatId, messageId)) { result ->
                (result as? TdApi.Message)?.let(::mapMessageToMedia)?.let { found[keys[index]] = it }
                next(index + 1)
            }
        }
        next(0)
    }

    /** Downloads a small file (a thumbnail or chat photo) and returns its local path, or null. */
    fun downloadPath(fileId: Int, onResult: (String?) -> Unit) {
        paths[fileId]?.let {
            onResult(it)
            return
        }
        client.send(TdApi.DownloadFile(fileId, 8, 0, 0, true)) { result ->
            val local = (result as? TdApi.File)?.local
            val path = local?.path?.takeIf { local.isDownloadingCompleted && it.isNotBlank() }
            if (path != null) paths[fileId] = path
            onResult(path)
        }
    }
}
