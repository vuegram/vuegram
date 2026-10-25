# Getting started

VueGram puts the Telegram groups and channels you follow on your TV, as a grid of their photos, and plays their
videos on the big screen. This page takes you from nothing to a working VueGram in about ten minutes.

::: info VueGram is unofficial
VueGram is an unofficial app built on Telegram's own library (TDLib). It is not made by, affiliated with or
endorsed by Telegram.
:::

## What you need

- **An Android TV or Google TV**, running **Android 6.0 or newer**.
- **A Telegram account**, and Telegram on your phone (for the easiest way to log in).
- **Your own Telegram API ID and API hash**, from [my.telegram.org](https://my.telegram.org/apps). They are free;
  the next section shows how to get them.
- Optional: [mpv-android](https://github.com/mpv-android/mpv-android/releases), for the rare video the built-in
  player cannot decode. See [the mpv fallback](./using-vuegram#the-mpv-fallback).

## Get your API ID and hash

Every app that talks to Telegram identifies itself with an API ID and an API hash. VueGram asks you for your own
the first time it starts, so your copy talks to Telegram as itself.

1. On a phone or computer, open [my.telegram.org](https://my.telegram.org/apps) and log in with your phone
   number. Telegram sends the login code to your Telegram app.
2. Open **API development tools**.
3. If you have never done this before, fill in the short form to create an app. Any title and short name will
   do, for example "VueGram on my TV".
4. You now see your **App api_id** (a number) and **App api_hash** (a long string of letters and digits). Keep
   this page open: you will type both on the TV.

::: tip Treat them like a password
The API ID and hash belong to your Telegram account. Don't post them anywhere or share them with anyone.
:::

## Install VueGram on the TV

The short address **vuegram.com/get** always downloads the newest VueGram, so that is all you need to type on the
TV. (It is `vuegram.apk` from the latest [GitHub release](https://github.com/vuegram/vuegram/releases), which works on
every TV.) Once VueGram is installed, it [updates itself](./updates), with a smaller file made for your TV.

Pick whichever way suits you.

### With the Downloader app on the TV (easiest)

1. Install [**Downloader**](https://play.google.com/store/apps/details?id=com.esaba.downloader) (by AFTVnews) from your TV's Play Store: search for "Downloader", or send it
   to the TV from the [Play Store page](https://play.google.com/store/apps/details?id=com.esaba.downloader) on a phone or computer signed in to the same Google account. Any
   other app that downloads files from a web address works too.
2. Allow it to install apps: Android asks for this the first time, or you can turn on **Install unknown apps**
   for that app in the TV's settings (on many TVs under Settings › Apps › Security & restrictions).
3. In Downloader, type **`vuegram.com/get`** and choose **Go**.
4. When the download finishes, choose **Install**. Downloader then offers to delete the file; you can.

### With ADB from a computer

ADB is Android's developer tool. Use it if you already have it, or don't mind setting it up.

1. On the TV, turn on **Developer options**: open Settings › Device Preferences › About (the names differ a little
   between TVs) and press OK on **Build** seven times.
2. In Developer options, turn on **USB debugging**, or **Network debugging** if your TV has it.
3. On the computer, download **`vuegram.apk`** from [vuegram.com/get](https://vuegram.com/get) (or the
   [releases page](https://github.com/vuegram/vuegram/releases)), then connect and install. Use your TV's IP address
   in place of `192.168.1.50`:

   ```sh
   adb connect 192.168.1.50:5555
   adb install vuegram.apk
   ```

   ::: tip Smaller downloads
   Each release also has a file per processor type: `vuegram-armeabi-v7a.apk` (most TVs, about 23 MB) and
   `vuegram-arm64-v8a.apk` (about 29 MB). `vuegram.apk` (about 45 MB) works everywhere. The in-app updater picks the
   right small one by itself.
   :::

   The TV may ask you to allow the connection the first time; accept it on the TV.

4. When you are done, turn debugging off again in Developer options.

## First start

1. Open **VueGram** from the TV's app list. The welcome screen offers **Get started**, and a **Language** button if
   you'd like VueGram in another language than the TV's.
2. **Step 1, your API keys.** The TV shows a QR code. Scan it with your phone's camera: a small page opens on your
   phone, with a button to my.telegram.org. Get your API ID and hash there (see above), paste them into the page and
   tap **Send to TV**. The TV fills them in by itself.

   ::: tip No phone handy?
   Choose **Type them with the remote** and enter the API ID and API hash with the TV's keyboard instead.
   :::

   ::: details How the phone page works
   The page comes from the TV itself, over your home Wi-Fi: your phone must be on the same network. It only exists
   while that screen is open (at most 10 minutes), its address contains a long random code that only the QR code
   shows, and it closes as soon as the keys arrive. Nothing goes through the internet or any server.
   :::

3. **Step 2, sign in.** Scan the next QR code with Telegram on your phone: see [Logging in](./logging-in).

Your groups and channels appear in the library. Continue with [Using VueGram](./using-vuegram).
