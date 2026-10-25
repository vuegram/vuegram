<div align="center">

<img src="site/www/logo.svg" alt="VueGram logo: a folded violet play button" width="112" height="112">

# VueGram

*Say it "view-gram".*

**Your Telegram channels, on the big screen.**

An open-source Android TV and Google TV app that shows the groups and channels you follow as a grid of their photos
and plays their videos on the TV, streamed straight from Telegram.

[![Android TV and Google TV, Android 6.0 or newer](https://img.shields.io/badge/Android%20TV%20%26%20Google%20TV-Android%206.0%2B-000000?style=flat-square)](https://docs.vuegram.com/guide/getting-started)
[![License: MIT](https://img.shields.io/badge/license-MIT-000000?style=flat-square)](LICENSE)
[![No analytics](https://img.shields.io/badge/analytics-none-000000?style=flat-square)](https://docs.vuegram.com/guide/privacy)

[**Download**](https://github.com/vuegram/vuegram/releases) ·
[**Docs**](https://docs.vuegram.com) ·
[**Website**](https://vuegram.com)

</div>

> [!NOTE]
> VueGram is an unofficial client built on Telegram's own library (TDLib). It is not made by, affiliated with or
> endorsed by Telegram.

## What it does

- **Library:** your groups and channels, with **Continue watching** and your **pinned** chats at the top. Hold OK
  on a chat to pin or hide it. Out of the box it lists groups and channels only, never private chats or bots.
- **Chat:** a chat's videos and video files as a 16:9 grid with thumbnails, length, date and size, newest first,
  and a search field for that chat.
- **Search all channels:** Telegram's search across every group and channel at once.
- **Playback:** a built-in player (AndroidX Media3) that tries another decoder, then Android's software decoders,
  when the TV's hardware decoder refuses a file. It has a TV overlay: **OK** pauses, **left/right** jump 10 s back
  and 30 s forward (hold to keep adding), **Back** leaves. Files the built-in player cannot decode open in
  [mpv-android](https://github.com/mpv-android/mpv-android) at the same position, and hold OK on a video offers
  "Play in mpv", "Play from the beginning" and "Mark as (not) watched".
- **Player extras:** Up next (the chat's next video after a 10-second countdown), subtitles and audio tracks, speed,
  a sleep timer, and number keys to jump to 10–90 %.
- **Voice search** with the TV's own speech recognition, and your **Telegram folders** as library tabs, with a
  "New in your channels" row.
- **Resume:** where you stopped, what you finished and what you hid stay on the TV only.
- **QR login:** scan a code with Telegram on your phone. No SMS, no login email.
- **Settings:** *Show people* adds private chats with people to the library, and *Show messages* shows all of a
  chat's messages instead of only its videos. Both are off by default.
- **Languages:** English, Chinese (Simplified and Traditional), Spanish, Portuguese (Brazil), Russian, Arabic, Hebrew,
  Hindi, Indonesian, Persian and Turkish, fully right-to-left in Arabic, Hebrew and Persian. It follows the TV's language, or the one picked in Settings.
- **Updates:** VueGram finds new [releases](https://github.com/vuegram/vuegram/releases) by itself and installs them
  only after checking the checksum and the signing key.

The full guide is at **[docs.vuegram.com](https://docs.vuegram.com)**.

## How it works

```
Telegram ──► TDLib inside VueGram ──► stream server on 127.0.0.1 ──► player (Media3, or mpv)
             └─────────────────────────── all on your TV ──────────────────────────────┘
```

- Videos are streamed by a small server inside the app on `127.0.0.1` (one-time token per video). Nothing goes
  through a bot or a third-party server, and the parts you watch are deleted afterwards, so videos never pile up on
  the TV's storage.
- There are no analytics. Apart from Telegram, the only request is the update check: release builds ask GitHub for
  the newest [VueGram release](https://github.com/vuegram/vuegram/releases) at most every 12 hours. A newer one gets
  an **Update** button in the library header and is offered once; VueGram downloads it, refuses it unless it matches
  the checksum published with the release and is signed with the same key as the installed app, and then hands it to
  Android, which asks you to confirm. The first time, Android also asks you to allow VueGram to install apps.
  Nothing about you or the TV is sent; it is one request to GitHub's public API.

More in [Privacy & security](https://docs.vuegram.com/guide/privacy).

## Install

**Requirements**

- Android TV / Google TV, Android 6.0 or newer.
- Your own Telegram API ID and hash from [my.telegram.org](https://my.telegram.org/apps); the app asks for them on
  first start.
- Optional: [mpv-android](https://github.com/mpv-android/mpv-android/releases), for the rare files the built-in
  player cannot decode.

**Steps**

1. On the TV, open the [Downloader](https://play.google.com/store/apps/details?id=com.esaba.downloader) app (free on the Play Store) and type **`vuegram.com/get`**: it
   downloads the newest `vuegram.apk` from the
   [latest release](https://github.com/vuegram/vuegram/releases). Or install that file with `adb install`.
2. Choose **Install**. VueGram keeps itself up to date from then on.
3. Open VueGram, enter your API ID and hash, and log in.

Step by step: [Getting started](https://docs.vuegram.com/guide/getting-started).

## Logging in

After the API keys, VueGram offers **Log in with a QR code**: it shows a code on the TV, and you scan it with
Telegram on your phone (Settings › Devices › Add Device, called Link Desktop Device in older versions). No SMS and no login email are involved, and the
code refreshes itself until it is scanned. If the account has 2-step verification, the TV asks for that password
next. Logging in with a phone number and code still works as a fallback; when Telegram asks for a login email or a
paid SMS on that route, the screen offers QR login instead. More in
[Logging in](https://docs.vuegram.com/guide/logging-in).

## Building

JDK 17 and the Android SDK (platform 36). Then:

```sh
./gradlew assembleDebug
```

The APK is `app/build/outputs/apk/debug/app-debug.apk`. Install it with `adb install` or by pushing it and running
`pm install` on the TV. Before sending a change, `./gradlew testDebugUnitTest assembleDebug` must pass. See
[Building from source](https://docs.vuegram.com/developers/building) and, for coding agents, [AGENTS.md](AGENTS.md).

## Releasing

Releases are signed with VueGram's release key, which lives outside the repository in `~/.vuegram/`
(`vuegram-release.jks` and `signing.properties` with `storeFile`, `storePassword`, `keyAlias`, `keyPassword`).
Android only installs an update signed with the same key as the installed app, so **back up `~/.vuegram/`
somewhere safe**: without it, everyone would have to uninstall VueGram (and log in again) to get a new version.
The certificate's SHA-256 is `7b69b3ef239f7f9d80e3b1d11eebabaa75fb0cb7232e9327bcae298652d2da1b`.

1. Raise `appVersion` in `app/build.gradle.kts` and commit. `versionCode` follows it (`1.2.3` is `10203`).
2. `scripts/release.sh` builds and tests the release, refuses it if it is not signed with that certificate, writes
   `dist/vuegram.apk` (universal) plus `vuegram-armeabi-v7a.apk` and `vuegram-arm64-v8a.apk`, each with its
   `.sha256`, and tags `v<version>`.
3. Publish a release on vuegram/vuegram with all six files (`scripts/release.sh --publish` does this with `gh`). The
   app's update check reads the latest release and downloads the APK for the TV's processor, with its checksum.

Details: [Releasing](https://docs.vuegram.com/developers/releasing).

## TDLib, built from source

`app/src/main/jniLibs/<abi>/libtdjni.so` and the bindings in `app/src/main/java/org/drinkless/tdlib/` are built
from TDLib's own source with TDLib's own Android Dockerfile (Ubuntu 24.04, Android NDK r23c, OpenSSL 1.1.1w), at
commit [`cb863c16`](https://github.com/tdlib/td/commit/cb863c1600082404428f1a84e407b866b9d412a8):

```sh
scripts/build-tdlib.sh            # or: scripts/build-tdlib.sh <another TDLib commit>
```

It needs Docker; the first run downloads about 2 GB and compiles for about half an hour. The files it produced for
this repository:

| File | SHA-256 |
|---|---|
| `armeabi-v7a/libtdjni.so` | `8e45fddeaac6fb3b76d141c8e9822646901aa4f31c261560f84bb3ab373043a4` |
| `arm64-v8a/libtdjni.so` | `0ccdc1f6548cbb582bc2617a5f5206224a21349cbd9d12739f83e5f210393d70` |

Tele shipped a prebuilt `libtdjni.so` (armeabi-v7a only) that could not be checked. It was replaced with this build:
the same TDLib commit, so `TdApi.java` and `Client.java` came out byte for byte identical to Tele's and the native
methods the library registers are the same. More in
[TDLib, built from source](https://docs.vuegram.com/developers/tdlib).

## The websites

[vuegram.com](https://vuegram.com) and [docs.vuegram.com](https://docs.vuegram.com) live in [`site/`](site/) and
deploy to Cloudflare Workers from this repository. See [site/README.md](site/README.md).

## Credits

- [TDLib](https://github.com/tdlib/td) (Boost Software License) with OpenSSL 1.1.1w (OpenSSL License), built from
  source as described above.
- [Tele](https://github.com/saieshshirodkar/Tele) by Syndicate (MIT): VueGram started from Tele v0.1. Its login
  screens and the message mapping still come from it.
- [cleanyourtv](https://github.com/Gathin23/cleanyourtv) (MIT): the glass focus highlight is ported from its
  launcher, and the look follows it.
- [Outfit](https://github.com/Outfitio/Outfit-Fonts) font (SIL Open Font License, `FONT-LICENSE-OFL.txt`).

## License

MIT, see [`LICENSE`](LICENSE).

---

<sub>VueGram is an unofficial app. It is not made by, affiliated with or endorsed by Telegram.</sub>
