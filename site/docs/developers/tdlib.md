# TDLib, built from source

VueGram talks to Telegram through [TDLib](https://github.com/tdlib/td), Telegram's own client library. The native
library and its Java bindings in this repository are built from TDLib's own source, so anyone can check them.

## What is built

- `app/src/main/jniLibs/<abi>/libtdjni.so`, for `armeabi-v7a` and `arm64-v8a`
- the Java bindings in `app/src/main/java/org/drinkless/tdlib/`

They are built with TDLib's own Android Dockerfile (Ubuntu 24.04, Android NDK r23c, OpenSSL 1.1.1w), at commit
[`cb863c16`](https://github.com/tdlib/td/commit/cb863c1600082404428f1a84e407b866b9d412a8).

## Build it yourself

```sh
scripts/build-tdlib.sh            # or: scripts/build-tdlib.sh <another TDLib commit>
```

It needs **Docker**. The first run downloads about 2 GB and compiles for about half an hour.

## Checksums

The files it produced for this repository:

| File | SHA-256 |
|---|---|
| `armeabi-v7a/libtdjni.so` | `8e45fddeaac6fb3b76d141c8e9822646901aa4f31c261560f84bb3ab373043a4` |
| `arm64-v8a/libtdjni.so` | `0ccdc1f6548cbb582bc2617a5f5206224a21349cbd9d12739f83e5f210393d70` |

## Why it was rebuilt

VueGram started from [Tele](https://github.com/saieshshirodkar/Tele), which shipped a prebuilt `libtdjni.so`
(armeabi-v7a only) that could not be checked. It was replaced with this build: the same TDLib commit, so
`TdApi.java` and `Client.java` came out byte for byte identical to Tele's, and the native methods the library
registers are the same.

## Rules

- **Never rename or move `org.drinkless.tdlib`.** The native `libtdjni.so` binds to these exact class names.
- **Never drop in a prebuilt `.so` from elsewhere.** Build it with `scripts/build-tdlib.sh`, and update the
  checksums here and in the README.
