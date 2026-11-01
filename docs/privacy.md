# Qupa privacy policy

Last updated: 1 November 2026

Qupa is a party quiz for Android TV. It is open source:
<https://github.com/zaitsew/qupa>.

## What data Qupa collects

None. Qupa has no accounts, no analytics, no advertising and no crash-reporting
service. The developer receives no data from the app. The app makes no outgoing
connection to the internet.

## What happens on your home network

When you choose "Play with phones", the TV starts a small web server on your
home network and shows a QR code. A phone that scans it opens a web page served
by the TV and connects back to the TV. Only devices on the same network can do
this, and only with the four-digit room code shown on the TV.

- The phone sends the nickname the player typed and the answers they tap. These
  stay in the TV's memory while the game runs and are deleted when the game ends
  or the app closes.
- The phone's browser keeps a random player id in its own storage so a player can
  rejoin after a dropped connection. It never leaves the phone and the TV.
- The page is served over plain HTTP on your local network, as a TV cannot get a
  certificate for a private address. Anyone who is already on your Wi-Fi and
  knows the room code could see what players on it send. The server runs only
  while a lobby or game is open and stops when you leave.

## What stays on your TV

- **Settings:** sound on or off, and your default game setup (number of questions,
  answer time, difficulty, categories).
- **Seen questions:** the ids of questions already asked, so new games prefer
  fresh ones. The ids carry no personal information.

Clearing the app's storage in the Android TV settings, or uninstalling Qupa,
deletes both.

## Permissions

- `INTERNET`: Android requires it for any app that opens a network port. Qupa uses
  it only to serve the game on your local network.
- `ACCESS_NETWORK_STATE`: to find the TV's address on your Wi-Fi or Ethernet.

## Questions

Questions come from the [Open Trivia Database](https://opentdb.com), licensed
CC BY-SA 4.0. They ship inside the app; Qupa does not fetch questions at run time.

## Children

Qupa does not collect personal information from anyone, including children.

## Changes

If this policy changes, the new version is published at this address with a new date.

## Contact

Open an issue at <https://github.com/zaitsew/qupa/issues>.
