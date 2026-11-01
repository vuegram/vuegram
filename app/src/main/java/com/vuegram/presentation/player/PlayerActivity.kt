package com.vuegram.presentation.player

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.media.MediaCodecList
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.BidiFormatter
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.fragment.app.FragmentActivity
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player as Media3Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vuegram.R
import com.vuegram.core.AppLanguage
import com.vuegram.data.library.ChannelSubtitles
import com.vuegram.data.library.SubtitleFile
import com.vuegram.data.history.WatchHistory
import com.vuegram.data.settings.AppSettings
import com.vuegram.data.stream.TdStreamServer
import com.vuegram.domain.model.MediaItem as Video
import com.vuegram.presentation.ui.Images
import com.vuegram.presentation.ui.Motion
import com.vuegram.presentation.ui.alignWithTitle
import java.io.File
import java.util.Locale

/**
 * The built-in player (Media3 ExoPlayer) for a video streamed by TdStreamServer from 127.0.0.1.
 *
 * Remote, like YouTube on a TV: OK or play/pause pauses and plays; left/right jump 10 s back / 30 s forward (holding
 * keeps adding, the jump happens when the key is let go, a bubble over the bar shows where); 1-9 jump to 10-90 % and 0
 * to the start; up shows the overlay. Down moves into the row of buttons (previous, back 10 s, play/pause, forward 30 s,
 * next, subtitles, audio, quality, speed, sleep timer), and Down again into the channel's next videos; Up or Back comes
 * back out. Menu or Captions open the options directly; Back from the bar leaves.
 *
 * Subtitle files the channel posted next to the video (.srt, .vtt, .ass) are added as subtitle tracks and shown. When
 * Telegram keeps smaller copies of a video, Quality switches between them and the original.
 *
 * Where the viewer stopped is saved every 30 s and on leaving; the end of a video marks it watched and, with Play next
 * on, counts down to the chat's next video ("Up next": OK plays it now, Back stops). When the TV's hardware decoder
 * refuses a file, the player tries the next decoder, then Android's software decoders first; only a file none of them
 * can play is handed to mpv, if installed, at the same position.
 */
