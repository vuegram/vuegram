# Updates

You install VueGram once. After that it keeps itself up to date from its
[GitHub releases](https://github.com/vuegram/vuegram/releases), and it is careful about what it installs.

## How it works

1. **It checks, at most every 12 hours.** VueGram asks GitHub's public API for the newest VueGram release. That is
   the only request: nothing about you or the TV is sent.
2. **It downloads the new version quietly.** When there is a newer release, VueGram downloads it in the background
   while you watch, and refuses it unless:
   - its **SHA-256 checksum** matches the `.apk.sha256` file published with the release, and
   - it is **signed with the same key** as the VueGram you have installed.
3. **It asks once.** When the update is ready, the library's header shows **Update ready**, and VueGram asks
   "VueGram *version* is ready to install": **Install now** or **Later**. After Later it doesn't ask again for that
   version; the header button stays, so you can install it whenever you like.
4. **It installs.** On Android 12 and newer, once VueGram has updated itself one time, later updates install
   without any extra screen. Otherwise Android shows its own install screen: choose **Install** (or **Update**).
5. **What's new.** The next time you open VueGram, it shows what changed in the new version.

## Automatic updates off, or a metered network

In **Settings › Automatic updates** you can turn the background download off. VueGram then only tells you a new
version is out (the header shows **Update to** *version*), and downloads it, with a progress bar, when you choose
**Update**. It does the same on a metered connection, such as a phone's hotspot, so it never spends your data
without asking.

Your login, pinned and hidden chats, and where you stopped in each video are kept across updates.

## The first time: "Install unknown apps"

Android doesn't let an app install updates until you allow it, once. The first time you choose **Update**,
VueGram explains this (**Allow VueGram to install its updates**) and offers **Open settings**:

1. On the next screen, turn on **Allow from this source** for VueGram.
2. Press <kbd>Back</kbd>.
3. Choose **Install now** (or **Update**) again.

You won't be asked again. If your TV has no "Install unknown apps" screen for VueGram, it says so; in that case,
install the new version the same way you installed the first one (see [Getting started](./getting-started#install-vuegram-on-the-tv)).

## Development builds

Only release builds check for updates by themselves. A build you made yourself with `./gradlew assembleDebug` does
not.

If an update fails, see [Troubleshooting](./troubleshooting#an-update-fails).
