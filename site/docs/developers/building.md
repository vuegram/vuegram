# Building from source

VueGram is a plain Android app in Kotlin. You can build it with the usual Android tools.

## What you need

- **JDK 17**
- **The Android SDK, platform 36** (Android Studio installs it, or use the command-line tools)
- Git

The app's namespace is `com.vuegram` and its application ID is `com.vuegram.tv`.

## Build

```sh
git clone https://github.com/vuegram/vuegram.git
cd vuegram
./gradlew assembleDebug
```

The APK is `app/build/outputs/apk/debug/app-debug.apk`.

## Install it on a TV

With ADB connected to the TV (see [Getting started](../guide/getting-started#with-adb-from-a-computer)):

```sh
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or push it to the TV and install it there with `pm install`.

::: warning Debug and release builds don't mix
A debug build is signed with your own debug key, and released VueGram is signed with VueGram's release key.
Android won't install one over the other: uninstall first (which logs you out). Debug builds also don't check for
updates by themselves.
:::

## Check a change

Before you send a change, run the unit tests and the build:

```sh
./gradlew testDebugUnitTest assembleDebug
```

The unit tests live in `app/src/test` (for example the QR code round-trip). UI changes are checked on a real TV with
screenshots: the D-pad from every edge, and Back from each screen.

## The native library

VueGram uses TDLib through `libtdjni.so`, which is already in the repository under `app/src/main/jniLibs/`, built
from source. You don't need to rebuild it to build the app. If you want to, or want to check it, see
[TDLib, built from source](./tdlib).

## Next

- [Project layout and rules](./project)
- [Releasing](./releasing)
- [Contributing](./contributing)
