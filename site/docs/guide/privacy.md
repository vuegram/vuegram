# Privacy & security

VueGram has no account of its own, no server of its own, no analytics and no tracking. Here is exactly what
happens to your data.

::: info VueGram is unofficial
VueGram is an unofficial client built on Telegram's own library, TDLib. It is not made by, affiliated with or
endorsed by Telegram. Its source code is public under the MIT license, so anyone can check what it does.
:::

## What stays on the TV

These are stored on the TV only, and never sent anywhere:

- where you stopped in each video, and which videos you have watched;
- the chats you pinned and the chats you hid;
- your [settings](./settings);
- your API ID and hash, and the login session, which TDLib keeps on the TV like any Telegram app does.

## What goes to Telegram, and only Telegram

VueGram talks to Telegram through **TDLib**, Telegram's own library, built from source (see
[TDLib, built from source](../developers/tdlib)). Through it, and only to Telegram:

- your login (the QR code, or your phone number, login code and 2-step verification password);
- loading your chats, their videos and thumbnails, and your searches;
- streaming the videos you play.

There are **no bots** and **no third-party servers** in the middle.

## One question to GitHub

Release builds ask GitHub's public releases API whether there is a newer VueGram, at most every 12 hours. Nothing
about you or the TV is sent. When you choose to update, the new version is downloaded from GitHub and checked
before it is installed. See [Updates](./updates).

## The stream server on 127.0.0.1

Telegram's library can only download a video to a file, and a video player wants a web address. To connect the
two, VueGram runs a tiny server **inside the app**:

- It only answers on `127.0.0.1`, the TV's own internal address. Other devices on your network can't reach it.
- Each video gets a **one-time token** in its address, so only the video you just started can be played through it.
- It plays one video at a time.
- The parts that were played are **deleted** when you leave the player, when the next video starts, and when VueGram
  starts. Videos never pile up on the TV.

The app is only allowed to use plain, unencrypted HTTP for `127.0.0.1`, and nowhere else.

## Sending the API keys from your phone

During setup, the TV can receive your API ID and hash from your phone, so you don't have to type them with a remote.
For that, VueGram runs a small web page **on the TV** for a few minutes:

- It answers only on the TV's own address on your home network, so only devices on your Wi-Fi can reach it at all.
- Its address contains a long random code (128 bits) that only the QR code on the TV shows; every other address gets
  "not found", and after a handful of wrong tries the page shuts down.
- It accepts one small form, and closes as soon as the keys arrive, when you leave that screen, or after 10 minutes.
- The page has no scripts and loads nothing from anywhere else.

The page is plain HTTP on your local network, not encrypted. That is acceptable for the API ID and hash, which
identify an app rather than unlock your account; your Telegram login itself never goes through it.

## Updates are verified

An update is installed only when its SHA-256 checksum matches the one published with the release **and** it is
signed with the same key as the app you have. Android then asks you to confirm. See [Updates](./updates).

## These websites

vuegram.com and these docs have no cookies, no analytics and no trackers. They load the Outfit font from Google
Fonts, and vuegram.com asks GitHub's public API for the latest version number.

## Found a security problem?

Please don't post it in a public issue. Contact the maintainers privately through the
[GitHub repository](https://github.com/vuegram/vuegram).
