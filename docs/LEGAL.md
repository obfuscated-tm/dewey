# Legal: policies, store forms, and what's left

Not legal advice. Written from the code on 2026-10-31. Have a parent and ideally a
lawyer read it before a store launch or before taking money.

## Where the policies live

| Policy | Source | Live at |
|---|---|---|
| Dewey Privacy | [`site/privacy.html`](../site/privacy.html) | dewey.owen64.me/privacy |
| Dewey Terms | [`site/terms.html`](../site/terms.html) | dewey.owen64.me/terms |
| neo-plan Privacy | `src/app/(legal)/privacy/page.tsx` in neo-plan | neo-plan.vercel.app/privacy |
| neo-plan Terms | `src/app/(legal)/terms/page.tsx` in neo-plan | neo-plan.vercel.app/terms |

Both Terms follow [Basecamp's open-source policies](https://github.com/basecamp/policies)
(CC BY 4.0). The credit line at the foot of each page is required by that license;
keep it.

**The rule: a policy changes in the same change as the code.** If Dewey starts
sending something new off the device, or neo-plan stores a new thing about a
person or uses a new service, the policy is edited in that same commit, with a
new date at the top.

## Facts the policies rely on

If one of these stops being true, the policies are wrong.

- Dewey has no server. Everything it reads stays on the device, except what the
  two optional connections send.
- To neo-plan, Dewey sends course names and section IDs; assignment and event
  titles, links, IDs and due dates; submitted and late; the gradebook's
  **Missing** flag only (`extension/sync/sync.js`, `gradebook = { missing }`);
  assignment description text; and the user's own changes. **No scores.**
  neo-plan's parser also keeps only `missing` (`src/lib/enrich.ts` in neo-plan).
- The Claude add-on runs locally, gets data from the extension over
  127.0.0.1, and fetches public course pages itself (`mcp/src/fetchpage.js`).
- neo-plan stores accounts (email, password hash, and for Google sign-in a name and
  picture link), everything the user enters, the Schoology feed URL (never read back
  by the browser), hashed extension tokens, and import records, which are pruned
  after 30 days. It runs on Supabase (us-west-1) and Vercel, with no analytics.
- neo-plan's Delete account removes every row (all `user_id`s cascade).
- Dewey is all rights reserved (`LICENSE`): the code is public to read, not to
  copy. Releases before 2026-10-31 went out under MIT, and those copies stay MIT.

## Deploying the Dewey pages

The site isn't in this repo yet, so copy `site/privacy.html` and `site/terms.html`
over the deployed files and redeploy the `dewey-site` Worker. neo-plan's pages
ship when [obfuscated-tm/neo-plan#4](https://github.com/obfuscated-tm/neo-plan/pull/4)
merges. Until then, Dewey's links to neo-plan's policies 404, so deploy both
together.

## Store privacy forms

**Chrome Web Store** (Privacy practices tab):
- Single purpose: "Shows a student's own Schoology courses, grades and assignments
  in a clearer way, and saves their classes for studying."
- Data usage: without neo-plan, nothing leaves the device, so nothing is
  collected. Because the optional neo-plan sync sends Schoology data off the
  device, the safe answer is to tick **Website content**, and say it's sent only
  to neo-plan, only after the user connects it.
- Tick all three certifications: no selling, no unrelated use, no creditworthiness use.
- Privacy policy URL: `https://dewey.owen64.me/privacy`.
- Justify each permission and host (`downloads`, `scripting`, `offscreen`,
  `*.schoology.com`, Google Docs/Drive, `neo-plan.vercel.app`) in one line each.
  Remove `http://localhost:3000/*` from the store build.

**App Store** (App Privacy):
- "Data Not Collected". Apple counts only data sent off the device to you or third
  parties. If the neo-plan connection ships in the iOS app, check that answer again.
  A user-initiated transfer to a service they log into may still need declaring.
- Guideline 5.2.2: be ready to explain that Dewey only shows each student their
  own data, read-only, with their own login.

**Google Play** (Data safety): no data collected or shared. Data is encrypted in
transit (HTTPS to Schoology). Users can delete data by uninstalling. The form is
required even when nothing is collected.

## If neo-plan's data is ever exposed

California law (Civ. Code 1798.82) requires telling affected users. An email
address plus a password counts.

1. Stop the leak: rotate the Supabase service-role key and `CRON_SECRET`, revoke
   extension tokens if needed, and force sign-out.
2. Work out what was exposed, whose data, and when.
3. Email each affected user without unreasonable delay. Say what happened, what
   data was involved, what you did, and what they should do (change their password;
   in Schoology, reset the calendar feed link).
4. If more than 500 Californians are affected, also send a sample notice to the
   California Attorney General.
5. Write down what happened and what changed.

## Still to do

- [ ] Turn on leaked-password protection in Supabase Auth, and move `citext` out
      of `public` (both Supabase advisor warnings).
- [ ] Send a test email to legal@owen64.me to confirm it receives mail.
- [ ] Search USPTO for OCLC's DEWEY marks before any store listing. Consider
      toning down the Dewey Decimal framing (the "340 Law" call numbers).
- [ ] Read FUHSD's acceptable-use policy.
- [ ] Add a licenses notice for bundled open-source code in the iOS and Android
      apps.
- [ ] Have a parent or lawyer read all four pages before launch.
