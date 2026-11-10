# Relay: legal and policy review (multi-user cookie relay)

Not legal advice. Written 2026-11-09 from public sources, before any student
other than Owen uses the relay. Have a parent and ideally a lawyer (a law-school
clinic or the Student Press Law Center's referral line are free options) read it before
launch. Read this with [docs/LEGAL.md](../LEGAL.md), which covers the extension,
the iOS app and neo-plan.

## The short answer

**No-go as planned.** Three things block inviting other students, and none of
them is fixed by better code:

1. **Claude's consumer apps are 18+.** The relay only works through claude.ai
   or the Claude iOS app, and Anthropic's Consumer Terms require users to be 18.
   Inviting 14–17-year-olds means building a service whose only use breaks the
   terms of the product it plugs into.
2. **FUHSD's Responsible Use Policy and PowerSchool's Terms of Use both forbid
   sharing an account or its login with anyone.** A Schoology session cookie
   is the login. Each student who uploads one breaks both, and Owen is the
   person who asked them to. FUHSD and PowerSchool have already acted against a
   student-built Schoology tool in this district (Feb 2026, below).
3. **Owen would hold live keys to hundreds of minors' school accounts.** A
   Schoology session can read, post, message teachers, submit work and start
   quizzes. "Read-only" is a promise in the Worker's code, not a limit
   Schoology enforces. One leak of KV plus the key means someone else can act
   as every user.

What could make it a go: **written permission from FUHSD Technology Services**,
and **users limited to students who are 18**, plus the design changes in
"Design changes" below. Without FUHSD's OK, the safer path is the one Dewey
already has: everything stays on the student's own device (extension + local
MCP), or each student runs their own copy of the relay, so Owen holds nothing.

## What this review assumes

From the plan as described on 2026-11-09:

- A Cloudflare Worker (`relay/`) is a remote MCP server for claude.ai and the
  Claude iOS app. Sign-in goes through `workers-oauth-provider` with Google,
  limited to `fuhsd.org`, scopes `openid email` only.
- Per user, KV holds one AES-GCM-encrypted Schoology session cookie keyed by
  user ID, with an expiry. No Schoology data is stored. Each tool call fetches
  `fuhsd.schoology.com` live with that cookie, read-only, and never takes
  quizzes.
- The cookie is uploaded by the Dewey iOS app or the extension.
- Per-user revoke/delete is planned. Many FUHSD students are expected to use
  it, most of them minors.
