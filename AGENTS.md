# AGENTS.md

Context for AI coding agents working on this repo. See `README.md` for the
project's purpose and architecture first.

## Scope of this repo

This is the Android client only. There is no Pi/server-side source here — the
Raspberry Pi that receives Bluetooth messages and drives the physical LED
panel is a separate, undocumented system. Don't assume you can trace or fix
panel-side behavior from this codebase; you only see the client's half of the
protocol.

## The wire protocol (undocumented elsewhere, inferred from `BluetoothClient`/`PanelCommand`)

Each interaction is: send a command-type marker string, then a payload, then a
literal `DONE`. The Pi is expected to send some response after each write
(read into a buffer, discarded) — the exact contract (ack format, error
signaling) isn't specified anywhere and was reverse-engineered by prior
iterations of this app. If you change chunk size, framing, or add a new
command type, it must stay in sync with whatever the Pi-side script expects —
there's no shared schema to check against.

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
  stub returning `null`/`0`/`false`. Do not call any other inherited method
  expecting real behavior, and do not use this pattern as a template elsewhere.

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
