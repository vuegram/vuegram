# Troubleshooting

Something not working? Find it below. If it isn't here, ask on the
[GitHub repository](https://github.com/vuegram/vuegram).

## A video won't play

VueGram already does a lot by itself here:

1. When the TV's hardware decoder refuses a file, the built-in player **tries another decoder**, then **Android's
   software decoders**.
2. If none of them can play it, VueGram hands the video to **mpv-android** at the same position.

So if you see **"This video's format cannot be played here, and mpv is not installed"**, install
[mpv-android](https://github.com/mpv-android/mpv-android/releases) on the TV and play the video again. You can also
open any video in mpv yourself: hold <kbd>OK</kbd> on it and choose **Play in mpv**.

If a video starts but stalls, it is usually the connection: Telegram streams the video while you watch. Give it a
moment, or try again a little later.

## Login is stuck

- **The QR code doesn't log in.** Scan it from Telegram on your phone, under **Settings › Devices › Link Desktop
  Device**, not with the phone's camera app. The code refreshes itself, so an old photo of it won't work: scan the
  one on the screen now.
- **Telegram asks for a login email, or a paid SMS.** This happens on the phone-number route. Use **Log in with a QR
  code** instead: it needs neither. See [Logging in](./logging-in#why-qr-is-the-better-way).
- **It asks for a password you don't know.** That is your Telegram **2-step verification** password (the cloud
  password you set in Telegram), not your phone's lock code. You can reset it from Telegram on your phone, under
  Settings › Privacy and Security.
- **The API ID or hash was wrong.** Check both on [my.telegram.org](https://my.telegram.org/apps) under API
  development tools. The ID is only digits; the hash is a long string of letters and digits. If VueGram keeps
  rejecting them, clear VueGram's data in the TV's settings (Settings › Apps › VueGram › Clear data) and enter them
  again. This logs you out and resets your pinned and hidden chats and where you stopped.

## FLOOD_WAIT

If VueGram says **"Telegram asked VueGram to slow down. Try again in … seconds"** (Telegram calls this
`FLOOD_WAIT`, or error `429`), wait that long, then press **Try again**. VueGram never retries by itself, so it can't
make the wait longer.

Telegram limits how many requests one account can make in a short time. VueGram is careful to send its searches
and lookups one after the other, never in bursts, but a lot of searching in a short time can still hit the limit.
It clears up by itself after a short wait; how long is up to Telegram.

## The library is empty

- **"No groups or channels yet".** By default VueGram lists only **groups and channels**, never private chats or
  bots. If you only have private chats, the library is empty. Join a channel in Telegram, or turn on
  [Show people](./settings#show-people).
- **Did you hide them?** Hidden chats only show up behind the **Hidden channels** card. See
  [Hidden channels](./using-vuegram#hidden-channels).
- **"Could not load your chats".** The message says why. Check the TV's internet connection; if it mentions
  `FLOOD_WAIT`, see [above](#flood-wait).
- **A chat says "No videos in this chat".** VueGram shows a chat's videos and video files only (unless
  [Show messages](./settings#show-messages) is on). A chat with only text and photos has nothing to show.

## An update fails

When an update can't be installed, VueGram says **The update was not installed** and why:

| Message | What to do |
|---|---|
| *the download does not match the release's SHA-256 checksum* | The download was damaged or incomplete. Try again later. |
| *the release has no SHA-256 checksum, so it can't be checked* | The release is missing its `.apk.sha256` file. VueGram refuses to install it unchecked; wait for the release to be fixed. |
| *the download is not signed with VueGram's key* | VueGram only installs updates signed with the same key as the installed app. Don't install this file. |
| *GitHub answered …* or *download failed (…)* | GitHub couldn't be reached or refused the request. Check the TV's internet connection and try again later. |

If you are asked to **Allow VueGram to install its updates**, that is the one-time "Install unknown apps"
permission: see [Updates](./updates#the-first-time-install-unknown-apps).

If nothing else works, you can always install the new version by hand, the same way as the first time (see
[Getting started](./getting-started#install-vuegram-on-the-tv)). Install it over the existing app; don't uninstall
first, or you will have to log in again.
