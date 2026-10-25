# Contributing

Thanks for helping make VueGram better. Fixes, translations of these docs, and ideas are all welcome.

## Before you start

- Read [Project layout and rules](./project). The rules there (no request bursts, no analytics, streams are not
  storage, verified updates) are not up for negotiation in a pull request.
- For anything bigger than a fix, open an issue first so we can talk it through.

## Making a change

1. Fork the repository and create a branch.
2. Make your change. Keep it to one topic.
3. Run the checks:

   ```sh
   ./gradlew testDebugUnitTest assembleDebug
   ```

4. If you changed the UI, try it on a real TV: the D-pad from every edge, and Back from each screen. Put
   screenshots in the pull request.
5. Open a pull request that says what changed and which TV you tried it on.

## The websites

vuegram.com lives in `site/www` (plain HTML, CSS and JavaScript), and these docs in `site/docs` (VitePress). To
work on the docs:

```sh
cd site/docs
npm install
npm run dev
```

`npm run build` must pass: it fails on dead links. Neither site may load analytics, trackers or third-party scripts.

## License

VueGram is MIT licensed. By contributing, you agree that your contribution is released under the same license.
