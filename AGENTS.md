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
dispatches on the command type (`COMMAND_IMAGE` / `COMMAND_LEGACY` /
`COMMAND_KILL`, defined identically on both sides — keep the constants in
sync if you ever add one).

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
- `BluetoothClient extends Context` purely to get access to
  `ActivityCompat.checkSelfPermission()`. Every other `Context` method is a
  stub returning `null`/`0`/`false`. It holds a real `Context` in
  `realContext` (passed in via the constructor) specifically so permission
  checks reflect actual OS state — the old version checked permission on
  itself, which always returned `PERMISSION_GRANTED` regardless of reality
  and crashed with `SecurityException` at the real `socket.connect()` call.
  Do not call any other inherited method expecting real behavior, and do not
  use the fake-Context pattern as a template elsewhere.
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

## Conventions actually in use (not necessarily best practice, but consistent)

- One `PanelCommand` subclass per command type (`ImageCommand`,
  `LegacyCommand`); `MainActivity` wires each into a button via a
  `LinkedHashMap<String, PanelCommand>` — order in that map is the on-screen
  button order.
- Status feedback is a temporary button background color flash (green/red),
  not a dedicated status UI.
- `.ppm` is the image format sent to the panel — check `app/src/main/assets`
  for existing examples before adding a new image-based command.

## Build/verify

```
./gradlew assembleDebug
```
There's no CI, no lint gate enforced, and only the default Android Studio
instrumented/unit test templates exist (`ExampleInstrumentedTest`,
`ExampleUnitTest`) — they don't test this app's actual logic. Don't treat a
green `./gradlew test` as meaningful coverage of Bluetooth/command behavior.
