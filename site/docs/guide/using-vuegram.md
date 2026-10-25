# Using VueGram

VueGram has two screens: the **library**, with all your groups and channels, and a **chat**, with that chat's
videos. Everything works with the TV remote's arrows, **OK** and **Back**.

::: tip Hold OK
Almost everything has a menu: hold <kbd>OK</kbd> on a chat or a video to see what you can do with it.
:::

## The library

The library is the first screen, titled **Your channels**. From top to bottom:

| Row | What it shows |
|---|---|
| **Continue watching** | The videos you started most recently and haven't finished, with how many minutes are left. Press <kbd>OK</kbd> to carry on where you stopped. |
| **New in your channels** | The newest videos you haven't started yet, from your first chats (pinned ones first). |
| **Pinned** | The chats you pinned, so your favourites are always at the top. |
| **Channels & groups** | Every group and channel you follow, as a grid of their photos. |

By default the library lists only **groups and channels**: no private chats and no bots. (A
[setting](./settings#show-people) can add your private chats with people.)

### Your Telegram folders

If you sorted your chats into folders in Telegram (Work, News, Series…), the library shows them as tabs above the
grid, with **All chats** first. Choose a tab to see only that folder's chats; **Continue watching** stays on top.
Folders you add or change in Telegram show up by themselves.

### Pin, hide and remove

Hold <kbd>OK</kbd> on a chat:

- **Pin to top** puts it in the Pinned row. **Unpin** takes it out again.
- **Hide from VueGram** takes it out of the library, for chats you follow but never watch on the TV. It only hides
  the chat in VueGram on this TV; nothing changes in Telegram.

Hold <kbd>OK</kbd> on a video in Continue watching to **Mark as watched** or **Remove from Continue watching**.

### Hidden channels

Once you have hidden a chat, a **Hidden channels** card appears in the library. Press <kbd>OK</kbd> on it to show
or hide your hidden chats. While they are shown, hold <kbd>OK</kbd> on one and choose **Show in VueGram again** to
unhide it.

### Leaving

Press <kbd>Back</kbd> twice in the library to leave VueGram ("Press Back again to exit").

## A chat's videos

Press <kbd>OK</kbd> on a chat to open it. You see the chat's videos and video files as a grid of 16:9 cards, newest
first. Each card shows the thumbnail, the length, the date and the file size. More videos load as you move down.

Videos you have started show where you stopped, or how many minutes are left; videos you finished carry a
**✓ Watched** mark.

Hold <kbd>OK</kbd> on a video for more:

- **Play from the beginning** ignores where you stopped last time.
- **Mark as watched** or **Mark as not watched**.
- **Play in mpv** opens it in mpv-android, if you have it installed. See [the mpv fallback](#the-mpv-fallback).

::: info
With the [Show messages](./settings#show-messages) setting on, a chat shows all its messages instead of only its
videos.
:::

## Searching

### Search one chat

At the top of a chat there is a **Search this channel** field. Type a word and VueGram finds the chat's videos that
match.

### Search all channels

At the top of the library there is a **Search all channels** field. It uses Telegram's own search across every
group and channel you follow at once, and shows the matching videos in a grid you can play from, just like a chat.

### Search by voice

Next to each search field there is a microphone button: choose it and say what you're looking for. The remote's
**Search** button does the same, if it has one. Voice search uses your TV's own speech recognition (on Google TV,
Google's), so the button only appears where the TV has it.

## The player

Press <kbd>OK</kbd> on a video and it starts playing in VueGram's built-in player, streamed straight from Telegram.
If you watched part of it before, it picks up where you stopped and says **Resuming at** the time.

| Remote key | What it does |
|---|---|
| <kbd>OK</kbd> | Pause, and play again |
| <kbd>◀</kbd> Left | Jump **10 seconds back** |
| <kbd>▶</kbd> Right | Jump **30 seconds forward** |
| Hold <kbd>◀</kbd> or <kbd>▶</kbd> | Keep adding: each repeat adds another 10 or 30 seconds. The screen shows how far you will jump and where you will land, and the jump happens a moment after you let go. |
| <kbd>1</kbd> … <kbd>9</kbd>, <kbd>0</kbd> | Jump to 10 % … 90 % of the video; <kbd>0</kbd> goes back to the start |
| <kbd>▲</kbd> Up | Show the progress bar |
| <kbd>▼</kbd> Down | Show the progress bar; press again for the **options** |
| <kbd>Back</kbd> | Leave the player |

Play/pause, rewind, fast-forward and next buttons on the remote work too, and so do **Menu** (the options) and
**Captions** (subtitles), where the remote has them.

### Options: subtitles, audio, speed, sleep timer

- **Subtitles:** off, or any subtitle track in the video file, in large letters you can read from the sofa.
- **Audio:** choose another audio track (another language, a commentary), when the file has more than one.
- **Speed:** from 0.5× to 2×.
- **Sleep timer:** 15, 30, 60 or 90 minutes, or **End of this video**. When it runs out, the video stops and VueGram
  remembers where.

Speed and the sleep timer carry on to the next video.

### Up next

When a video ends, VueGram counts down 10 seconds to the chat's next video (the one posted after it, the way episodes
are posted): **OK** plays it now, **Back** stops. You can turn this off in [Settings](./settings#play-next).

### Resume and watched marks

- VueGram remembers where you stopped: while you watch, and when you leave the player.
- If you stop within the first 30 seconds, the video counts as not started.
- If you get through about 92% of it, or to the end, it counts as **watched** and gets the ✓ Watched mark.
- You can always change this by hand: hold <kbd>OK</kbd> on the video and choose **Mark as watched** or **Mark as
  not watched**.

All of this is stored on the TV only. See [Privacy & security](./privacy).

### When a video won't play

Some TVs' hardware video decoders refuse certain files. When that happens, VueGram doesn't give up: it tries
another decoder, then Android's software decoders.

### The mpv fallback

For the rare file that none of those can play, VueGram hands the video to
[mpv-android](https://github.com/mpv-android/mpv-android/releases), a free video player, if it is installed. The
video opens in mpv **at the same position**, and when you press <kbd>Back</kbd> in mpv, VueGram remembers where
you stopped there too.

If mpv is not installed, VueGram tells you that the video's format cannot be played here. Installing mpv-android
once is enough; you can also pick it yourself any time with hold <kbd>OK</kbd> › **Play in mpv**.

### Streams, not downloads

The parts of a video you watch are only kept while you watch. They are deleted when you leave the player, when you
start the next video, and when VueGram starts, so videos never pile up on the TV's storage.
