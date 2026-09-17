# MCpilot

**Let an AI agent see and control your Minecraft client.**

MCpilot is a small Fabric mod plus an agent skill that together expose a local Minecraft 1.20.6
client over a loopback HTTP bridge. An AI agent can then take screenshots, read structured game
state, and inject keyboard/mouse/chat input — so it can actually *play*, *test*, and *verify* the
game rather than guess from logs.

> All input is injected through the same vanilla code paths a human keyboard and mouse use.
> There is no world-reading cheat channel and no privileged capability: the bridge can do exactly
> what a player sitting at the keyboard can do. The HTTP server binds to `127.0.0.1` only.

---

## Why

Automating a Minecraft client usually means either a headless bot (never touches the real client)
or brittle screen-scraping. MCpilot sits inside the real client, so:

- **The agent sees what the player sees** — the actual framebuffer, including HUD and any open GUI.
- **The agent can trust numbers** — coordinates, health, inventory, crosshair target and server-GUI
  contents come back as structured JSON instead of being read off pixels.
- **Verification is real** — this was built to check server-side GUIs (DeluxeMenus / TrMenu /
  ItemsAdder menus) against a design spec, item by item, including `CustomModelData` icon bindings.

## Repository layout

```
mc-pilot/
├── mod/                  Fabric mod "aipilot" (the in-game HTTP bridge)
│   └── src/main/java/dev/aipilot/
│       ├── AiPilotClient.java     mod entrypoint, reads -Daipilot.port / -Daipilot.token
│       ├── PilotServer.java       HTTP server + routing (loopback only)
│       ├── Actions.java           one method per endpoint, all on the game thread
│       ├── VirtualPointer.java    GUI pointer that survives the human using their mouse
│       ├── KeyNames.java          key-name → GLFW code table
│       └── mixin/                 minimal accessors/invokers
├── skill/                Agent skill (drop into your agent's skills directory)
│   ├── SKILL.md          the perception-action loop, for the agent to read
│   └── scripts/mc.ps1    PowerShell CLI over the bridge
└── docs/
    ├── API.md            full endpoint reference
    └── VIRTUAL-POINTER.md design notes on the virtual pointer
```

## Requirements

| | |
|---|---|
| Minecraft | 1.20.6, **client** |
| Loader | Fabric Loader ≥ 0.15.0 |
| Java | 21+ |
| OS | the skill CLI targets Windows (PowerShell); the mod itself is platform-independent |

## Build

```bash
cd mod
./gradlew build          # or: gradle build
```

The jar lands at `mod/build/libs/aipilot-<version>.jar`.

> Fabric Loom downloads and remaps Minecraft on first run, so the first build takes a few minutes
> and needs network access. Later builds are fast.

## Install

1. Install **Minecraft 1.20.6 + Fabric Loader** (in PCL2: version list → install new version →
   1.20.6 with Fabric).
2. Copy `aipilot-<version>.jar` into that version's `mods/` folder (create it if missing).
3. Optional JVM arguments (launcher → version settings → JVM arguments):

   | argument | effect |
   |---|---|
   | `-Daipilot.port=8765` | use a different port |
   | `-Daipilot.token=YOURSECRET` | require the `X-Ai-Pilot-Token` header on every request |

4. Launch the game. When the log shows

   ```
   [aipilot] HTTP bridge listening on http://127.0.0.1:8765 (no token; localhost only)
   ```

   the bridge is up.

## Use it

### From a browser or curl

```bash
curl http://127.0.0.1:8765/health
curl http://127.0.0.1:8765/state
curl -o shot.png http://127.0.0.1:8765/screenshot
curl -X POST http://127.0.0.1:8765/look  -d '{"yaw":90,"pitch":0}'
curl -X POST http://127.0.0.1:8765/chat  -d '{"message":"/time set day"}'
```

### From the skill CLI

```powershell
$mc = ".\skill\scripts\mc.ps1"   # ExecutionPolicy may require -ExecutionPolicy Bypass

powershell.exe -NoProfile -ExecutionPolicy Bypass -File $mc health
powershell.exe -NoProfile -ExecutionPolicy Bypass -File $mc shot .\shots\mc.png
powershell.exe -NoProfile -ExecutionPolicy Bypass -File $mc state
powershell.exe -NoProfile -ExecutionPolicy Bypass -File $mc clickat 426 285 left
```