- This reverses the rule in [FINDINGS.md:33](../ios/FINDINGS.md) ("Show it on
  screen only, never upload it"). If the relay ships, that line must change in
  the same commit.

Confidence labels: **High** = read the primary source or settled law.
**Medium** = primary source is unclear on this exact case, or I am relying on
well-known law I did not re-read today. **Low** = inference; check before relying
on it.

## Summary

| # | Issue | Risk | Confidence | What to do |
|---|---|---|---|---|
| 1 | Claude consumer apps are 18+ | Blocker for minors | High | **No-go** for under-18 users |
| 2 | FUHSD Responsible Use Policy bans sharing accounts/passwords | Blocker; school discipline for Owen and users | High (text), Medium (that a cookie counts) | **Get written FUHSD permission** |
| 3 | PowerSchool ToU: logins not shareable, no bots/scraping | High; takedown, account bans, legal letters | High | Permission from FUHSD (who licenses Schoology); stop on any notice |
| 4 | CFAA / Cal. Penal Code 502 after a stop notice | Low now, severe if ignored | Medium | **Hard rule: shut down on any stop request** |
| 5 | Holding minors' live sessions (security) | High | High | Design changes below |
| 6 | Google under-18 third-party-app block | May stop students signing in at all | High (setting exists), Low (FUHSD's config) | Test; FUHSD admin approval if blocked |
| 7 | Google OAuth app setup | Low | Medium | Production status, privacy policy, check `hd` |
| 8 | SOPIPA | Medium; likely applies | Medium | Comply in design + policy |
| 9 | Breach notification (Civ. Code 1798.82) | Medium | Medium | Breach plan below |
| 10 | FERPA | Low for Owen directly | High | Nothing legal; affects how FUHSD sees it |
| 11 | CCPA/CPRA | Does not apply | High | None |
| 12 | COPPA | Very low (high school) | High | Refuse under-13 |
| 13 | App Store / Chrome Web Store rules | Medium at review time | Medium | Disclose; be ready to show permission |

---

## 1. Anthropic: Claude's consumer apps are 18+

**Risk: blocker for minors.** Confidence: **High.**

Anthropic's Consumer Terms (effective Oct 8, 2025), section 2: "You must be at
least 18 years old or the minimum age required to consent to use the Services in
your location", whichever is higher. Anthropic's policy page says Claude.ai is not
offered to users under 18 and that it detects and offboards underage users.
Parental consent does not change this for consumer accounts.

The relay is useless without claude.ai or the Claude app, so inviting a
16-year-old means inviting them to break those terms. If Anthropic offboards
them, their Dewey connector goes too; if a parent or the school complains, "the
tool is built for a product my users aren't allowed to use" is a bad position.

Some third-party guides say school-provided Claude accounts (Claude for
Education) can be used under 18 when the school supervises. I could not confirm
that from Anthropic. FUHSD does not provide Claude accounts, so it doesn't
apply here anyway.

**Do:** limit the relay to students who say they are 18 or older, and say so on
the sign-in page and in the Terms. A Google sign-in doesn't tell you a user's
age, so this is a stated limit, not a check. Don't market it to underclassmen.

## 2. FUHSD Responsible Use Policy

**Risk: blocker; school discipline.** Confidence: **High** on the text,
**Medium** that the district will treat a session cookie as a password.

Source: *Fremont Union High School District Responsible Use Policy for
Students* (finalsite PDF; the file dates from 2018, so ask whether a newer one
exists). Students and parents sign it every year, and it calls the signed page
"a legally binding document."

Relevant clauses (section III):

- **1(a) Passwords:** "students shall not share passwords".
- **1(b) Sharing:** students shall not share personal accounts with other
  students, "with or without the permission of the other student."
- **1(c) Intrusion:** report it to an administrator if you think an account
  was compromised.
- **4(f):** attempts "(successful or not) to gain unauthorized access to
  district resources" are not allowed.
- **4(o):** anything that breaks "school policy, or any local, state or
  federal law" (this pulls in the PowerSchool terms and the laws below).
- **4(p):** no sharing your own or other students' personal information in
  any electronic medium. This covers address and phone; Schoology pages also
  show classmates' names, posts and group membership.
- **4(q):** applies on non-district networks too, so "it's my phone at home"
  doesn't take it out of scope.
- **10:** misuse can mean loss of access plus discipline under the Student
  Code of Conduct.

How it lands on the relay: a Schoology session cookie is the student's district
account login in another form. Uploading it to a service another student runs
means that student (Owen) can act as them. That looks like 1(a)/1(b) in
everything but name, and Owen is the one asking each student to do it. The
district does not need to win an argument about the word "password" to
discipline.

**Local precedent (Feb–Mar 2026).** *The Epitaph* (Homestead High) reported a
student-built "Schoology Scraper" that showed profiles of 10,000+ FUHSD students
and staff. After the paper told administrators, the district reported it to
Schoology, PowerSchool sent a takedown request to the host, and the host
deleted the site and the creator's account. PowerSchool cited "unauthorized use
of Schoology content and the potential for misuse of student information," and
the paper said its investigation of the creator was ongoing. The article
said the project "possibly infringed" the district's acceptable-use policy. The
relay is different (each student sees only their own data, by choice), but it
shows FUHSD and PowerSchool watch for this and move quickly.

**Do:** ask FUHSD Technology Services in writing before inviting anyone. Send a
one-page description: what is stored (encrypted session cookie, expiry), what
is never stored, read-only fetches, who can see what, how to revoke, and the
kill switch. Ask for a written yes, or for them to suggest a sanctioned route
(an approved app listing, or Schoology API keys). A no is the answer; don't
launch anyway.

## 3. PowerSchool (Schoology) Terms of Use

**Risk: high (takedown, session bans, legal letters).** Confidence: **High.**

Source: PowerSchool *General Terms of Use* (powerschool.com/terms, "Revised –
August 2024"). They cover "any PowerSchool Product", which includes Schoology,
and bind students as "Users".

- **Access and Security:** "Usernames and passwords are personal to the
  registered User and may not be shared or transferred with any other party."
  You are responsible for everything done under your login.
- **Prohibited Actions** (all users): you will not "extract data using automated
  processes such as bots and webcrawlers (scraping)" unless PowerSchool
  authorizes it in writing.
- **User Restrictions:** no reproducing, transmitting or distributing any part
  of the site beyond personal, non-commercial printing or downloading, without
  PowerSchool's written approval.

The relay is an automated process fetching Schoology pages with a login handed
to a third party (Owen's Worker). The extension and local MCP raise the same
scraping question, but there the student's own browser does the fetching and
no one else holds the login. The relay adds the sharing problem and puts
hundreds of sessions behind Cloudflare IP addresses, where PowerSchool's bot
protection is more likely to notice.

PowerSchool contracts with FUHSD, not with Owen. The practical risks are
PowerSchool asking FUHSD to act, revoking sessions, blocking the Worker, or a
takedown request to Cloudflare, as in the Epitaph case.

**Do:** treat FUHSD's written permission as the minimum. Only PowerSchool can
give "authorized in writing" for scraping; FUHSD can tell you whether it will
ask. Keep request volume low (see design changes).

## 4. Computer-access laws (CFAA, California Penal Code 502)

**Risk: low today, severe if a stop notice is ignored.** Confidence:
**Medium** (from well-known cases; I did not re-read them today).

- *Van Buren v. United States* (2021) narrowed the federal CFAA: breaking terms
  of use alone generally isn't "exceeding authorized access."
- *Facebook v. Power Ventures* (9th Cir. 2016) is the warning. Power Ventures
  logged into Facebook with users' own credentials and the users' permission.
  Once Facebook sent a cease-and-desist and blocked it, continuing was held to
  be access "without authorization" under the CFAA and Penal Code 502. User
  consent did not save it.

**Do:** written rule in the README and in your head: **if PowerSchool or FUHSD
asks you to stop, or blocks the Worker, turn the relay off the same day,
delete every stored cookie, and don't work around the block.** Build the kill
switch before launch.

## 5. Security: you would hold minors' live school sessions

**Risk: high.** Confidence: **High.**

This is where legal risk becomes real harm. A Schoology session cookie is not
read-only: whoever holds it can submit assignments, post in course discussions,
message teachers, start quizzes and change account settings, as that student,
until Schoology signs them out (about once a day; [FINDINGS.md](../ios/FINDINGS.md)).
Encryption with one Worker secret protects against a KV-only leak, not against
a leaked secret, a bad deploy, a compromised Cloudflare or GitHub account, or
a bug that serves user A's cookie to user B.

### Design changes (do these whatever FUHSD says)

1. **Make a KV dump useless on its own.** Keep the cookie inside the OAuth
   grant's `props`, which `workers-oauth-provider` stores encrypted with key
   material from the user's token, instead of in a separate KV value under a
   global key. Then the Worker can decrypt a cookie only while that user's
   token is presented. (Confidence Medium: check the library's README on how
   `props` are encrypted.)
2. **Short life.** Expire the stored cookie after 24 hours at most (Schoology
   signs users out about daily anyway). Never extend the cookie's expiry the
   way the iOS app does for its own store. Delete it as soon as Schoology
   returns the login page.
3. **Enforce read-only in the Worker.** GET only. Allowlist the exact Schoology
   paths the tools need. Refuse `/assessment`, `/quiz`, submission, messaging
   and settings paths. Don't follow redirects to other hosts.
4. **Log nothing sensitive.** No request URLs with IDs, no response bodies, no
   cookies, no emails in logs. Check Cloudflare Workers Logs/observability
   settings and `console.log` calls.
5. **Bind identity correctly.** Verify the Google ID token yourself:
   `hd == "fuhsd.org"` and `email_verified == true`. Don't trust the `hd` request
   parameter alone. Key storage by Google `sub`, not email.
6. **Revoke and delete** (planned): a "Disconnect Dewey" button in the app and
   extension, and a web page, that deletes the cookie and the grant at once.
   Also tell users that signing out of Schoology kills the stored session.
7. **Kill switch:** one Worker secret or KV flag that makes every tool return
   "relay paused" and deletes all stored cookies.
8. **Rate limit** per user and overall, so a broken client or a prompt loop
   can't hammer Schoology.
9. **Least people:** only Owen can deploy, 2FA on Cloudflare, GitHub and the
   Google Cloud project, and no shared Cloudflare API tokens with KV write.
10. **Lower-custody alternative:** a "deploy your own relay" button, where each
    student runs the Worker in their own Cloudflare account with their own
    cookie. Owen then holds nobody's session. The PowerSchool scraping and
    Anthropic age issues remain, but the account-sharing and custody issues
    mostly go away.

## 6. Google Workspace: under-18 users and third-party apps

**Risk: students may simply be unable to sign in.** Confidence: **High** that the
setting exists, **Low** on how FUHSD has it set.

Google Workspace for Education blocks users that the admin designates as under
18 from "unconfigured third-party apps"; they see a message offering to request
access (Google Admin Help, "Manage access to unconfigured third-party apps for
users designated as under 18"; enforced since Oct 2023). The admin can mark an
app Trusted, Limited, Specific Google data, or Blocked. There is one exception
admins can turn on: "Allow users to access third-party apps that only request
basic info needed for Sign in with Google" (name, email, profile picture). With
that on, an `openid email` app needs no approval. With it off, every
under-18 student needs a FUHSD admin to configure the relay's OAuth client.
Google's page also says the organization (FUHSD) is responsible for parental
consent where the law requires it before allowing third-party apps.

**Do:** check before inviting anyone. If your own `fuhsd.org` sign-in to the
feasibility Worker worked and your account is designated under 18, the
basic-info option is on. If you are 18 or your designation is unknown, you
can't tell from your own login. Ask FUHSD as part of item 2. Since issue 1
limits the relay to 18+ anyway, this mostly matters if FUHSD later approves it
for everyone.

## 7. Google OAuth app (External, `fuhsd.org` only, `openid email`)

**Risk: low.** Confidence: **Medium.**

- **Internal vs External.** "Internal" requires the Google Cloud project to belong
  to FUHSD's Workspace organization. A project under Owen's personal account
  has to be External. Domain limiting is your own code's job (check `hd`, design change 5 under issue 5).
- **Testing status** caps the app at 100 test users and shows the unverified
  screen; refresh tokens expire after 7 days (Medium; from Google's
  "When is verification not needed" page and well-known behavior). Fine for a
  pilot, not for a school.
- **Production with only `openid email`.** These are non-sensitive scopes, so no
  security assessment or demo video. Google now expects *brand verification*
  (homepage, privacy policy on a verified domain, accurate app name/logo,
  contact email) for apps that show a name and logo on the consent screen.
  Use `dewey.owen64.me` (already verified for the site, presumably) for the
  homepage and policy links.
- **Google API Services User Data Policy** applies even unverified: say what
  you do with the email/`sub`, use it only for sign-in, and say so in the
  privacy policy.
- Google Cloud and Cloudflare accounts need a holder who can agree to their
  terms. If Owen is under 18, check whether those accounts should be in a
  parent's name (Low: check each provider's terms).

## 8. California SOPIPA (Bus. & Prof. Code 22584)

**Risk: medium; probably applies.** Confidence: **Medium.**

SOPIPA covers any "operator" of a site, service or app that knows it is "used
primarily for K–12 school purposes and was designed and marketed for" them
(22584(a)(7)). No contract with the school and no size threshold are needed. A
Schoology connector built for FUHSD students' coursework likely fits. "K–12 school
purposes" means things that "customarily take place at the direction of the
K–12 school, teacher, or local educational agency", including home
instruction. The covered information includes anything that identifies a
student and their school records.

What it requires, and where the relay stands:

| SOPIPA | Relay |
|---|---|
| (b)(1) No targeted advertising | None. Keep it that way. |
| (b)(2) No profile of a pupil except for K–12 purposes | No stored Schoology data. Don't add analytics keyed to users. |
| (b)(3) No selling | None. |
| (b)(4) No disclosure except listed cases. Service providers only under a contract limiting use, banning onward disclosure and requiring security | Cloudflare (hosting) and Google (sign-in) are service providers. Their standard terms and DPA are the contract; link them in the policy. Data sent to Claude happens at the student's own request inside their own Claude account. Say so plainly. |
| (d)(1) Reasonable security | Item 5. |
| (d)(2) Delete when the school or district asks | Honor any FUHSD deletion request at once. The kill switch covers it. |

**Do:** write the privacy policy to these rows, and keep the "no stored
Schoology data" fact true.

## 9. California breach law and reasonable security (Civ. Code 1798.81.5, 1798.82)

**Risk: medium.** Confidence: **Medium.**

The breach statute's "personal information" includes "a username or email
address, in combination with a password or security question and answer that
would permit access to an online account." A session cookie isn't literally a
password. A cookie stored next to the user's email or Google `sub` does give
access to an account, though, and a court or the AG could read it that way. It
applies to a "person or business that conducts business in California." Whether
a free student project counts is unclear. Treat it as covering you: it costs
little, and the breach plan below meets it.

## 10. FERPA

**Risk: low for Owen directly.** Confidence: **High.**

FERPA (20 U.S.C. 1232g; 34 CFR Part 99) restricts what *schools that receive
federal education funds* disclose from education records. Owen is not one, and a
student opening their own records through their own session is not a school
disclosure. FERPA rights belong to parents until the student is 18 or enters
college, but that limits the school, not the student.

Where it still matters: (a) Schoology pages show other students' names, posts
and group membership, and those records leave Schoology through the relay into
Claude; (b) FUHSD will see the relay through a FERPA lens, because it is
responsible for where student records go and the relay is outside its vendor
contracts. If FUHSD ever formally adopts the relay, it would need a written
agreement under California Education Code 49073.1 and FERPA's school-official
rules. That is FUHSD's paperwork, and it would be a large ask of a student
project.

**Do:** nothing legally required. In the FUHSD request, say that the relay stores
no records and fetches only what the student's own session can see.

## 11. CCPA / CPRA

**Does not apply.** Confidence: **High.**

The CCPA covers for-profit "businesses" with more than $26,625,000 in annual
revenue (2025–2026 figure), or that buy, sell or share the personal information of
100,000+ consumers or households, or that make half their revenue from selling or
sharing it (Civ. Code 1798.140(d); CPPA threshold table). A free, non-profit
student project meets none. California's Age-Appropriate Design Code is
tied to the same "business" definition and has been largely blocked in court.

CalOPPA (Bus. & Prof. Code 22575) requires a posted privacy policy for commercial
online services that collect personal information from Californians. Whether a free
service is "commercial" is arguable. Dewey already has a policy; extend it.

## 12. COPPA

**Risk: very low.** Confidence: **High.**

COPPA (15 U.S.C. 6501; 16 CFR Part 312, amended 2025, compliance date April
2026) covers services directed to children under 13 or with actual knowledge
that a user is under 13. High school students are almost all 13 or older, and
issue 1 limits the relay to 18+ anyway. If you ever learn a user is under 13,
delete their data and block the account.

## 13. Store and platform rules

**Risk: medium at review time.** Confidence: **Medium.**

- **App Store guideline 5.2.2:** an app that accesses or displays a
  third-party service's content must be "specifically permitted to do so under
  the service's terms of use." Showing a student their own data on the device
  is defensible. Uploading their Schoology session to your server is harder to
  defend against PowerSchool's ToU. App Privacy labels become "Data Linked to
  You" (email, user ID, and the session credential), not "Data Not Collected"
  as [docs/LEGAL.md](../LEGAL.md) says today.
- **Chrome Web Store:** reading and sending a cookie off the device is handling
  "authentication information." Disclose it on the Privacy practices tab and in
  the policy. Justify the `cookies` permission. Send it only on a clear user
  action, never by default.
- Both stores can pull the app if PowerSchool complains.

---

## What the privacy policy must say (relay section)

Add a "Claude relay" section to `site/src/privacy.html` in the same change as
the code (the rule in [docs/LEGAL.md](../LEGAL.md)):

- **Who runs it:** Owen, a FUHSD student; not FUHSD, PowerSchool, Google or
  Anthropic, and not endorsed by them. Contact: `legal@owen64.me`.
- **Who may use it:** FUHSD students who are 18 or older (issue 1).
- **What is stored:** Google account ID and email (sign-in), one encrypted
  Schoology session cookie, its expiry, and OAuth grant records. Nothing else.
- **What is not stored:** grades, assignments, files, messages or any page
  content. Pages are fetched live and passed straight to the user's own Claude
  conversation.
- **What the cookie can do,** in plain words: it is your Schoology login for up
  to a day. Dewey uses it only to read; here is how that is enforced.
- **Where it goes:** Cloudflare (hosting, US/global edge), Google (sign-in),
  and Anthropic, but only what the student asks Claude for, under the
  student's own Anthropic account and Anthropic's privacy policy.
- **How long:** at most 24 hours, or until sign-out, disconnect or Schoology
  ending the session.
- **SOPIPA promises:** no ads, no selling, no profiles, no other use.
- **Deleting:** how to disconnect, what it deletes, and that deletion takes
  effect at once. Also: we delete on FUHSD's request.
- **Breach:** we will tell you, and what you should do.
- **Changes:** dated policy, users notified of material changes.

## Data-deletion flow

1. In the Dewey app, extension and a web page: **Disconnect Claude relay** → deletes
   the stored cookie and revokes the OAuth grant at once. Show "Deleted."
2. Revoking the connector in claude.ai should revoke the grant; the cookie dies
   with it (design change 1) or is deleted by a revocation hook.
3. Automatic: cookie deleted at 24 h, or as soon as Schoology returns the login page.
4. By email to `legal@owen64.me`: delete within 7 days, confirm by reply.
5. FUHSD request or kill switch: delete everyone's cookies at once.
6. Nothing to delete in backups. KV is the only store; don't add others.

## Breach plan

Trigger: any sign that a stored cookie, the encryption key, a Cloudflare/GitHub/
Google Cloud credential, or the Worker's code was exposed, or that one user got
another's data.

1. **Within the hour:** flip the kill switch (deletes every cookie, stops all
   fetches). Rotate Worker secrets and Cloudflare API tokens. Deleting the
   cookies doesn't end the Schoology sessions; only signing out does.
2. **Same day:** tell every affected student to **sign out of Schoology
   everywhere** (it ends the stolen session) and to check their recent
   submissions, posts and messages. Tell FUHSD Technology Services (the RUP's
   1(c) asks students to report compromised accounts to an administrator).
3. **Work out** what was exposed, whose, from when to when. Keep notes.
4. **Written notice** to each affected user without unreasonable delay
   (1798.82): what happened, what data, what you did, what they should do.
   If more than 500 Californians are affected, send a sample notice to the
   California Attorney General.
5. **Tell parents** of any affected minor (if minors ever use it), through the
   student or the school.
6. **Write up** what changed, and don't restart without FUHSD's OK.

## Is parental consent needed?

- **By law:** no, for students 13+, with this design. COPPA stops at 13. FERPA
  and SOPIPA don't require parental consent from a non-school operator.
- **In practice:** the question goes away if the relay is 18+ (issue 1). If FUHSD
  ever approves it for minors, FUHSD decides the consent process (Google puts
  that duty on the school for under-18 app access). A short parent notice would
  be the minimum.
- **Owen's own parent:** yes. They should know you are running a service that
  holds other students' school logins, and may need to hold the provider
  accounts (issue 7).

---

## Before inviting any other student (ranked)

1. **Get FUHSD Technology Services' written OK** for the relay as designed.
   No yes, no launch. (Issues 2, 3, 6, 10.)
2. **Limit it to students 18+,** stated at sign-in and in the Terms, as long as
   it runs through consumer Claude. (Issue 1.)
3. **Build the kill switch, and commit to stopping** the same day if FUHSD or
   PowerSchool objects or blocks it. (Issue 4.)
4. **Ship the security changes:** per-token encryption, 24 h expiry, GET-only
   path allowlist with quizzes blocked, no sensitive logs, `hd` and
   `email_verified` checks, rate limits, 2FA everywhere. (Issue 5.)
5. **Ship disconnect/delete** in the app, the extension and a web page. (Deletion flow.)
6. **Publish the privacy policy relay section** and Terms changes, and update
   [FINDINGS.md:33](../ios/FINDINGS.md) and [docs/LEGAL.md](../LEGAL.md) in the
   same change. (Policy section, issues 8, 13.)
7. **Write the breach plan** into `docs/LEGAL.md` and test the kill switch once. (Breach plan.)
8. **Tell a parent**, and have them (and ideally a lawyer) read this. (Parental consent.)
9. **Move the Google OAuth app to Production** with brand verification, and confirm
   a FUHSD student account can sign in. (Issues 6, 7.)

If FUHSD says no, keep the relay single-user (Owen only), or offer only the
"deploy your own" version, and keep the extension + local MCP as the
multi-student path.

## Sources

- Anthropic, Consumer Terms of Service (effective Oct 8, 2025), §2 Minimum age: <https://www.anthropic.com/legal/consumer-terms>
- Anthropic, AI policy page (Claude.ai not offered under 18): <https://www.anthropic.com/policy>
- FUHSD, Responsible Use Policy for Students (PDF, file dated 2018): <https://resources.finalsite.net/images/v1530641392/fuhsdorg/psmlebkxlktxdpfyz5pk/TechnologyUseAgreement.pdf>
- FUHSD, 1:1 Device Program (refers students to the Technology Use Agreement): <https://www.fuhsd.org/departments/technology-services/one-to-one-device-program>
- *The Epitaph* via Best of SNO, "Schoology Scraper exploits digital loophole to display FUHSD profiles" (Mar 2026): <https://bestofsno.com/85798/news/the-epitaph-investigates-schoology-scraper-exploits-digital-loophole-to-display-fuhsd-profiles/>
- PowerSchool, General Terms of Use (Revised August 2024): <https://www.powerschool.com/terms/>
- Google Workspace Admin Help, "Manage access to unconfigured third-party apps for users designated as under 18": <https://support.google.com/a/answer/13288950>
- Google Cloud Help, OAuth app verification and "When is verification not needed": <https://support.google.com/cloud/answer/13464321>, <https://support.google.com/cloud/answer/13464323>
- Cal. Bus. & Prof. Code 22584 (SOPIPA): <https://california.public.law/codes/business_and_professions_code_section_22584>
- CPPA, Updated monetary thresholds in the CCPA: <https://www.cppa.ca.gov/regulations/cpi_adjustment.html>
- Cal. Civ. Code 1798.82 (breach notice), 1798.81.5 (reasonable security), 1798.140 (CCPA definitions); Cal. Educ. Code 49073.1; Cal. Penal Code 502: leginfo.legislature.ca.gov
- FERPA, 20 U.S.C. 1232g and 34 CFR Part 99; COPPA, 15 U.S.C. 6501 and 16 CFR Part 312
- *Van Buren v. United States*, 593 U.S. 374 (2021); *Facebook, Inc. v. Power Ventures, Inc.*, 844 F.3d 1058 (9th Cir. 2016)
- Apple App Store Review Guidelines 5.1.1, 5.2.2; Chrome Web Store Program Policies (user data)
