# Security policy

VueGram holds a Telegram login on a TV, so security problems matter. Please report them privately.

## Reporting a vulnerability

Use GitHub's **private vulnerability reporting**: on https://github.com/vuegram/vuegram, open **Security › Report a
vulnerability**. Don't open a public issue, discussion or pull request for it.

Please include what you found, the VueGram version (Settings › Check for updates shows it), the steps to reproduce it,
and what an attacker could do with it. Leave out your Telegram account details, phone number and API hash; nobody
needs them to look into a report.

You'll get an answer within a week. Fixes ship as a new release, which the in-app updater offers to everyone.

## Supported versions

Only the latest release gets fixes. The in-app updater checks for it at most every 12 hours.

## What VueGram does to protect you

- **Your login stays on the TV.** TDLib's session and your API ID and hash are kept in the app's private storage and
  excluded from Android backups and device transfer.
- **Video streams stay on the TV.** Videos go from TDLib to the player through a server that listens on `127.0.0.1`
  only, with a new random token per video; plain HTTP is allowed to `127.0.0.1` and nowhere else.
- **The setup page is short-lived.** The page that receives the API keys from your phone exists only during
  setup, on the home network, behind a 128-bit random address, and closes once the keys arrive (at most 10 minutes).
- **Updates are verified twice.** An update is installed only if its SHA-256 matches the checksum published with the
  release and it is signed with the same key as the installed app.
- **Nothing is collected.** No analytics, no crash reporting service, no ads, no VueGram servers. The crash log stays
  on the TV.
- **TDLib is built from source** at a pinned commit, with published checksums.

## Out of scope

- Telegram's own servers, apps and protocol: report those to Telegram (https://telegram.org/faq#q-what-if-i-find-a-security-bug).
- Someone with physical access to an unlocked, rooted or ADB-enabled TV.
