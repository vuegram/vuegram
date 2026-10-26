# Contributing to VueGram

Thanks for helping. The full guide is on the docs site:
[docs.vuegram.com/developers/contributing](https://docs.vuegram.com/developers/contributing). In short:

- **Bugs:** open an issue with the bug template. Say which TV, Android version and VueGram version, and what you
  pressed. If VueGram crashed, Settings › Last crash report has the details; paste it.
- **Security problems:** never in a public issue. See [SECURITY.md](SECURITY.md).
- **Code:** read [AGENTS.md](AGENTS.md) for the project's rules (they apply to people too), then open a pull request.
  One topic per pull request. Before you push, run:

  ```sh
  ./gradlew testDebugUnitTest lintDebug assembleDebug
  ```

  CI runs the same, and lint must stay at zero errors. UI changes need a screenshot from a real TV, and a note of the
  TV you tried it on.
- **New behaviour is off by default** and turned on in Settings.
- **Never commit personal data:** no IP addresses, phone numbers, account names, API hashes, keys or TV serial
  numbers, in code, docs, screenshots or logs.

Contributions are released under the [MIT licence](LICENSE).
