# Google Play: what to paste

Everything Play Console asks for before Qupa's first release, in the order it
asks. Written for **0.1.0 (1)**. The answers come from the code: the manifest
asks for `INTERNET` and `ACCESS_NETWORK_STATE` only, and the app has no SDK
besides AndroidX, Compose for TV, DataStore, zxing (QR drawing) and Ktor (the
local web server).

## 1. Account, package, signing

- Account: the **Fabrique Futur** organisation account. It is exempt from the
  12-testers-for-14-days rule of personal accounts, so the order is internal
  testing, then production.
- Package `com.fabriquefutur.qupa` (permanent).
- **Upload key** `~/.qupa/android-upload.jks`, passwords in
  `~/.qupa/android-signing.properties` (keys `storeFile`, `storePassword`,
  `keyAlias`, `keyPassword`). Both live outside the repo. Play App Signing
  re-signs with Google's key; a lost upload key is reset in Play Console, App
  integrity, "Request upload key reset".
- Build: `./gradlew :app:bundleRelease`, output
  `app/build/outputs/bundle/release/app-release.aab`. `versionCode` rises with
  every upload.
- Upload: `~/ops/bin/play upload --package com.fabriquefutur.qupa --aab <aab> --track internal`.
  The service account needs "Release to testing tracks" (and "Manage store
  presence" for listing images) on this app. While the app is a draft, a listing
  commit also needs "Edit and delete draft apps".

## 2. Create app

| field | value |
|---|---|
| App name | `Qupa: Party Quiz for TV` |
| Default language | English (United States), `en-US` |
| App or game | Game |
| Free or paid | Free (permanent once published) |

Category: **Trivia** (under Games). Tags: Trivia, Party, Multiplayer.

## 3. Store listing

Languages: `en-US` (default), `es-ES`, `ru-RU`. The screens speak the same three;
the questions are English in 0.1.0, and every description says so.
Website: `https://zaitsew.github.io/qupa/`. Contact email: the Fabrique Futur
support address.

### Texts

One folder per language in [`docs/play/listing/`](play/listing/), fastlane-supply
layout: `title.txt` (at most 30 characters), `short_description.txt` (at most 80),
`full_description.txt` (at most 4000). Those files are the source of truth. The
full description carries the Open Trivia Database attribution (CC BY-SA 4.0).
No model-provider names appear anywhere in the listing.

### Graphics

All in `docs/play/`, drawn by `swift tools/make-banner.swift`.

| asset | size | file |
|---|---|---|
| App icon | 512 x 512 PNG | `icon-512.png` |
| Feature graphic | 1024 x 500 | `feature-graphic-1024x500.png` |
| Android TV banner | 1280 x 720 | `tv-banner-1280x720.png` |
| TV screenshots | 1920 x 1080, 1 to 8 per language | `screenshots/<locale>/` |

Screenshots are taken on a TV or TV emulator after the build is installed; they
are not drawn by a script. Suggested set: home, phone lobby with the QR code,
a question, the reveal, the scoreboard, the podium, the team lobby. Play still
requires 2 to 8 phone screenshots on the default listing even for a TV-only app;
`tools/play-listing.sh` uploads the same 16:9 TV screenshots into that slot.

Upload texts and graphics for every language with `tools/play-listing.sh` (add
`--dry-run` first).

## 4. Form factors

Console path `/app/4972483586312634084/advanced-distribution?tab=releaseTypes` (`advanced-settings` redirects home), **Add form factor**, Android TV, then opt in. The TV listing
uses the TV banner and TV screenshots. Remove "Phone" if Play added it: the
manifest requires leanback, so phones cannot install it. Play reviews TV apps
against the TV app quality guidelines: D-pad navigation everywhere, no
touch-only controls, a banner, readable text at TV distance. Note for the
reviewer: phones are not needed to test the app, since "Play with the remote"
runs a full game from the remote alone.

## 5. App content

| section | answer |
|---|---|
| Privacy policy | `https://zaitsew.github.io/qupa/privacy` |
| Sign in details | **Yes**, because the form's Yes list names QR codes and actions on another device. No account or payment exists. The entry tells the reviewer that "Play with the remote" runs a full game with no phone. Google's partner-device testing toggle is off |
| Ads | No, the app contains no ads |
| Content rating | Category **Game**. The questions ask about text references too, and the bank has them, so the answers are Yes as text only. Violence against humans: realistic setting, referred to, no blood. Suggestive references (song and album titles). Gambling themes, not prominent. Minor profanity, rarely. References to illegal drugs, medical drugs, alcohol and tobacco, rarely. Fear, crude humor, digital purchases, location, Nazi symbols, terrorism, crime techniques: No. "Users can interact": **Yes**, because nicknames show on the shared screen. Block, report and moderation: No, the app has none. Limited to invited friends: Yes, joining needs the room code on the TV. Result on 2026-11-01: PEGI 12, USK 12, ESRB Teen, ClassInd 14+, GRAC 15+, ACB Mature, Gmedia 16, DGSC Restricted, IARC generic 12+ |
| Target audience | 18 and over (keeps the app out of the Families program) |
| News app | No |
| Data safety | See below |
| Government app | No |
| Financial features | None |
| Health | None |
| Advertising ID | No |
| Generative AI features | None. No AI-generated content in the app |

### Data safety

- Does your app collect or share any of the required user data types? **No.**
- Is all user data encrypted in transit? Not asked once the answer above is No.
- Account deletion: not applicable (no accounts).

Why "No": nicknames and answers go from a phone to the TV over the player's own
home network and are held in the TV's memory for the length of a game. Nothing
is sent off the device or to the developer, and the app makes no outgoing
connection. Settings and seen-question ids stay in the TV's storage. The local
page is plain HTTP on the private network; the privacy policy says so.

### Permission declarations

`INTERNET` is needed to bind the local web server socket; there is no outgoing
traffic. No sensitive permission, no foreground service, no background location.

## 6. Release order

1. Internal testing track, tester list with the owner's Google account (the one
   signed in on the TV).
2. The owner opens the opt-in link once, then installs from the TV's Play Store.
3. Production after a day of use without a blocking problem. Play reviews the
   first production release and the TV form factor (allow up to 7 days).

### Release notes

Every release after 0.1.0 ships its own notes. Put one file per listing language in
`docs/whats-new/<version>/` (`en-US.txt`, `es-ES.txt`, `ru-RU.txt`, at most 500 characters each)
and pass `--notes-dir docs/whats-new/<version>/` to `~/ops/bin/play`. Write one line per change a
player would notice, from the PRs merged since the last release. Never use a generic line such as
"Bug fixes and improvements" (POLICY section 15). `tools/play-listing.sh` uploads listing text and
graphics only, not release notes.

## 7. Submission history

| date | version | state |
|---|---|---|
| 2026-11-01 | 0.1.0 (1) | first production release, sent for review, full rollout |

Decisions at submission: Google Play Games on PC was opted out, because Play added it automatically to the game and the manifest requires leanback, so it cannot run on a PC. It can be reversed in the form factors page. Tags are Party games and Trivia; Play has no Multiplayer tag.
