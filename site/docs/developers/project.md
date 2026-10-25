# Project layout and rules

## Layout

| Path | What it is |
|---|---|
| `app/src/main/java/com/vuegram/` | The app (Kotlin). Namespace `com.vuegram`, applicationId `com.vuegram.tv`. |
| `…/data/library/` | `LibraryRepository` (chats, videos, search over TDLib), `MessageMapper`, pinned and hidden chats. |
| `…/data/history/` | `WatchHistory`: resume positions and watched videos (SharedPreferences, on the TV only). |
| `…/data/stream/` | `TdStreamServer` (TDLib to `http://127.0.0.1` with byte ranges) and its keep-alive service. |
| `…/data/update/`, `…/presentation/update/` | The update check against GitHub releases (`Updates`), PackageInstaller (`Installer`) and their dialogs. |
| `…/presentation/library`, `…/presentation/chat` | The two screens; `chat` also shows "Search all channels" results. |
| `…/presentation/ui/` | `GlassView`/`FocusGlass` (focus highlight), `GlowView`, `Images` (thumbnails and colours). |
| `app/src/main/java/org/drinkless/tdlib/` | TDLib's Java bindings. Never rename or move this package. |
| `app/src/main/jniLibs/` | TDLib (with OpenSSL), built from source by `scripts/build-tdlib.sh`. See [TDLib, built from source](./tdlib). |
| `scripts/` | `build-tdlib.sh` and `release.sh`. |
| `site/` | vuegram.com (`site/www`) and these docs (`site/docs`). |

## Rules

These keep VueGram fast, private and on good terms with Telegram. Please follow them in every change.

- **Never send TDLib requests in bursts.** Search, history and message lookups go one after another. Tele v0.1 fired
  5 searches on each of 40 chats at once and got `FLOOD_WAIT` / `-429` from Telegram until the list stayed empty.
- **Groups and channels by default.** Out of the box, only groups and channels are listed or searched. Private
  chats appear only when the viewer turns on [Show people](../guide/settings#show-people).
- **Streams are not storage.** Whatever `TdStreamServer` downloads is deleted on return from the player, on the next
  video and once per process at start (`clearVideoCacheOnce`). Keep it that way.
- **No bots or third-party servers** for playback, and **no analytics**.
- **Don't use "Telegram" or the paper-plane logo** in the app's name or icon (Telegram's API terms).
- **Playback** is `PlayerActivity` (Media3) by default; it checkpoints the position every 30 s and records it on
  leaving. Plain HTTP is allowed only to `127.0.0.1` (`res/xml/network_security_config.xml`); keep it that way.
- **mpv is the fallback**, opened for a result (`playerLauncher`, no `FLAG_ACTIVITY_NEW_TASK`): it reports
  `position`/`duration` in ms on Back, nothing at the end of the video. See
  [mpv-android's intent API](http://mpv-android.github.io/mpv-android/intent.html).
- **Updates stay verified.** An update is installed only when its SHA-256 matches the release's `.apk.sha256` and its
  signing certificate equals the installed app's. Don't loosen either check, and never commit the release key,
  `signing.properties` or anything from `dist/`.
- **Text direction.** Hebrew and English titles both occur: keep text `viewStart`-aligned and isolate mixed-language
  strings with `BidiFormatter`.

The repository's `AGENTS.md` has the same rules, for coding agents.
