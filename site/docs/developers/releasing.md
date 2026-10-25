# Releasing

A release is a signed APK on [GitHub releases](https://github.com/vuegram/vuegram/releases), plus its checksum.
Installed copies of VueGram [find it there](../guide/updates) and update themselves.

## The release key

Releases are signed with VueGram's release key, which lives **outside the repository** in `~/.vuegram/`:

- `vuegram-release.jks`, the keystore
- `signing.properties`, with `storeFile`, `storePassword`, `keyAlias` and `keyPassword`

(`scripts/release.sh` reads `~/.vuegram/signing.properties`, or the file the `VUEGRAM_SIGNING` environment variable
names.)

::: danger Back up ~/.vuegram somewhere safe
Android only installs an update signed with the same key as the installed app. Without this key, no installed copy
of VueGram could be updated: everyone would have to uninstall VueGram (and log in again) to get a new version.
:::

The certificate's SHA-256 is:

```
7b69b3ef239f7f9d80e3b1d11eebabaa75fb0cb7232e9327bcae298652d2da1b
```

Never commit the release key, `signing.properties` or anything from `dist/`.

## Make a release

1. **Raise `appVersion`** in `app/build.gradle.kts` and commit. `versionCode` follows it: `1.2.3` is `10203`
   (each part must be below 100).
2. **Run `scripts/release.sh`.** It:
   - refuses to run with uncommitted changes, or if the tag already exists;
   - builds and tests the release (`testDebugUnitTest assembleRelease`);
   - refuses the APK if it is not signed with the certificate above (the app would refuse it too);
   - writes three APKs to `dist/`, each with its `.sha256`: `vuegram.apk` (universal, for first installs and
     [vuegram.com/get](https://vuegram.com/get)), `vuegram-armeabi-v7a.apk` and `vuegram-arm64-v8a.apk` (one per
     processor type, about half the size; the in-app updater downloads the one matching `Build.SUPPORTED_ABIS`);
   - tags `v<version>` in this repository.
3. **Publish it** on `vuegram/vuegram` (the public, flattened repository), with **all six** files:

   ```sh
   scripts/release.sh --publish
   ```

   does that with the [`gh`](https://cli.github.com/) command-line tool, creating the tag on `vuegram/vuegram`'s own
   `main`. To do it by hand: on GitHub create a release there, tag `v<version>` on `main`, titled
   "VueGram &lt;version&gt;", attach the six files, write what changed, and publish it as the **latest release** (not a
   pre-release or draft). The file names carry no version on purpose: `releases/latest/download/vuegram.apk`, which
   vuegram.com/get points to, then always means the newest release.

## What the app expects

The app's update check reads the **latest** release and needs:

- an `.apk` asset, and
- its `.apk.sha256` next to it.

Without the checksum file, the app refuses the update. It also refuses an APK signed with any other key.
