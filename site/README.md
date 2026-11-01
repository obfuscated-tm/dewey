# site/

dewey.owen64.me, deployed as the Cloudflare Worker `dewey-site` (static assets only).

- `src/index.html`: the landing page (the "Green cover" design), with its own CSS and JS inline
- `src/web.html`: where the home page's "Continue on the web" goes, served at `/web`: a live mockup of the web app and a locked Sign in until Schoology approves OAuth (delete it and that button if that never happens). Both pages slide between each other with cross-document view transitions
- `src/img/app/`: the app screenshots it shows
- `src/style.css`, `src/site.js`, `src/404.html`: shared by the Privacy, Terms and 404 pages
- `src/privacy.html`: Privacy, served at `/privacy`
- `src/terms.html`: Terms of Use, served at `/terms`
- `drafts/`: earlier design drafts, not deployed
- `build.sh`: builds everything into `dist/`, including brand files and the latest release downloads

From `site/`: `npm run build`, `npm run preview` (local), or `npm run deploy`.

What the Privacy and Terms pages say, and the rule for keeping them true, is in [docs/LEGAL.md](../docs/LEGAL.md).
