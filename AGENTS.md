# AGENTS.md

Context for AI coding agents working on this repo. See `README.md` for the
project's purpose and architecture first.

## Scope of this repo

This repo has the Android client only (`app/`). The Pi-side receiver used to
be duplicated here under `pi/`, but that was a stale fork of what's now
maintained at
[romainhedouin/tesla-panel](https://github.com/romainhedouin/tesla-panel)
(`bt_server.py` at that repo's root) — it's been removed from here to avoid
two copies silently drifting apart. Treat `tesla-panel`'s `bt_server.py` as
authoritative for anything Pi-side (see that repo's own README for the wire
protocol and deployment details); this repo and that one deploy completely
separately (one's an APK, the other's copied by hand onto the Pi's home
directory and run via systemd) and nothing in the build connects them — a
protocol change requires manually redeploying `bt_server.py` to the actual
Pi regardless of which repo you edited it in, and nothing checks that the
Pi is still in sync with what's committed there either.

## The wire protocol (see tesla-panel's `bt_server.py` for the authoritative receiver side)

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
  running — see `tesla-panel`'s README. This has nothing to do with the
  Android code; don't go looking for a client-side cause if pairing itself
  won't complete.
- A panel showing colored static/noise instead of the actual image is a
  Pi-side GPIO-timing or wiring issue, not an Android-side bug — see
  `tesla-panel`'s README troubleshooting section. Confirmed by reproducing it
  with the LED matrix library's own test patterns, no Bluetooth/Android
  involved at all.
- `BluetoothClient.connect()` guards against `createSocket()` failing (no
  device, permission denied, or the create call itself throwing) before
  touching the socket — `createSocket()` returns `false` in all of those
  cases instead of leaving `this.socket` null for `connect()` to `NPE` on.
  `connect()` also runs the actual `BluetoothSocket.connect()` call under a
  watchdog thread that force-closes the socket after `CONNECT_TIMEOUT_MS` —
  that call has no documented timeout, and everything (`send()`/`stop()`)
  funnels through the single `ioHandler` thread that would otherwise be
  stuck inside it indefinitely.
- `BluetoothClient.sendCommand()` discards (closes + nulls) its socket
  whenever the send fails, whether that's a write/read `IOException` *or* the
  status-byte read returning EOF (`-1`) - the latter happens when the remote
  end (e.g. `teslabot` restarted on the Pi) closes the connection without an
  error, which Android's `BluetoothSocket.isConnected()` doesn't detect on
  its own (it only reflects local state). Without discarding on both cases,
  every future `send()` keeps reusing the same dead socket forever, silently
  failing. `MessageSender.connectAndSyncBrightness()` also checks this call's
  return value for the same reason: a failure there can mean the socket was
  just discarded, so the very next `sendCommand()` would otherwise `NPE` on
  a null socket instead of failing gracefully.
- All `BluetoothClient` I/O runs on `MessageSender`'s own `HandlerThread`
  (`ioHandler`), never on the caller's thread - a socket connect can block
  for several seconds, and doing that on the UI thread from a button click
  freezes the whole app (manifests as "spent Nms processing MotionEvent"
  warnings and taps that appear to silently do nothing for several
  seconds). `currentMessage`/`generation` are only ever touched on
  `ioHandler`'s thread; `Listener` callbacks are posted back to the main
  thread since they touch views. Don't add a Bluetooth call anywhere else
  without routing it through `ioHandler`.
- `MessageStore.getFiltered()` treats a language-neutral message (no
  language tag) as matching *every* language filter, not just "All" - a
  message meant for everyone shouldn't disappear just because the user
  filtered to a specific language. Easy regression to reintroduce if this
  method gets touched again: the free-pass check must be on the message's
  own language, not only on whether the selected filter is empty.
- Long-pressing the Emoji category chip is a hidden feature
  (`MainActivity.startEasterEgg()`), not dead code — it cycles a fixed emoji
  list via `EasterEggDataSource` (a `LiveDataSource`) until Stop is pressed,
  found by scanning the chip group for the chip whose text is "Emoji" and
  attaching `setOnLongClickListener` (returning `true` so the gesture
  doesn't also toggle the chip's checked state / switch categories). Don't
  "clean up" that lookup loop thinking it's unused.
- Contrast was tried as a second emoji-appearance knob (alongside Gamma) and
  fully removed after real-panel testing showed it made high settings worse,
  not better — only Gamma remains in `EmojiRenderer`/`Settings`/the emoji
  panel UI now. A gamma-style brightening curve was also tried as *the*
  contrast implementation before Gamma existed as its own control, and was
  also reverted for the same reason (washed the panel out at high values).
  If either comes back, re-test against the actual panel before trusting it,
  not just the in-app preview — that's what caught both regressions.
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

- Everything the panel can show is a `PanelMessage` (one or more `Frame`s, or
  a `LiveDataSource` for the Data category — see `model/`); there's no
  separate class hierarchy per content type. `MessageAdapter` binds them into
  `MainActivity`'s thumbnail grid, filtered by category chips; the language
  filter is an app-bar popup menu (`showLanguageMenu()`), not a chip row —
  it was moved there deliberately to stop it eating vertical space, and it
  doubles as the "display language" for Data's generated labels.
- A `LiveDataSource` (`model/LiveDataSource.java`) is just
  `byte[] renderFrame()` — implement it to add a new live-updating category
  (see `data/TimeDataSource`/`data/SpeedDataSource` for the pattern:
  construct with a `PixelFontRenderer` + `Settings`, render on demand,
  `MessageSender` handles the re-render/re-send loop). `renderTwoLine()` on
  `PixelFontRenderer` is what these use for the label/value split — top half
  is the label, bottom half the value, both centered independently.
- Status feedback for the browsing grid is via the "now showing" bar (bound
  to `MessageSender.Listener` plus a poll of `BluetoothClient.isConnected()`
  for the idle Connected/Not connected text), not per-card color flashes.
- `.ppm` (P6, 64×32) is the format every frame is sent as, whether it's a
  bundled asset, `PixelFontRenderer`-generated text, or a `LiveDataSource`
  frame — `ppm/PpmCodec` encodes/decodes it, `ppm/PpmBitmap` additionally
  decodes+upscales (nearest-neighbor) for on-screen thumbnails/previews so
  they stay crisp rather than smoothed.
- The Emoji category isn't a `PanelMessage` list like the others — it's a
  curated 200-entry grid (`EmojiPickerAdapter`, backed by `MainActivity`'s
  `EMOJIS` constant) plus a keyboard-fallback `EditText`
  (`emoji_keyboard_input`) for anything not curated, both funneling into
  `sendEmoji()`. `EmojiRenderer` renders through `Canvas`/`Paint` instead of
  `PixelFontRenderer`'s BDF glyph blit, since BDF fonts here don't carry
  color emoji. The grid always previews at neutral gamma (1.0) regardless of
  the adjustable Gamma setting below it — only the actual `sendEmoji()` call
  applies `emojiGamma` — so the picker shows what the emoji *is*, not a live
  preview of the panel adjustment; that was tried the other way and
  deliberately reverted.

## Build/verify

```
./gradlew assembleDebug
```
There's no CI, no lint gate enforced, and only the default Android Studio
instrumented/unit test templates exist (`ExampleInstrumentedTest`,
`ExampleUnitTest`) — they don't test this app's actual logic. Don't treat a
green `./gradlew test` as meaningful coverage of Bluetooth/command behavior.
