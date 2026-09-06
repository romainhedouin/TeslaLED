# TeslaLED

An Android app that remote-controls an LED message panel mounted on a car, over
Bluetooth. The panel itself is driven by a Raspberry Pi (3B/3B+).

> **Note:** this app is also maintained as `android/` inside
> [romainhedouin/tesla-panel](https://github.com/romainhedouin/tesla-panel)
> (a fork of `hzeller/rpi-rgb-led-matrix`), alongside the Pi-side receiver in
> that repo's `tesla/` directory — see `tesla/README.md` there for
> deployment and a documented gotcha around Bluetooth pairing on a headless
> Pi. This standalone repo may drift from that copy; treat `tesla-panel` as
> the actively-maintained one if the two disagree.

## How it works

- The phone pairs with the Raspberry Pi over classic Bluetooth (SPP, RFCOMM),
  connecting to a hardcoded MAC address (`BluetoothClient.findDevice()`).
- Everything the panel can show is a `PanelMessage` (`model/PanelMessage.java`):
  a label, a category (Greetings/Courtesy/Traffic-safety/Fun/Custom), an
  optional language tag (`fr`/`en`/none), and one or more `Frame`s (a ready-
  to-send 64×32 PPM + a display duration). A single frame is a static image;
  multiple frames are a slideshow/animation — there's no separate concept
  for "animation," it's just a `PanelMessage` with more than one frame.
- `storage/MessageStore` loads the bundled defaults (`assets/default_messages.json`
  + the `.ppm` assets it references) and any user-created messages (persisted
  under the app's internal files dir), and supports filtering by
  category/language.
- `MainActivity` shows those messages as a thumbnail grid (`MessageAdapter`),
  filterable by category/language chips, with a "now showing" preview pane at
  the top — accurate for free, since the app is the only thing that ever
  tells the panel what to display or when to stop.
- `CreateMessageActivity` (the "+" button) is how you make a custom message:
  a label, category, language, and a list of frames. Each frame is just
  typed text — `render/PixelFontRenderer` rasterizes it using a bitmap (BDF)
  font bundled from the `rpi-rgb-led-matrix` library (`assets/fonts/9x18B.bdf`),
  blitting glyph pixels directly with no anti-aliasing, so panel text is
  always crisp. A frame whose text doesn't fit the panel width is
  auto-paginated into multiple frames by `render/TextChunker` (word-boundary
  aware) — long messages split into readable chunks rather than scrolling,
  since scrolling text is hard to read from a following car.
- `MessageSender` sends a `PanelMessage`'s frames in order over Bluetooth,
  timing each one client-side via `Handler.postDelayed`, and sends a final
  kill once the sequence ends.
- Messages are framed as `[1 byte command type][4 bytes big-endian payload
  length][payload]`, then a single-byte status response. `BluetoothClient`
  writes the whole thing in one shot — no manual chunking or sentinel
  values, since RFCOMM is a reliable ordered stream that already handles
  fragmentation/reassembly transparently. Command types: image, kill, and
  set-brightness (see `Settings` — brightness is a persisted app setting,
  synced to the Pi before every message so it's always current).

## Requirements

- Android Studio (or just the command-line SDK tools + a JDK) with SDK
  Platform 32 installed.
- minSdk 23, targetSdk/compileSdk 32.
- A phone with Bluetooth, paired with the Pi ahead of time (pairing happens
  outside the app, at the OS level — see `tesla-panel`'s `tesla/README.md` if
  pairing silently fails, since a headless Pi needs a pairing agent running
  to accept it).

## Building

```
./gradlew assembleDebug
```
The APK lands in `app/build/outputs/apk/debug/app-debug.apk`. Sideload it to a
phone directly (`adb install`, AirDrop, etc.) — it isn't published anywhere.

## Known rough edges

- The target device's Bluetooth MAC address is hardcoded in
  `BluetoothClient.findDevice()`. Point it at a different Pi and you'll need to
  edit and rebuild.
- No error surfacing beyond `System.out.println`/`Toast` — failures don't
  produce a real diagnostic message.
- On Android 12+ (targetSdk 31+), `BLUETOOTH_CONNECT` is a runtime permission.
  `MainActivity` requests it on launch and re-checks before every command; if
  you see the app silently no-op on button taps (a toast, no crash), the
  permission was denied — check phone Settings → Apps → TeslaLED →
  Permissions.
- Multi-frame sequences are "slideshow" pacing (each frame shown for at
  least ~0.5-1s+), not smooth high-fps animation — each frame costs a
  Bluetooth round trip and the Pi respawning its rendering process. Fine for
  paginated text or a deliberate slideshow; not meant for anything
  resembling video.
- Custom messages currently support text only (no photo import, no freehand
  drawing) — both would fit the same `Frame` model later without changing
  anything else.