### From an AI agent

Copy `skill/` into your agent's skills directory, or point the agent at `skill/SKILL.md`. The skill
teaches the loop *check → see → ground → act → verify*:

1. **Check** the bridge is alive (`health`).
2. **See** — take a screenshot and read it with a vision-capable model.
3. **Ground** — pull `/state` so positions, health and GUI contents come from data, not pixels.
4. **Act** — one small input at a time.
5. **Verify** — screenshot again and confirm the effect landed.

The stock skill text mentions DeepSeek Harness, but nothing in it is harness-specific: any agent
that can run a shell command and view an image can use it.

## Endpoints at a glance

Full details, field-by-field, in [`docs/API.md`](docs/API.md).

| method | path | purpose |
|---|---|---|
| `GET` | `/health` | liveness, focus state, open screen |
| `GET` | `/state` | structured game state incl. inventory and open-GUI contents |
| `GET` | `/screenshot` | PNG of the framebuffer (HUD + GUIs included) |
| `POST` | `/look` | set absolute or relative yaw/pitch |
| `POST` | `/move` | press/release a movement keybinding |
| `POST` | `/key` | raw key press/release/tap |
| `POST` | `/char` | type text into an open field |
| `POST` | `/mouse` | click / press / release a mouse button |
| `POST` | `/cursor` | move the pointer (GUI pixel coords) |
| `POST` | `/clickAt` | move + click atomically — preferred inside menus |
| `POST` | `/virtual` | arm/disarm the virtual pointer |
| `POST` | `/scroll` | mouse wheel |
| `POST` | `/chat` | send chat or run a `/command` |
| `POST` | `/hotbar` | select hotbar slot 0–8 |
| `POST` | `/gui` | open inventory/chat, or close the screen |
| `POST` | `/focus` | bring the game window to the foreground |
| `POST` | `/pause` | toggle pause-on-lost-focus |

Responses are uniform: `{"ok":true,"message":...,"data":{...}}` or `{"ok":false,"error":"..."}`.
Action failures are reported inside the JSON body rather than as HTTP 500, so **check `ok`**.

## How the virtual pointer works

Minecraft keeps exactly one cursor position. Injecting into it doesn't move the OS cursor, so the
moment the human moves their real mouse the injected position is overwritten — the agent and the
user fight over one value, and mouse injection historically also required stealing window focus.

Arming the virtual pointer (`POST /virtual {"enabled":true}`) makes a mixin rewrite every incoming
real cursor event to the stored virtual position. By default it only applies while a screen is open
(`guiOnly`), leaving in-world camera look untouched — so the agent can drive a chest GUI while you
keep using your own mouse. Details and verified test notes: [`docs/VIRTUAL-POINTER.md`](docs/VIRTUAL-POINTER.md).

## Behaviour and safety notes

- **Loopback only.** The server binds `127.0.0.1`; it is not reachable from your LAN. Set a token if
  other local processes shouldn't be able to drive your game.
- **Pause-on-lost-focus is turned off** automatically on first use so the game keeps running while
  you work in another window. `POST /pause {"enabled":true}` pins it back and stops the bridge from
  changing it again.
- **Injection needs focus** for keyboard and mouse — except while the virtual pointer is armed. If a
  call reports the window is unfocused, run `focus` first.
- **The human always wins the pointer** unless the virtual pointer is armed; don't fight over the
  mouse.
- **Do not minimise** the window while a script is running — minimised windows stop rendering, so
  screenshots fail.
- Anything the agent does, it does as your player. Use it on your own worlds/servers and follow
  whatever rules apply there.

## Contributing

Issues and pull requests are welcome. The codebase is deliberately small — one endpoint per method
in `Actions.java`, routing in `PilotServer.java`. When adding an endpoint, please also document it
in `docs/API.md` and, if it needs a CLI convenience wrapper, in `skill/scripts/mc.ps1`.

## License

[MIT](LICENSE).