// DefaultRenderersFactory's decoder fallback, MediaCodecSelector and the subtitle styling are marked unstable in
// Media3: they may change between Media3 versions, which is accepted here (the version is pinned in libs.versions.toml).
@OptIn(UnstableApi::class)
class PlayerActivity : FragmentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var overlay: View
    private lateinit var progress: ProgressBar
    private lateinit var position: TextView
    private lateinit var duration: TextView
    private lateinit var seekLabel: TextView
    private lateinit var seekLane: View
    private lateinit var barRow: View
    private lateinit var track: View
    private lateinit var knob: View
    private lateinit var playButton: View
    private lateinit var playIcon: ImageView
    private lateinit var nextButton: View
    private lateinit var previousButton: View
    private lateinit var qualityButton: View
    private lateinit var controls: List<View>
    private lateinit var controlLabel: TextView
    private lateinit var moreBox: View
    private lateinit var moreList: RecyclerView
    private val more = MoreAdapter()
    private lateinit var buffering: View
    private lateinit var titleView: TextView
    private lateinit var upNextPanel: View
    private lateinit var upNextTitle: TextView
    private lateinit var upNextCount: TextView

    private val handler = Handler(Looper.getMainLooper())
    /** Where a run of left/right presses will land; null when no jump is waiting. */
    private var seekTarget: Long? = null
    /** Set once the outcome is recorded (end, Back, hand-off to mpv), so onStop does not record it again. */
    private var done = false
    /** The software-decoders-first retry has been used; the next decoder error goes to mpv. */
    private var triedSoftware = false

    /** The stream playing now, and the videos that can follow it (see [Player.upNext]). */
    private var url = ""
    private var title = ""
    private var chatTitle = ""
    private var startAtMs = 0L
    private val queue = ArrayDeque<Video>()
    /** The video playing (null only when started without it), and the ones before it, the nearest first. */
    private var current: Video? = null
    private val previous = ArrayDeque<Video>()
    /** The file playing: the video's own, or one of Telegram's smaller copies of it. */
    private var playingFileId = 0
    /** The height chosen in Quality (0: the original); kept for the next videos too, like the speed. */
    private var preferredHeight = 0
    /** Subtitle files the chat posted next to this video, added to the player as extra subtitle tracks. */
    private var sideSubtitles: List<SubtitleFile> = emptyList()
    /** Bumped by each start of a stream: subtitles found for an earlier one are dropped. */
    private var loadToken = 0
    /** Which video codecs this TV can decode, asked once each. */
    private val decoders = HashMap<String, Boolean>()
    /** Seconds left before "Up next" starts the next video; -1 when no countdown runs. */
    private var countdown = -1
    /** Kept for the next video too: the viewer's speed and sleep timer apply to the whole sitting. */
    private var speed = 1f
    private var sleepAtEnd = false
    private var sleepMinutes = 0

    private val mpvLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        Player.onResult(result)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        playerView = findViewById(R.id.player_view)
        overlay = findViewById(R.id.player_overlay)
        progress = findViewById(R.id.player_progress)
        position = findViewById(R.id.player_position)
        duration = findViewById(R.id.player_duration)
        seekLabel = findViewById(R.id.player_seek)
        seekLane = findViewById(R.id.player_seek_lane)
        barRow = findViewById(R.id.player_bar_row)
        track = findViewById(R.id.player_track)
        knob = findViewById(R.id.player_knob)
        playButton = findViewById(R.id.control_play)
        playIcon = findViewById(R.id.control_play_icon)
        nextButton = findViewById(R.id.control_next)
        previousButton = findViewById(R.id.control_previous)
        qualityButton = findViewById(R.id.control_quality)
        controlLabel = findViewById(R.id.control_label)
        moreBox = findViewById(R.id.player_more)
        moreList = findViewById(R.id.player_more_list)
        buffering = findViewById(R.id.player_buffering)
        titleView = findViewById(R.id.player_title)
        upNextPanel = findViewById(R.id.player_up_next)
        upNextTitle = findViewById(R.id.player_up_next_title)
        upNextCount = findViewById(R.id.player_up_next_count)

        url = intent.getStringExtra(EXTRA_URL).orEmpty()
        title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        chatTitle = intent.getStringExtra(EXTRA_CHAT).orEmpty()
        startAtMs = intent.getLongExtra(EXTRA_RESUME_MS, 0L)
        @Suppress("DEPRECATION", "UNCHECKED_CAST")
        (intent.getSerializableExtra(EXTRA_UP_NEXT) as? ArrayList<Video>)?.let(queue::addAll)
        @Suppress("DEPRECATION", "UNCHECKED_CAST")
        (intent.getSerializableExtra(EXTRA_PREVIOUS) as? ArrayList<Video>)?.let(previous::addAll)
        @Suppress("DEPRECATION")
        current = intent.getSerializableExtra(EXTRA_CURRENT) as? Video
        playingFileId = current?.fileId ?: 0
        titleView.text = title
        findViewById<TextView>(R.id.player_chat).text = BidiFormatter.getInstance().unicodeWrap(chatTitle)
        alignWithTitle(titleView, findViewById(R.id.player_chat))
        styleSubtitles()

        startPlayer(startAtMs, softwareFirst = false)
        setUpControls()

        handler.post(tick)
        handler.postDelayed(checkpoint, CHECKPOINT_MS)
        showOverlay()
        onBackPressedDispatcher.addCallback(this, upNextBack)
        onBackPressedDispatcher.addCallback(this, leaveControls)
    }

    // ---- The buttons and the row of next videos ---------------------------------------------------------------------

    /** Where focus is in the overlay: the progress bar (Left/Right jump), the buttons, or the next videos. */
    private enum class Mode { BAR, BUTTONS, ROW }

    private var mode = Mode.BAR

    /** Back in the buttons or the row of videos goes back to the bar instead of leaving the player. */
    private val leaveControls = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = setMode(Mode.BAR)
    }

    private fun setUpControls() {
        val actions = mapOf(
            R.id.control_previous to { previousVideo() },
            R.id.control_back to { queueSeek(-BACK_MS) },
            R.id.control_play to { player.playWhenReady = !player.playWhenReady },
            R.id.control_forward to { queueSeek(FORWARD_MS) },
            R.id.control_next to { nextVideo() },
            R.id.control_subtitles to { chooseTrack(C.TRACK_TYPE_TEXT) },
            R.id.control_audio to { chooseTrack(C.TRACK_TYPE_AUDIO) },
            R.id.control_quality to { chooseQuality() },
            R.id.control_speed to { chooseSpeed() },
            R.id.control_sleep to { chooseSleep() }
        )
        controls = actions.keys.map { findViewById(it) }
        // White icons that turn black on the focused (white) button; set here, as this isn't an AppCompat screen.
        val iconTint = ContextCompat.getColorStateList(this, R.color.tv_button_text)
        controls.forEach { button ->
            (button as ViewGroup).children.filterIsInstance<ImageView>().forEach { it.imageTintList = iconTint }
        }
        for ((id, action) in actions) {
            val button = findViewById<View>(id)
            button.setOnClickListener { action() }
            button.setOnFocusChangeListener { v, hasFocus ->
                v.animate().scaleX(if (hasFocus) 1.08f else 1f).scaleY(if (hasFocus) 1.08f else 1f).setDuration(Motion.FAST).start()
                if (hasFocus) controlLabel.text = labelOf(id)
            }
        }
        moreList.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        moreList.adapter = more
        refreshQueue()
        setMode(Mode.BAR)
    }

    /** The name under a button, with its current value where it has one ("Speed: 1.5×"). */
    private fun labelOf(id: Int): String = when (id) {
        R.id.control_previous -> listOfNotNull(
            getString(R.string.player_previous), previous.firstOrNull()?.title?.let { BidiFormatter.getInstance().unicodeWrap(it) }
        ).joinToString("  ·  ")
        R.id.control_back -> getString(R.string.player_back_10)
        R.id.control_play -> getString(if (player.playWhenReady) R.string.player_pause else R.string.player_play)
        R.id.control_forward -> getString(R.string.player_forward_30)
        R.id.control_next -> listOfNotNull(
            getString(R.string.player_next), queue.firstOrNull()?.title?.let { BidiFormatter.getInstance().unicodeWrap(it) }
        ).joinToString("  ·  ")
        R.id.control_subtitles -> getString(R.string.player_subtitles_value, currentSubtitle())
        R.id.control_audio -> getString(R.string.player_audio_value, currentAudio())
        R.id.control_quality -> getString(R.string.player_quality_value, qualityLabel(currentQuality()))
        R.id.control_speed -> getString(R.string.player_speed_value, speedLabel(speed))
        R.id.control_sleep -> getString(R.string.player_sleep_value, sleepLabel())
        else -> ""
    }

    private fun setMode(next: Mode) {
        mode = next
        controls.forEach { it.isFocusable = next == Mode.BUTTONS }
        moreBox.visibility = if (next == Mode.ROW) View.VISIBLE else View.GONE
        controlLabel.visibility = if (next == Mode.ROW) View.GONE else View.VISIBLE
        knob.visibility = if (next == Mode.BAR) View.VISIBLE else View.INVISIBLE
        leaveControls.isEnabled = next != Mode.BAR
        when (next) {
            Mode.BAR -> {
                currentFocus?.clearFocus()
                controlLabel.setText(R.string.player_options_hint)
            }
            Mode.BUTTONS -> playButton.requestFocus()
            Mode.ROW -> moreList.post { (moreList.findViewHolderForAdapterPosition(0)?.itemView ?: moreList).requestFocus() }
        }
        if (overlay.visibility == View.VISIBLE || next != Mode.BAR) showOverlay()
    }

    /** The next video now (the button, the remote's Next key, or a card in the row). */
    private fun nextVideo() {
        if (queue.isEmpty()) return
        WatchHistory.finishPending(player.currentPosition, player.duration.takeIf { it > 0 })
        playNext()
    }

    /** The row's card at [index]: skip the videos before it and play it. */
    private fun jumpTo(index: Int) {
        if (index >= queue.size) return
        WatchHistory.finishPending(player.currentPosition, player.duration.takeIf { it > 0 })
        setMode(Mode.BAR)
        clearWaiting()
        // This video and the ones skipped go to Previous, the nearest (the last skipped) first.
        current?.let(previous::addFirst)
        repeat(index) { queue.removeFirstOrNull()?.let(previous::addFirst) }
        queue.removeFirstOrNull()?.let(::start)
    }

    /** The Next and Previous buttons and the row follow the queue; Quality shows when the video has other sizes. */
    private fun refreshQueue() {
        nextButton.visibility = if (queue.isEmpty()) View.GONE else View.VISIBLE
        previousButton.visibility = if (previous.isEmpty()) View.GONE else View.VISIBLE
        qualityButton.visibility = if (current?.let(::qualityChoices).isNullOrEmpty()) View.GONE else View.VISIBLE
        more.items = queue.toList()
        // A focused button that just went (the last Previous): focus back on Play rather than lost.
        if (mode == Mode.BUTTONS && controls.none { it.hasFocus() }) playButton.requestFocus()
    }

    /** Small 16:9 cards of the videos that follow, in the row under the buttons. */
    private inner class MoreAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        var items: List<Video> = emptyList()
            @SuppressLint("NotifyDataSetChanged")
            set(value) {
                field = value
                notifyDataSetChanged()
            }

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view = layoutInflater.inflate(R.layout.item_video, parent, false)
            val width = (CARD_DP * resources.displayMetrics.density).toInt()
            view.layoutParams = RecyclerView.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            val frame = view.findViewById<View>(R.id.frame)
            frame.layoutParams.height = (width - view.paddingStart - view.paddingEnd) * 9 / 16
            frame.clipToOutline = true
            return object : RecyclerView.ViewHolder(view) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = items[position]
            val view = holder.itemView
            val frame = view.findViewById<View>(R.id.frame)
            val name = view.findViewById<TextView>(R.id.name)
            name.text = item.title
            val pill = view.findViewById<TextView>(R.id.duration)
            pill.text = com.vuegram.presentation.chat.VideoCardAdapter.formatDuration(item.durationSeconds)
            pill.visibility = if (item.durationSeconds > 0) View.VISIBLE else View.GONE
            Images.load(view.findViewById(R.id.thumb), item.thumbnailFileId, null)
            view.setOnClickListener {
                val at = holder.bindingAdapterPosition
                if (at != RecyclerView.NO_POSITION) jumpTo(at)
            }
            view.setOnFocusChangeListener { _, hasFocus ->
                frame.foreground = if (hasFocus) ContextCompat.getDrawable(this@PlayerActivity, R.drawable.tv_card_focus_ring) else null
                frame.animate().scaleX(if (hasFocus) 1.06f else 1f).scaleY(if (hasFocus) 1.06f else 1f).setDuration(Motion.FAST).start()
                name.setTextColor(ContextCompat.getColor(this@PlayerActivity, if (hasFocus) R.color.tv_text else R.color.tv_text_dim))
            }
        }
    }

    /** Back during the "Up next" countdown stops it (and closes the player); otherwise Back leaves as usual. */
    private val upNextBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = cancelUpNext()
    }

    /** Subtitles big enough to read from the sofa: white on a soft dark box, a fifth larger than Media3's default. */
    private fun styleSubtitles() {
        playerView.subtitleView?.apply {
            setStyle(CaptionStyleCompat(
                0xFFFFFFFF.toInt(), 0x99000000.toInt(), 0x00000000, CaptionStyleCompat.EDGE_TYPE_NONE, 0xFF000000.toInt(), null
            ))
            setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * 1.2f)
        }
    }

    /**
     * A buffer sized for 1 GB TVs. Media3's default keeps 50 seconds, up to about 130 MB, which a high-bitrate video
     * fills; the stream comes from this TV (TDLib downloads it), so 15 to 30 seconds, at most 32 MB, is plenty.
     */
    private fun smallBuffer() = DefaultLoadControl.Builder()
        .setBufferDurationsMs(15_000, 30_000, 1_500, 3_000)
        .setTargetBufferBytes(32 * 1024 * 1024)
        .setPrioritizeTimeOverSizeThresholds(false)
        .build()

    /**
     * Builds the player and starts the video at [atMs]. Decoder fallback lets Media3 move on to the next decoder when
     * one fails to start; [softwareFirst] puts Android's software decoders ahead of the TV's hardware ones, which
     * plays files the hardware decoder rejects (an unusual profile or level), at the cost of more CPU.
     */
    private fun startPlayer(atMs: Long, softwareFirst: Boolean) {
        val renderers = DefaultRenderersFactory(this).setEnableDecoderFallback(true)
        if (softwareFirst) {
            renderers.setMediaCodecSelector { mimeType, secure, tunneling ->
                MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, secure, tunneling).sortedByDescending { it.softwareOnly }
            }
        }
        player = ExoPlayer.Builder(this, renderers).setLoadControl(smallBuffer()).build()
        playerView.player = player
        player.addListener(object : Media3Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering.visibility = if (state == Media3Player.STATE_BUFFERING) View.VISIBLE else View.GONE
                if (state == Media3Player.STATE_ENDED) onEnded()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playIcon.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
                playButton.contentDescription = getString(if (isPlaying) R.string.player_pause else R.string.player_play)
                if (playButton.hasFocus()) controlLabel.text = labelOf(R.id.control_play)
                // The screen stays on while a video plays; paused, the TV's own screensaver may start.
                if (isPlaying) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                // Paused: keep the overlay up, as on any TV player.
                if (!isPlaying) showOverlay(autoHide = false) else showOverlay()
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.w(TAG, "ExoPlayer cannot play this video: ${error.errorCodeName}", error)
                when {
                    dropBrokenSubtitles(error) -> Unit
                    // The stream itself failed (network, or Telegram slowing down): mpv would fail the same way.
                    isStreamError(error) -> showStreamError()
                    isDecoderError(error) && !triedSoftware -> retryInSoftware()
                    else -> handToMpv()
                }
            }
        })
        player.setPlaybackSpeed(speed)
        load(atMs)
    }

    /**
     * Starts the stream at [atMs] at once, so the player never holds the previous video while the history already
     * points at this one. Subtitle files posted next to the video are looked for meanwhile (unless subtitles are off)
     * and, when found, added where the video is by then.
     */
    private fun load(atMs: Long) {
        val token = ++loadToken
        sideSubtitles = emptyList()
        player.setMediaItem(mediaItem())
        if (atMs > 0) player.seekTo(atMs)
        player.playWhenReady = true
        player.prepare()
        val video = current ?: return
        if (C.TRACK_TYPE_TEXT in player.trackSelectionParameters.disabledTrackTypes) return
        ChannelSubtitles.find(this, video.chatId, video.messageId) { files ->
            runOnUiThread {
                // Not for a later video, nor after the end, a hand-off to mpv or leaving.
                if (token != loadToken || done || isFinishing || isDestroyed || files.isEmpty()) return@runOnUiThread
                sideSubtitles = files
                player.setMediaItem(mediaItem(), player.currentPosition)
                player.prepare()
                Toast.makeText(this, R.string.player_channel_subtitles, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * A subtitle file from the chat the player can't read (a renamed or broken file posted by anyone): play on without
     * the chat's subtitles, from the same spot, instead of giving up on the video. True when that is what happened.
     */
    private fun dropBrokenSubtitles(error: PlaybackException): Boolean {
        if (sideSubtitles.isEmpty() || error.errorCode !in PARSING_ERRORS) return false
        Log.w(TAG, "playing on without the chat's subtitle files")
        sideSubtitles = emptyList()
        player.setMediaItem(mediaItem(), player.currentPosition)
        player.prepare()
        player.playWhenReady = true
        return true
    }

    /** The stream, with the chat's subtitle files as extra tracks; the first one shows unless subtitles are off. */
    private fun mediaItem(): MediaItem = MediaItem.Builder()
        .setUri(url)
        .setSubtitleConfigurations(sideSubtitles.mapIndexed { index, file ->
            MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(File(file.path)))
                .setMimeType(file.mimeType)
                .setLabel(file.name)
                .setSelectionFlags(if (index == 0) C.SELECTION_FLAG_DEFAULT else 0)
                .build()
        })
        .build()

    /** The end: it counts as watched; then "Up next", unless the sleep timer said "end of this video". */
    private fun onEnded() {
        if (done) return
        done = true
        // Back is for the countdown now, not for leaving the buttons.
        setMode(Mode.BAR)
        WatchHistory.finishPending(null, player.duration.takeIf { it > 0 })
        val next = queue.firstOrNull()
        if (next == null || sleepAtEnd || !AppSettings.playNext) {
            finish()
            return
        }
        upNextTitle.text = next.title
        upNextPanel.visibility = View.VISIBLE
        overlay.visibility = View.GONE
        countdown = UP_NEXT_SECONDS
        upNextBack.isEnabled = true
        handler.post(countdownTick)
    }

    private val countdownTick = object : Runnable {
        override fun run() {
            if (countdown < 0) return
            if (countdown == 0) {
                playNext()
                return
            }
            upNextCount.text = resources.getQuantityString(R.plurals.player_up_next_in, countdown, countdown)
            countdown--
            handler.postDelayed(this, 1_000)
        }
    }

    /** The next video in this same player; the one ending becomes Previous's first. */
    private fun playNext() {
        clearWaiting()
        val next = queue.removeFirstOrNull() ?: return finish()
        current?.let(previous::addFirst)
        start(next)
    }

    /**
     * Previous (the button, or the remote's Previous key): back to this video's start once it has played a while, as any
     * player does; near its start, the video before it, and this one goes back to the front of Up next.
     */
    private fun previousVideo() {
        val before = previous.firstOrNull()
        if (before == null || player.currentPosition > RESTART_MS) {
            player.seekTo(0)
            showOverlay()
            return
        }
        WatchHistory.finishPending(player.currentPosition, player.duration.takeIf { it > 0 })
        clearWaiting()
        previous.removeFirst()
        current?.let(queue::addFirst)
        start(before)
    }

    /** What belongs to the video being left: the "Up next" countdown, and a jump still waiting. */
    private fun clearWaiting() {
        countdown = -1
        upNextBack.isEnabled = false
        handler.removeCallbacks(countdownTick)
        handler.removeCallbacks(commitSeek)
        seekTarget = null
        seekLabel.visibility = View.INVISIBLE
        upNextPanel.visibility = View.GONE
    }

    /**
     * Starts [video] in this same player: a new stream (in the quality chosen earlier, when the video has it), its own
     * resume point and history entry.
     */
    private fun start(video: Video) {
        val quality = if (preferredHeight > 0) {
            qualityChoices(video).filter { !it.original && it.height <= preferredHeight }.maxByOrNull { it.height }
        } else {
            null
        }
        val streamed = quality?.let { video.copy(fileId = it.fileId) } ?: video
        val (nextUrl, resumeMs) = Player.startStream(this, streamed, chatTitle) ?: return finish()
        current = video
        playingFileId = streamed.fileId ?: 0
        url = nextUrl
        title = video.title
        startAtMs = resumeMs
        titleView.text = title
        alignWithTitle(titleView, findViewById(R.id.player_chat))
        done = false
        triedSoftware = false
        refreshQueue()
        load(resumeMs)
        showOverlay()
    }

    private fun cancelUpNext() {
        countdown = -1
        upNextBack.isEnabled = false
        handler.removeCallbacks(countdownTick)
        finish()
    }

    /** Input/output errors (codes 2000-2999): the stream from TdStreamServer broke, not the file's format. */
    private fun isStreamError(error: PlaybackException) = error.errorCode in 2000..2999

    /** Says the video stopped loading, and offers Try again (from the same spot) or Close. Never retries by itself. */
    private fun showStreamError() {
        if (done || isFinishing || isDestroyed) return
        AlertDialog.Builder(this, DIALOG)
            .setTitle(R.string.player_stream_error_title)
            .setMessage(R.string.error_network)
            .setCancelable(false)
            .setPositiveButton(R.string.action_retry) { _, _ ->
                player.seekTo(player.currentPosition)
                player.prepare()
                player.playWhenReady = true
            }
            .setNegativeButton(R.string.close_player) { _, _ -> finish() }
            .show()
    }

    /** Plays the video again from where it stopped, with the software decoders first. */
    private fun retryInSoftware() {
        triedSoftware = true
        val at = player.currentPosition.takeIf { it > 0 } ?: startAtMs
        Log.i(TAG, "retrying with software decoders at ${at}ms")
        player.release()
        startPlayer(at, softwareFirst = true)
    }

    private fun isDecoderError(error: PlaybackException) = error.errorCode in DECODER_ERRORS

    // ---- Options: subtitles, audio, speed, sleep timer --------------------------------------------------------------

    private fun showOptions() {
        if (isFinishing || isDestroyed) return
        val options = listOfNotNull(
            getString(R.string.player_subtitles_value, currentSubtitle()) to { chooseTrack(C.TRACK_TYPE_TEXT) },
            getString(R.string.player_audio_value, currentAudio()) to { chooseTrack(C.TRACK_TYPE_AUDIO) },
            (getString(R.string.player_quality_value, qualityLabel(currentQuality())) to { chooseQuality() })
                .takeIf { qualityButton.visibility == View.VISIBLE },
            getString(R.string.player_speed_value, speedLabel(speed)) to { chooseSpeed() },
            getString(R.string.player_sleep_value, sleepLabel()) to { chooseSleep() }
        )
        AlertDialog.Builder(this, DIALOG)
            .setTitle(R.string.player_options)
            .setItems(options.map { it.first }.toTypedArray()) { _, which -> options.getOrNull(which)?.second?.invoke() }
            .show()
    }

    // ---- Quality: the original file or one of Telegram's smaller copies ---------------------------------------------

    /** [height] 0 when unknown; [original] is the file as it was posted. */
    private data class Quality(val height: Int, val fileId: Int, val original: Boolean)

    /**
     * The original first, then Telegram's smaller copies this TV can decode, one per height (H.264 when there is a
     * choice), tallest first. Empty when there is nothing to choose between.
     */
    private fun qualityChoices(video: Video): List<Quality> {
        val own = video.fileId ?: return emptyList()
        val copies = video.qualities
            .filter { it.fileId != own && it.height > 0 && (video.videoHeight == 0 || it.height < video.videoHeight) && canDecode(it.codec) }
            .groupBy { it.height }
            .map { (height, same) -> Quality(height, (same.firstOrNull { isH264(it.codec) } ?: same.first()).fileId, original = false) }
            .sortedByDescending { it.height }
        return if (copies.isEmpty()) emptyList() else listOf(Quality(video.videoHeight, own, original = true)) + copies
    }

    private fun currentQuality(): Quality? = current?.let(::qualityChoices)?.firstOrNull { it.fileId == playingFileId }

    /** "Original · 1080p", "720p". */
    private fun qualityLabel(quality: Quality?): String {
        val original = getString(R.string.player_quality_original)
        return when {
            quality == null -> original
            quality.original -> if (quality.height > 0) "$original  ·  ${quality.height}p" else original
            else -> "${quality.height}p"
        }
    }

    private fun chooseQuality() {
        val video = current ?: return
        val choices = qualityChoices(video)
        if (choices.size < 2) return
        AlertDialog.Builder(this, DIALOG)
            .setTitle(R.string.player_quality)
            .setSingleChoiceItems(choices.map(::qualityLabel).toTypedArray(), choices.indexOfFirst { it.fileId == playingFileId }) { dialog, which ->
                dialog.dismiss()
                val chosen = choices[which]
                preferredHeight = if (chosen.original) 0 else chosen.height
                if (chosen.fileId != playingFileId) switchFile(chosen.fileId)
            }
            .show()
    }

    /** The same video from another file, from where it is now (a jump still waiting lands there). */
    private fun switchFile(fileId: Int) {
        val at = seekTarget ?: player.currentPosition
        handler.removeCallbacks(commitSeek)
        seekTarget = null
        seekLabel.visibility = View.INVISIBLE
        playingFileId = fileId
        url = TdStreamServer.start(this, fileId)
        player.setMediaItem(mediaItem(), at)
        player.prepare()
        player.playWhenReady = true
    }

    private fun isH264(codec: String) = codec.lowercase().let { it.startsWith("h264") || it.startsWith("avc") }

    /** True when the TV has a decoder for [codec]; an unknown codec name is let through (the player falls back anyway). */
    private fun canDecode(codec: String): Boolean {
        val name = codec.lowercase()
        val mime = when {
            isH264(name) -> MimeTypes.VIDEO_H264
            name.startsWith("h265") || name.startsWith("hevc") || name.startsWith("hvc") -> MimeTypes.VIDEO_H265
            name.startsWith("av1") || name.startsWith("av01") -> MimeTypes.VIDEO_AV1
            name.startsWith("vp9") || name.startsWith("vp09") -> MimeTypes.VIDEO_VP9
            else -> return true
        }
        return decoders.getOrPut(mime) {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
                !info.isEncoder && info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
            }
        }
    }

    private fun currentSubtitle() = selectedTrack(C.TRACK_TYPE_TEXT)?.let(::trackName) ?: getString(R.string.player_off)

    private fun currentAudio() = selectedTrack(C.TRACK_TYPE_AUDIO)?.let(::trackName) ?: "—"

    private data class Track(val group: Tracks.Group, val index: Int)

    private fun tracks(type: Int): List<Track> = player.currentTracks.groups
        .filter { it.type == type }
        .flatMap { group -> (0 until group.length).filter { group.isTrackSupported(it) }.map { Track(group, it) } }

    private fun selectedTrack(type: Int): Track? = tracks(type).firstOrNull { it.group.isTrackSelected(it.index) }

    /** A track's label, its language in the viewer's language, or "Track 2". */
    private fun trackName(track: Track): String {
        val format = track.group.getTrackFormat(track.index)
        format.label?.takeIf { it.isNotBlank() }?.let { return it }
        format.language?.takeIf { it.isNotBlank() && it != "und" }?.let { code ->
            return Locale.forLanguageTag(code).let { it.getDisplayName(Locale.getDefault()).ifBlank { code } }
        }
        return getString(R.string.player_track_number, tracks(track.group.type).indexOf(track) + 1)
    }

    /** Subtitles: Off, then each subtitle track in the file. Audio: each audio track (no Off). */
    private fun chooseTrack(type: Int) {
        val available = tracks(type)
        if (available.isEmpty()) {
            Toast.makeText(this, if (type == C.TRACK_TYPE_TEXT) R.string.player_no_subtitles else R.string.player_no_audio_tracks, Toast.LENGTH_SHORT).show()
            return
        }
        val withOff = type == C.TRACK_TYPE_TEXT
        val names = (if (withOff) listOf(getString(R.string.player_off)) else emptyList()) + available.map(::trackName)
        val selected = available.indexOfFirst { it.group.isTrackSelected(it.index) }
        val checked = when {
            selected >= 0 -> selected + (if (withOff) 1 else 0)
            withOff -> 0
            else -> -1
        }
        AlertDialog.Builder(this, DIALOG)
            .setTitle(if (withOff) R.string.player_subtitles else R.string.player_audio)
            .setSingleChoiceItems(names.toTypedArray(), checked) { dialog, which ->
                dialog.dismiss()
                val builder = player.trackSelectionParameters.buildUpon()
                if (withOff && which == 0) {
                    builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                } else {
                    val track = available[which - (if (withOff) 1 else 0)]
                    builder.setTrackTypeDisabled(type, false)
                        .setOverrideForType(TrackSelectionOverride(track.group.mediaTrackGroup, track.index))
                }
                player.trackSelectionParameters = builder.build()
            }
            .show()
    }

    private fun chooseSpeed() {
        val names = SPEEDS.map(::speedLabel).toTypedArray()
        AlertDialog.Builder(this, DIALOG)
            .setTitle(R.string.player_speed)
            .setSingleChoiceItems(names, SPEEDS.indexOf(speed).coerceAtLeast(0)) { dialog, which ->
                dialog.dismiss()
                speed = SPEEDS[which]
                player.setPlaybackSpeed(speed)
            }
            .show()
    }

    private fun speedLabel(value: Float) =
        if (value == 1f) getString(R.string.player_speed_normal) else String.format(Locale.US, "%s×", value.toString().removeSuffix(".0"))

    /** Off, 15/30/60/90 minutes, or the end of this video. When it fires, the video pauses and the player closes. */
    private fun chooseSleep() {
        val minutes = listOf(0, 15, 30, 60, 90)
        val names = minutes.map { if (it == 0) getString(R.string.player_off) else resources.getQuantityString(R.plurals.player_minutes, it, it) } +
            getString(R.string.player_sleep_end)
        val checked = when {
            sleepAtEnd -> names.lastIndex
            else -> minutes.indexOf(sleepMinutes).coerceAtLeast(0)
        }
        AlertDialog.Builder(this, DIALOG)
            .setTitle(R.string.player_sleep)
            .setSingleChoiceItems(names.toTypedArray(), checked) { dialog, which ->
                dialog.dismiss()
                handler.removeCallbacks(sleep)
                sleepAtEnd = which == names.lastIndex
                sleepMinutes = if (sleepAtEnd) 0 else minutes[which]
                if (sleepMinutes > 0) handler.postDelayed(sleep, sleepMinutes * 60_000L)
            }
            .show()
    }

    private fun sleepLabel(): String = when {
        sleepAtEnd -> getString(R.string.player_sleep_end)
        sleepMinutes > 0 -> resources.getQuantityString(R.plurals.player_minutes, sleepMinutes, sleepMinutes)
        else -> getString(R.string.player_off)
    }

    /** Sleep timer: pause, remember the position (onStop records it), and close. */
    private val sleep = Runnable {
        player.pause()
        finish()
    }

    // ---- Overlay and keys -------------------------------------------------------------------------------------------

    /** Refreshes the times and the bar twice a second while the overlay shows. */
    private val tick = object : Runnable {
        override fun run() {
            if (overlay.visibility == View.VISIBLE) updateProgress()
            handler.postDelayed(this, 500)
        }
    }

    private val checkpoint = object : Runnable {
        override fun run() {
            val length = player.duration
            if (!done && length > 0 && player.currentPosition > 0) WatchHistory.checkpoint(player.currentPosition, length)
            handler.postDelayed(this, CHECKPOINT_MS)
        }
    }

    /** The overlay goes after a few quiet seconds while playing; from the buttons or the row, back to the bar first. */
    private val hideOverlay = Runnable {
        if (!player.isPlaying || seekTarget != null) return@Runnable
        if (mode != Mode.BAR) setMode(Mode.BAR)
        overlay.visibility = View.GONE
    }

    private val commitSeek = Runnable {
        seekTarget?.let { player.seekTo(it) }
        seekTarget = null
        seekLabel.visibility = View.INVISIBLE
        showOverlay()
    }

    private fun showOverlay(autoHide: Boolean = true) {
        if (upNextPanel.visibility == View.VISIBLE) return
        overlay.visibility = View.VISIBLE
        updateProgress()
        handler.removeCallbacks(hideOverlay)
        if (autoHide) handler.postDelayed(hideOverlay, if (mode == Mode.BAR) OVERLAY_MS else CONTROLS_IDLE_MS)
    }

    private fun updateProgress() {
        val length = player.duration.takeIf { it > 0 } ?: 0L
        val shown = seekTarget ?: player.currentPosition
        // Only real changes: setting the same text still asks the whole overlay to lay out again.
        Player.formatClock(shown).let { if (position.text != it) position.text = it }
        (if (length > 0) Player.formatClock(length) else "").let { if (duration.text != it) duration.text = it }
        if (length > 0) {
            progress.progress = (shown * 1000 / length).toInt()
            progress.secondaryProgress = (player.bufferedPosition * 1000 / length).toInt()
            knob.translationX = (track.width - knob.width) * (shown.toFloat() / length)
        }
    }

    /** A left/right press: move the waiting jump, show where it lands, and jump once the keys go quiet. */
    private fun queueSeek(deltaMs: Long) {
        val length = player.duration.takeIf { it > 0 } ?: return
        val from = seekTarget ?: player.currentPosition
        seekTo((from + deltaMs).coerceIn(0L, (length - 1_000).coerceAtLeast(0)))
    }

    /** Shows where a jump lands ("+1:30  ·  12:34") and makes it once the keys go quiet. */
    private fun seekTo(target: Long) {
        seekTarget = target
        val offset = target - player.currentPosition
        val sign = if (offset >= 0) "+" else "−"
        seekLabel.text = getString(R.string.player_seek_label, sign + Player.formatClock(kotlin.math.abs(offset)), Player.formatClock(target))
        seekLabel.visibility = View.VISIBLE
        showOverlay(autoHide = false)
        // The bubble over the place on the bar where the jump lands.
        val length = player.duration.takeIf { it > 0 } ?: 0L
        if (length > 0) seekLabel.post {
            val at = barRow.left + track.left - seekLane.left + track.width * (target.toFloat() / length)
            seekLabel.translationX = (at - seekLabel.width / 2f).coerceIn(0f, (seekLane.width - seekLabel.width).toFloat().coerceAtLeast(0f))
        }
        handler.removeCallbacks(commitSeek)
        handler.postDelayed(commitSeek, SEEK_COMMIT_MS)
    }

    // Lint's RestrictedApi check misfires on super.dispatchKeyEvent from a ComponentActivity subclass; it is public API.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)
        // "Up next" is counting down: OK plays the next video now; Back (upNextBack) stops; other keys do nothing.
        if (countdown >= 0) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_NEXT -> {
                    playNext()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_STOP -> {
                    cancelUpNext()
                    return true
                }
            }
            return super.dispatchKeyEvent(event)
        }
        // Media keys do the same wherever focus is.
        when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_SPACE -> {
                if (event.repeatCount == 0) player.playWhenReady = !player.playWhenReady
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> { player.play(); return true }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> { player.pause(); return true }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                if (event.repeatCount == 0) nextVideo()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                if (event.repeatCount == 0) previousVideo()
                return true
            }
        }
        // In the buttons or the row: Up and Down move between them and the bar; the rest is ordinary focus and OK.
        if (mode != Mode.BAR) {
            showOverlay()
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (event.repeatCount == 0) setMode(if (mode == Mode.ROW) Mode.BUTTONS else Mode.BAR)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (event.repeatCount == 0 && mode == Mode.BUTTONS && queue.isNotEmpty()) setMode(Mode.ROW)
                    return true
                }
            }
            return super.dispatchKeyEvent(event)
        }
        // Holding a key repeats it about 20 times a second; take every third repeat so holding stays controllable.
        val counts = event.repeatCount == 0 || event.repeatCount % 3 == 0
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (event.repeatCount == 0) player.playWhenReady = !player.playWhenReady
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                if (counts) queueSeek(-BACK_MS)
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                if (counts) queueSeek(FORWARD_MS)
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                // The first Down shows the overlay; with the overlay up, Down moves into the buttons.
                if (event.repeatCount == 0 && overlay.visibility == View.VISIBLE) setMode(Mode.BUTTONS) else showOverlay()
                return true
            }
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS -> {
                if (event.repeatCount == 0) showOptions()
                return true
            }
            KeyEvent.KEYCODE_CAPTIONS -> {
                if (event.repeatCount == 0) chooseTrack(C.TRACK_TYPE_TEXT)
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_INFO -> {
                showOverlay()
                return true
            }
        }
        // Number keys: 1-9 jump to 10-90 % of the video, 0 to the start.
        if (event.keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 && event.repeatCount == 0) {
            val length = player.duration.takeIf { it > 0 } ?: return true
            seekTo(length * (event.keyCode - KeyEvent.KEYCODE_0) / 10)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    /** ExoPlayer cannot decode this file: play it in mpv from here, or say so when mpv is not installed. */
    private fun handToMpv() {
        if (done) return
        val at = player.currentPosition.takeIf { it > 0 } ?: startAtMs
        val mpv = Player.mpvIntent(this, url, title, at)
        if (mpv == null) {
            Toast.makeText(this, R.string.player_cannot_play, Toast.LENGTH_LONG).show()
            done = true
            WatchHistory.clearPending()
            finish()
            return
        }
        done = true
        player.stop()
        Toast.makeText(this, R.string.player_opening_mpv, Toast.LENGTH_SHORT).show()
        mpvLauncher.launch(mpv)
    }

    /** Leaving the player (Back, Home, another app, the sleep timer): remember where it stopped, then close it. */
    override fun onStop() {
        super.onStop()
        if (!done) {
            done = true
            val length = player.duration.takeIf { it > 0 }
            WatchHistory.finishPending(player.currentPosition, length)
            finish()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player.release()
        // Home or standby closes the player without VueGram's main screen coming back: stop the stream (and its
        // foreground service) here, unless another video has started streaming since.
        if (isFinishing) {
            TdStreamServer.release(this, onlyFor = url)
            ChannelSubtitles.clear(this)
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "VueGram"
        private const val DIALOG = android.R.style.Theme_DeviceDefault_Dialog_Alert
        private const val EXTRA_URL = "url"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_CHAT = "chat"
        private const val EXTRA_RESUME_MS = "resume"
        private const val EXTRA_UP_NEXT = "up_next"
        private const val EXTRA_CURRENT = "current"
        private const val EXTRA_PREVIOUS = "previous"
        /** Previous after this much of a video goes back to its start instead of to the video before it. */
        private const val RESTART_MS = 5_000L
        /** Media3's "could not parse" errors (container or subtitle, malformed or unsupported). */
        private val PARSING_ERRORS = setOf(
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED
        )
        private const val BACK_MS = 10_000L
        private const val FORWARD_MS = 30_000L
        private const val SEEK_COMMIT_MS = 700L
        private const val UP_NEXT_SECONDS = 10
        private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
        /** Errors where another decoder may succeed; anything else (network, source) goes straight to mpv. */
        private val DECODER_ERRORS = setOf(
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
        )
        private const val OVERLAY_MS = 4_000L
        /** In the buttons or the row of videos the overlay waits longer before it goes. */
        private const val CONTROLS_IDLE_MS = 8_000L
        /** The width of a card in the row of next videos. */
        private const val CARD_DP = 236
        private const val CHECKPOINT_MS = 30_000L

        fun intent(
            context: Context,
            url: String,
            video: Video,
            chatTitle: String,
            resumeMs: Long,
            upNext: List<Video> = emptyList(),
            previous: List<Video> = emptyList()
        ): Intent =
            Intent(context, PlayerActivity::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_TITLE, video.title)
                .putExtra(EXTRA_CHAT, chatTitle)
                .putExtra(EXTRA_RESUME_MS, resumeMs)
                .putExtra(EXTRA_CURRENT, video)
                .putExtra(EXTRA_UP_NEXT, ArrayList(upNext))
                .putExtra(EXTRA_PREVIOUS, ArrayList(previous))
    }
}
