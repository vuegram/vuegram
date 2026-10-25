# VueGram's websites

| Folder | Site | What it is | Cloudflare Worker |
|---|---|---|---|
| [`www/`](www/) | [vuegram.com](https://vuegram.com) | The product page: plain HTML, CSS and JavaScript, no build step | `vuegram-www` |
| [`docs/`](docs/) | [docs.vuegram.com](https://docs.vuegram.com) | The documentation, built with [VitePress](https://vitepress.dev) | `vuegram-docs` |
| [`github-profile/`](github-profile/) | github.com/vuegram | The organisation's profile README and its logo, to copy into the public `vuegram/.github` repository as `profile/README.md` and `profile/logo.svg` | none |

Both sites are **Cloudflare Workers with static assets only** (no Worker script), each configured by its own
`wrangler.jsonc`, and deployed from GitHub by **Cloudflare Workers Builds** on every push to `main`. Neither site
uses cookies, analytics, trackers or third-party scripts (Google Fonts is the only outside stylesheet), and the rule
stays that way.

## How the sites are deployed now: Cloudflare Pages, direct upload

Today both sites are **Cloudflare Pages** projects, uploaded from this PC with wrangler (not connected to Git):

| Site | Pages project | Pages address | Custom domain (added in the dashboard) |
|---|---|---|---|
| vuegram.com | `vuegram` | https://vuegram.pages.dev | `vuegram.com` (and `www.vuegram.com`) |
| docs.vuegram.com | `vuegram-docs` | https://vuegram-docs.pages.dev | `docs.vuegram.com` |

To publish a new version, from the repository root, logged in with `npx wrangler login`. Upload a copy of the folder
without `wrangler.jsonc` and `.assetsignore` (Pages would serve them as public files):

```sh
# vuegram.com
rm -rf /tmp/vuegram-www && mkdir /tmp/vuegram-www
git ls-files site/www | grep -vE 'wrangler.jsonc$|\.assetsignore$' | xargs -I{} cp {} /tmp/vuegram-www/
npx wrangler pages deploy /tmp/vuegram-www --project-name vuegram --branch main

# docs.vuegram.com
(cd site/docs && npm ci && npm run build)
npx wrangler pages deploy site/docs/.vitepress/dist --project-name vuegram-docs --branch main
```

`--branch main` makes it the production deployment. Pages reads each site's `_headers` file the same way Workers do.
Custom domains for Pages are added in the dashboard (**Workers & Pages › the project › Custom domains**); wrangler
can't add them.

The `wrangler.jsonc` files below are for the alternative, moving each site to a Worker deployed by Workers Builds.

## Deploying with Workers Builds (alternative)

Do this once per site. You need a Cloudflare account where the `vuegram.com` zone is active, and no existing DNS
records for `vuegram.com`, `www.vuegram.com` or `docs.vuegram.com` that would clash (a Worker custom domain creates
its own DNS record and certificate).

1. In the Cloudflare dashboard, open **Workers & Pages**, choose **Create application**, and pick the option to
   start from a Git repository, with **GitHub** as the provider.
2. Connect GitHub if asked, and give Cloudflare's GitHub app access to `vuegram/vuegram` (it is a private
   repository for now, so it must be granted explicitly).
3. Pick the `vuegram/vuegram` repository and fill in the settings below. The **project/Worker name must equal the
   `name` in that folder's `wrangler.jsonc`**, or the build fails.
4. Choose **Save and Deploy**. The custom domains in `wrangler.jsonc` are attached on the first deploy.

| Setting | vuegram.com | docs.vuegram.com |
|---|---|---|
| Worker name | `vuegram-www` | `vuegram-docs` |
| Production branch | `main` | `main` |
| Root directory | `site/www` | `site/docs` |
| Build command | *(leave empty)* | `npm run build` |
| Deploy command | `npx wrangler deploy` | `npx wrangler deploy` |
| Custom domains (from `wrangler.jsonc`) | `vuegram.com`, `www.vuegram.com` | `docs.vuegram.com` |

Notes:

- For the docs, Workers Builds installs the dependencies from `site/docs/package-lock.json` before the build command
  runs. The build writes `site/docs/.vitepress/dist`, which `wrangler.jsonc` serves.
- The marketing site has no build step: `site/www` itself is the site. `site/www/.assetsignore` keeps
  `wrangler.jsonc` out of the upload.
- Each folder's `_headers` file sets response headers (the marketing site's Content-Security-Policy among them).
  For the docs it lives in `site/docs/public/_headers` and is copied into the build output.
- Both Workers share one repository. If the dashboard offers **build watch paths**, set `site/www/*` and
  `site/docs/*` respectively, so a commit to the Android app doesn't redeploy the websites.
- To connect an existing Worker instead: open it, then **Settings › Builds › Connect**, and use the same settings.

## Previewing locally

### vuegram.com

The pages use root-relative paths (`/styles.css`), so open them through a local web server rather than as a file:

```sh
cd site/www
npx wrangler dev        # serves the folder like Cloudflare does, with _headers and the 404 page
# or any static server, for example:
python3 -m http.server 8000
```

`wrangler dev` runs on your computer and needs no Cloudflare login.

### docs.vuegram.com

```sh
cd site/docs
npm install
npm run dev             # live-reloading preview at http://localhost:5173
npm run build           # the production build; fails on dead links
npm run preview         # serves the production build
```

Commit `package.json` and `package-lock.json`; `node_modules/`, `.vitepress/dist/` and `.vitepress/cache/` are
ignored.

## Editing

- **vuegram.com:** `www/index.html`, `www/styles.css`, `www/main.js`. The colours and the Outfit font follow the app
  (`app/src/main/res/values/colors.xml`). The TV on the front page is drawn in HTML and CSS (its channel names are
  examples) and can be driven like a remote: arrow keys or clicks move the focus, Enter or a click "opens" a card.
  `main.js` also does the scroll tilt, the frosted nav, the reveal-on-scroll and the card spotlight, all switched off
  with `prefers-reduced-motion`. It asks GitHub's public API for the latest release and the star count, and the page
  keeps its plain text when that fails (it does while the repository is private). If you add anything that loads from
  another origin, add it to the Content-Security-Policy in `www/_headers`.
- **The logo:** the Folded play, a violet play button with folded facets. `www/logo.svg`, `www/favicon.svg`, the docs'
  copies, `github-profile/logo.svg` and the sprite in `www/index.html` are drawn from the app's
  `app/src/main/res/drawable-v24/vg_glyph.xml` (the app's banner adds the wordmark in Outfit Medium).
  The docs use copies in `docs/public/`; change them together.
- **docs.vuegram.com:** Markdown pages in `docs/guide/` (for people using VueGram) and `docs/developers/`. The sidebar,
  navigation and search are in `docs/.vitepress/config.mts`, the colours in `docs/.vitepress/theme/style.css`.
  Write for people who are not developers, except under `developers/`.
