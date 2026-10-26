# AGENTS.md

Notes for coding agents working on VueGram. People should start with `README.md`.

## Layout

| Path | What it is |
|---|---|
| `app/src/main/java/com/vuegram/` | The app (Kotlin). Namespace `com.vuegram`, applicationId `com.vuegram.tv`. |
| `.../data/library/` | `LibraryRepository` (chats, videos, messages, search over TDLib), `MessageMapper`, `MessageFeed` (message cards), pinned and hidden chats. |
| `.../data/settings/` | `AppSettings`: Show people and Show messages (both off by default). |
| `.../data/diagnostics/` | `CrashLog`: the last crash, kept on the TV for Settings; never sent anywhere. |
| `.../core/` | `TdLibClient` (the one TDLib client, its updates and connection state), `Errors` (TDLib errors in plain words), `Texts`, `AppLanguage`. |
| `.../data/login/` | `Login` (TDLib's sign-in as one state: keys, QR, phone, code, password), `ApiKeys`, `KeyDrop` (the short-lived phone page for the API keys). |
| `.../presentation/onboarding/` | `OnboardingFragment`: welcome, API keys, sign-in. |
| `.../data/history/` | `WatchHistory`: resume positions and watched videos (SharedPreferences, on the TV only). |
| `.../data/stream/` | `TdStreamServer` (TDLib -> `http://127.0.0.1` with byte ranges) and its keep-alive service. |
| `.../data/update/`, `.../presentation/update/` | The update check against GitHub releases (`Updates`), PackageInstaller (`Installer`) and their dialogs. |
| `.../presentation/library`, `/chat`, `/settings` | The screens. `chat` shows a video grid or (Show messages) a message list, the photo viewer, and "Search all channels" results. |
| `.../presentation/ui/` | `FocusRing`/`FocusHighlight` (focus highlight), `GlowView`, `Backdrop` (the softened picture behind the top), `SkeletonView` (loading placeholders), `ExpandingSearch`, `ToggleSwitch`, `Motion` (the three animation speeds), `Images` (thumbnails and colours), `StatusText` (errors and the connection pill). Text sizes come from the six-step scale in `res/values/dimens.xml`. |
| `app/src/main/java/org/drinkless/tdlib/` | TDLib's Java bindings. Never rename or move this package: the native `libtdjni.so` binds to these exact class names. |
| `app/src/main/jniLibs/` | TDLib (with OpenSSL) built from source by `scripts/build-tdlib.sh` at a pinned commit; checksums in THIRD_PARTY_NOTICES.md. Never drop in a prebuilt `.so` from elsewhere. |

## Rules

- **Never send TDLib requests in bursts.** Search, history and message lookups go one after another. Firing a few
  searches on each of dozens of chats at once earns `FLOOD_WAIT` / `-429` from Telegram, and the list stays empty.
- **Groups and channels by default.** Private chats with people and Saved Messages are listed only when the viewer
  turns on Settings › Show people; bots and deleted accounts are never listed. Secret chats are never shown.
- **New behaviour is off by default** and turned on in Settings, like Show people and Show messages.
- **Streams are not storage.** Whatever TdStreamServer downloads is deleted on return from the player, on the next
  video and once per process at start (`clearVideoCacheOnce`). Keep it that way.
- **No bots or third-party servers** for playback, and no analytics or crash reporting services. The crash log stays
  on the TV. Nothing is backed up by Android (`allowBackup="false"`, `res/xml/data_extraction_rules.xml`): the TDLib
  session and API keys must never leave the TV.
- **The key-drop page stays tight:** home-network address only, 128-bit random path, one small form, closes after
  the keys arrive, on leaving the screen or after 10 minutes; no scripts. Don't add routes or widen any of these.
- **Errors reach the viewer** in plain words (`StatusText.error`) with a Try again button; never retry in a loop.
- **Don't use "Telegram" or the paper-plane logo** in the app's name or icon (Telegram's API terms).
- Playback is `PlayerActivity` (Media3) by default; it checkpoints the position every 30 s and records it on leaving.
  Plain HTTP is allowed only to `127.0.0.1` (`res/xml/network_security_config.xml`); keep it that way.
- mpv is the fallback, opened for a result (`playerLauncher`, no `FLAG_ACTIVITY_NEW_TASK`): it reports
  `position`/`duration` in ms on Back, nothing at the end of the video. See
  http://mpv-android.github.io/mpv-android/intent.html

- **Updates stay verified.** An update is installed only when its SHA-256 matches the release's `.apk.sha256` and its
  signing certificate equals the installed app's. Don't loosen either check, and never commit the release key,
  `signing.properties` or anything from `dist/`. Releases are made with `scripts/release.sh` (see the README).

## Checking a change

`./gradlew testDebugUnitTest lintDebug assembleDebug` must pass, as CI runs it (`.github/workflows/ci.yml`). Lint
stays at zero errors; `app/lint.xml` lists the few checks turned off, each with its reason. Unit tests are in
`app/src/test` (TdApi objects can be built in tests, but their `toString()` is native, so never print them). UI changes are checked on a real TV with screenshots (D-pad from every edge,
Back from each screen). Hebrew and English titles both occur: keep text `viewStart`-aligned and isolate mixed-language
strings with `BidiFormatter`.
