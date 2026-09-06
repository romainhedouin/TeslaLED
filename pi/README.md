# Pi-side receiver

The other half of this project: what actually runs on the Raspberry Pi
mounted behind the LED panel. Pulled from a live Pi (3B/3B+) on 2026-09-06;
previously this code only existed on that one SD card, undocumented and
unversioned.

## Components

- **`bt_server.py`** — listens on an RFCOMM (Bluetooth SPP) socket, matching
  the wire protocol the Android app speaks (see the root `AGENTS.md`).
  Receives `image_command` payloads (a `.ppm` file) and shells out to
  `rpi-rgb-led-matrix`'s `demo` binary to render them on a 64-column HAT
  matrix, or `legacy_command` payloads that run a matching `.sh` script from
  `/home/pi/src/`. Runs under `sudo` because driving the LED matrix's GPIO
  pins needs root.
- **`teslabot`** — the supervisor script. Keeps Bluetooth powered on and
  discoverable, keeps a pairing agent registered, and respawns `bt_server.py`
  if it ever exits.
- **`teslabot.service`** — the systemd unit that runs `teslabot` on boot.

## The pairing agent (easy to lose, worth documenting)

Getting a phone paired with this Pi doesn't work out of the box — this bit
everyone from scratch. Android's classic-Bluetooth bonding requests MITM
protection, and `bluetoothd` **rejects any pairing attempt with "Pairing Not
Allowed"** unless something has registered a pairing agent to authorize it.
A headless Pi with no screen/keyboard can't do interactive PIN confirmation,
so `teslabot` runs:
```
sudo bt-agent -c NoInputNoOutput
```
(from the `bluez-tools` package — `sudo apt-get install bluez-tools`) in a
restart loop, which auto-accepts pairing requests without prompting anyone.
Without this running, pairing attempts from a phone will silently fail (no
error dialog, no entry in `bluetoothctl paired-devices`) — the request never
even reaches `bt_server.py`, since it dies at the Bluetooth-daemon pairing
step.

Once paired, `bluetoothctl paired-devices` should list the phone, and
pairing doesn't need to be redone unless the phone or Pi's Bluetooth identity
changes (e.g. a fresh OS install, a different phone).

## Deploying to a Pi

1. Copy `bt_server.py`, `teslabot`, and any `/home/pi/src/*.sh` legacy
   scripts you still use onto the Pi's home directory.
2. `chmod +x /home/pi/teslabot`
3. `sudo apt-get install bluez-tools` (for `bt-agent`) and make sure PyBluez
   (`python-bluez` / `pip install pybluez`) and `rpi-rgb-led-matrix` (built
   under `/home/pi/rpi-rgb-led-matrix`) are present — neither is vendored
   here.
4. `sudo cp teslabot.service /etc/systemd/system/teslabot.service`
5. `sudo systemctl daemon-reload && sudo systemctl enable --now teslabot`
6. Pair the phone from its own Bluetooth settings once `teslabot` is running.

## Known rough edges (inherited, not introduced by the above)

- `bt_server.py` only ever calls `server_sock.accept()` once, for a single
  client — it isn't a real multi-connection server. If the phone disconnects
  uncleanly, `bt_server.py` likely needs to fully exit/respawn (which
  `teslabot`'s loop does handle) rather than accepting a new connection.
- The Bluetooth MAC this Pi answers on (hardcoded in the Android app's
  `BluetoothClient.findDevice()`) is `B8:27:EB:A5:89:A8`. Re-flashing or
  swapping the Pi's Bluetooth adapter means updating that constant too.
- No authentication beyond Bluetooth pairing itself — anything paired can
  send arbitrary `legacy_command` script names, which are run directly via
  `os.popen` if a matching `.sh` file exists in `/home/pi/src/`.
