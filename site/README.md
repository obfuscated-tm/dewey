# site/

dewey.owen64.me, deployed as the Cloudflare Worker `dewey-site` (static assets only).

- `src/index.html`, `src/style.css`, `src/site.js`, `src/404.html`: the landing page
- `src/privacy.html`: Privacy, served at `/privacy`
- `src/terms.html`: Terms of Use, served at `/terms`
- `build.sh`: builds everything into `dist/`, including brand files and the latest release downloads

From `site/`: `npm run build`, `npm run preview` (local), or `npm run deploy`.

What the Privacy and Terms pages say, and the rule for keeping them true, is in [docs/LEGAL.md](../docs/LEGAL.md).
