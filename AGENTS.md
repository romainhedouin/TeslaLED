# AGENTS.md

Context for AI coding agents working on this repo. See `README.md` for the
project's purpose and architecture first.

## Scope of this repo

Both halves now live here: the Android client (`app/`) and the Pi-side
receiver (`pi/`, pulled from a live Pi on 2026-09-06 — see `pi/README.md`).
They deploy completely separately (one's an APK, the other's copied by hand
onto the Pi's home directory and run via systemd) and nothing in the build
connects them; keep that in mind when editing one side and assuming the other
picks up the change automatically. It doesn't — a protocol change requires
manually redeploying `pi/bt_server.py` to the actual Pi.

## The wire protocol (see `pi/bt_server.py` for the authoritative receiver side)

Each interaction is: send a command-type marker string, then a payload, then a
literal `DONE`. `bt_server.py`'s `receive_data()` sends `"ack"` after every
chunk and `"OK"` once it sees `DONE` — that's the response `BluetoothClient`
reads and discards after each write. If you change chunk size, framing, or add
a new command type on the Android side, `bt_server.py` must change to match,
and the updated file has to actually be redeployed to the Pi (see
`pi/README.md` — nothing automates that).

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
  running — see `pi/README.md`. This has nothing to do with the Android code;
  don't go looking for a client-side cause if pairing itself won't complete.

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
