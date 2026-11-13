# Qupa

A party quiz for Google TV and Android TV, where the phones are the buzzers.
Open Qupa on the TV, scan the QR code with a phone, type a nickname and play.
Nobody installs an app and nobody makes an account. The TV serves the game on
your home network, so nothing goes through the internet. No phones to hand? Two
to four teams can play on the remote alone.

Version 0.1.0 (1) went to Google Play for production review on 2026-11-01
([docs/PLAY-STORE.md](docs/PLAY-STORE.md), section 7). The docs record no outcome yet.
Until it is live, build and install it with the steps below.

## Quick start

```sh
git clone https://github.com/zaitsew/qupa && cd qupa
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties   # your Android SDK path
./gradlew :app:assembleDebug                                   # needs JDK 17 or newer
adb connect <tv-ip>:5555                                       # TV: Settings > Developer options > Network debugging
adb install -r app/build/outputs/apk/debug/app-debug.apk       # then open Qupa from the TV launcher
```

## How it works

- **Phones.** "Play with phones" starts a Ktor (CIO) web server on the TV, port
  8080 or the next free one, and shows a QR code for `http://<tv-ip>:<port>/?r=<room>`.
  Every request needs the four-digit room code. The page is one self-contained
  HTML file (`app/src/main/assets/web/index.html`, no CDN) in English, Spanish
  or Russian by the phone's language. Players connect by WebSocket, up to 8, and
  can rejoin after a dropped connection. The server runs only while a lobby or a
  game is open.
- **Game.** 10, 15 or 20 questions, answer time 10 to 30 s, categories and
  difficulty of your choice. A correct answer scores 1,000 times the share of
  time left and never less than 500. Every question ends with the answer, a
  scoreboard, and after the last one a podium. "Play again" keeps the players.
- **Remote mode.** Two to four named teams take turns, one question each. The
  team in turn picks an answer with the D-pad and OK. A correct answer scores
  a flat 1,000.
- **Look and sound.** One dark party palette, made for a living-room TV.
  Synthesized sound effects are on by default. Settings has a switch for them,
  next to the default game setup.
- **Questions.** 5,272 questions in 24 categories from the Open Trivia Database,
  filtered by `tools/fetch-opentdb.py` and shipped in `app/src/main/assets/questions/en.json`.
  The app prefers questions you have not seen; it keeps only their ids, on the TV.
  Questions are in English for now; `questions/<lang>.json` for a locale is used
  when present (Spanish and Russian packs are on the [roadmap](docs/ROADMAP.md)).

Questions from the Open Trivia Database (opentdb.com), CC BY-SA 4.0.

## Privacy

No analytics, no ads, no crash-reporting SDK, no accounts and no outgoing
network calls. The permissions are `INTERNET` (to open the local server port)
and `ACCESS_NETWORK_STATE` (to find the TV's address). Nicknames stay in the
TV's memory during a game. The game page is plain HTTP on your private network.
See [docs/privacy.md](docs/privacy.md).

## Develop

```sh
./check.sh                              # unit tests + lint + debug build (the gate)
git config core.hooksPath .githooks     # run ./check.sh before every push
./check.sh usage                        # prints telemetry=none
./gradlew :core:devServer               # the game server on your laptop, with bot players
```

- `core/` is pure Kotlin with no Android: the game engine (an injected clock, a
  synchronous state machine), the wire protocol, the question bank, the server
  and the sound synthesizer. Its tests run on the JVM, including a real WebSocket
  round trip.
- `app/` is the Android TV app: Compose for TV screens, DataStore, the QR code, sound.
- `./gradlew :core:devServer` serves the phone page at `http://localhost:8080/?r=1234`
  with a few bot players, so the page can be tried in a desktop browser.
- Strings are in `app/src/main/res/values` (English), `values-es` and `values-ru`.
- `tools/fetch-opentdb.py` rebuilds the question bank (it respects the API's
  rate limit and takes several minutes). `tools/make-banner.swift` redraws the
  store graphics (macOS).

### Release build

`./gradlew :app:bundleRelease` builds a shrunk (R8) Android App Bundle in
`app/build/outputs/bundle/release/`. It is signed when
`~/.qupa/android-signing.properties` exists (`storeFile`, `storePassword`,
`keyAlias`, `keyPassword`), and unsigned otherwise. Keys are never committed.
The Play Console answers are in [docs/PLAY-STORE.md](docs/PLAY-STORE.md).

## License

MIT, see [LICENSE](LICENSE). Copyright Fabrique Futur. The questions are
CC BY-SA 4.0 (Open Trivia Database).

<!-- teyla:reviewed 2026-11-13 -->
