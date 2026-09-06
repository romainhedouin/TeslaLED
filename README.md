# TeslaLED

An Android app that remote-controls an LED message panel mounted on a car, over
Bluetooth. The panel itself is driven by a Raspberry Pi (3B/3B+) — this repo
only contains the Android client, not the Pi-side receiver.

## How it works

- The phone pairs with the Raspberry Pi over classic Bluetooth (SPP, RFCOMM),
  connecting to a hardcoded MAC address (`BluetoothClient.findDevice()`).
- `MainActivity` renders one button per feature, defined in the
  `featuresToCommands` map. Each button sends a `PanelCommand` to the Pi:
  - `ImageCommand` — sends a raw `.ppm` image (bundled in `app/src/main/assets`)
    for the panel to display.
  - `LegacyCommand` — sends a named script/keyword the Pi-side software already
    knows how to run (a leftover command style from before images were used).
- Messages are framed as: a command-type string (`image_command` /
  `legacy_command`), then the payload, chunked into ~980-byte writes over the
  socket (with a read after each chunk, presumably as a basic ack), then a
  final `DONE` marker.
- On button release, `PanelCommand.kill()` sends a `kill` message to stop
  whatever's currently showing on the panel.

## Requirements

- Android Studio (or just the command-line SDK tools + a JDK) with SDK
  Platform 32 installed.
- minSdk 23, targetSdk/compileSdk 32.
- A phone with Bluetooth, paired with the Pi ahead of time (pairing happens
  outside the app, at the OS level).

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
- No error surfacing beyond `System.out.println` — failures show up as a red
  button flash and a generic toast, not a real message.
- `BluetoothClient` extends `android.content.Context` and stubs out every
  abstract method just to reuse `checkSelfPermission()`. It isn't a real
  Context; don't rely on the stubbed methods returning anything but `null`/`0`.
