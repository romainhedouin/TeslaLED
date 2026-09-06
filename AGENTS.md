# AGENTS.md

Context for AI coding agents working on this repo. See `README.md` for the
project's purpose and architecture first.

## Scope of this repo

This repo has the Android client only (`app/`). The Pi-side receiver used to
be duplicated here under `pi/`, but that was a stale fork of what's now
maintained at
[romainhedouin/tesla-panel](https://github.com/romainhedouin/tesla-panel)
(`tesla/` directory there) — it's been removed from here to avoid two copies
silently drifting apart. Treat `tesla-panel`'s `tesla/` as authoritative for
anything Pi-side; this repo and that one deploy completely separately
(one's an APK, the other's copied by hand onto the Pi's home directory and
run via systemd) and nothing in the build connects them — a protocol change
requires manually redeploying `bt_server.py` to the actual Pi regardless of
which repo you edited it in.

## The wire protocol (see tesla-panel's `tesla/bt_server.py` for the authoritative receiver side)

`[1 byte command type][4 bytes big-endian payload length][payload]`, then a
single-byte status response (`0` = OK, anything else = error).
`BluetoothClient.sendCommand()` builds and writes that header + payload in
one shot; `bt_server.py`'s `handle_one_command()` reads exactly
`HEADER_SIZE` bytes then exactly `length` more via a `recv_exact()` loop, and
dispatches on the command type (`COMMAND_IMAGE` / `COMMAND_KILL` /
`COMMAND_SET_BRIGHTNESS`, defined identically on both sides — keep the
constants in sync if you ever add one). `COMMAND_SET_BRIGHTNESS`'s payload is
a single byte, 1-100; `MessageSender` sends it before every message's first
frame rather than tracking whether the Pi's already up to date, since it's
cheap and keeps the client stateless.

This replaced an earlier ad-hoc scheme (chunk the payload, send an `"ack"`
after each chunk, terminate with a literal `"DONE"` marker) that was fragile
by construction: RFCOMM is a reliable ordered byte stream with no built-in
message boundaries, so nothing guaranteed a `recv()` call would ever return
exactly the sentinel bytes `b"DONE"` and nothing else — a coalesced or split
read could silently corrupt a transfer. The length prefix removes the need
for a sentinel entirely, and per-chunk acks were never actually
necessary — RFCOMM already guarantees reliable delivery, so they only added
round-trip latency without adding safety.

If you change the header format or add a new command type on the Android
side, `bt_server.py` (in `tesla-panel`) must change to match, and the
updated file has to actually be redeployed to the Pi — nothing automates
that.

## Things that will bite you

- `app/build.gradle` used to have a `fileTree` dependency pointing at
  `C:\Users\romaingue\AndroidStudioProjects\TeslaLED` — a Windows path from the
  original author's machine. It's been removed (nothing in the source actually
  used it; the two `bluecove-*.jar` files it might have referred to were a
  desktop-Java Bluetooth library, incompatible with Android's own
  `android.bluetooth` APIs used here, and have been deleted from the repo). If
  you see build errors referencing a missing directory, check for a
  reintroduced Windows-only path in a dependency block.
- `local.properties` is machine-generated (SDK path) and gitignored — never
  hand-edit it into a commit, and don't rely on a path checked into git history
  being valid on your machine.
- The target Pi's Bluetooth MAC is hardcoded in
  `BluetoothClient.findDevice()`. Any test/build against real hardware needs
  that value updated to match whichever Pi you're actually talking to.
- `BLUETOOTH_CONNECT` is a runtime permission on Android 12+ (targetSdk 31+
  here). Declaring it in the manifest isn't enough — `MainActivity` must
  request it (`onCreate`) and code that touches `BluetoothClient` must check
  it's actually granted before calling into Bluetooth APIs, not just assume
  the manifest entry covers it.
- Pairing a new phone with the Pi will silently fail (no error anywhere, not
  even in `bluetoothctl paired-devices`) unless the Pi has a pairing agent
  running — see `tesla-panel`'s `tesla/README.md`. This has nothing to do
  with the Android code; don't go looking for a client-side cause if pairing
  itself won't complete.
- A panel showing colored static/noise instead of the actual image is a
  Pi-side GPIO-timing or wiring issue, not an Android-side bug — see
  `tesla-panel`'s `tesla/README.md` troubleshooting section. Confirmed by
  reproducing it with the LED matrix library's own test patterns, no
  Bluetooth/Android involved at all.
- `BluetoothClient` discards (closes + nulls) its socket on any write/read
  failure, so `isConnected()` correctly reports false and the next `send()`
  opens a fresh connection. Without this, Android's `BluetoothSocket` can
  keep reporting `isConnected() == true` even after the remote end is long
  gone (e.g. `teslabot` restarted on the Pi) - discovered when redeploying a
  Pi-side fix mid-session left the app writing into a socket that failed
  with "Broken pipe" on every subsequent send until reinstalled.
- Two Android view-layout traps hit during development, worth knowing before
  reintroducing either pattern: (1) constructing a class that touches
  `Context` methods (e.g. `SharedPreferences`) as an `Activity` *field
  initializer* crashes with a `NullPointerException` - field initializers
  run before `attachBaseContext()`, so do that construction in `onCreate()`
  instead. (2) `GridLayout` with per-row fixed heights and `weight=1f`
  specs breaks down once the total minimum row height exceeds the
  container's available height (logs "constraints ... are inconsistent");
  wrapping it in a scrolling container (so it's measured with unspecified
  height) avoids the conflict entirely rather than trying to tune weights.

## Conventions actually in use (not necessarily best practice, but consistent)

- Everything the panel can show is a `PanelMessage` (one or more `Frame`s -
  see `model/`); there's no separate class hierarchy per content type
  anymore. `MessageAdapter` binds them into `MainActivity`'s thumbnail grid,
  filtered by the category/language chips.
- Status feedback for the browsing grid is via the "now showing" preview
  pane (bound to `MessageSender.Listener`), not per-card color flashes.
- `.ppm` (P6, 64×32) is the format every frame is sent as, whether it's a
  bundled asset or `PixelFontRenderer`-generated text — `ppm/PpmCodec`
  encodes/decodes it, `ppm/PpmBitmap` additionally decodes+upscales
  (nearest-neighbor) for on-screen thumbnails/previews so they stay crisp
  rather than smoothed.

## Build/verify

```
./gradlew assembleDebug
```
There's no CI, no lint gate enforced, and only the default Android Studio
instrumented/unit test templates exist (`ExampleInstrumentedTest`,
`ExampleUnitTest`) — they don't test this app's actual logic. Don't treat a
green `./gradlew test` as meaningful coverage of Bluetooth/command behavior.
