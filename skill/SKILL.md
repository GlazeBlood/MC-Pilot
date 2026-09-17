---
name: mc-pilot
description: Drive the local Minecraft 1.20.6 client (Fabric mod "aipilot") over a localhost HTTP bridge — take screenshots the agent can read with read_image, query structured game state, and inject keyboard/mouse/look/chat input.
whenToUse: Use when the user asks you to operate/play/see the local Minecraft client on this machine, inspect in-game screens or HUD, or automate in-game actions through the AI Pilot mod.
---

# mc-pilot — see and control the Minecraft client

The Fabric mod **aipilot** runs inside the local Minecraft 1.20.6 client and exposes an HTTP API on
**http://127.0.0.1:8765** (loopback only). All game actions are injected through the same vanilla
code paths a human keyboard/mouse uses — no privileges the player doesn't have.

- Mod source & build instructions: [`../mod/`](../mod/)
- Full endpoint reference: [`../docs/API.md`](../docs/API.md)

## Helper script

`scripts/mc.ps1` is PowerShell 5.1 compatible. ExecutionPolicy may block direct invocation, so
always call it through `powershell.exe -NoProfile -ExecutionPolicy Bypass -File`:

```powershell
$mc = "<absolute path to this skill>\scripts\mc.ps1"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File $mc health   # liveness check first
```

If the connection is refused → the client isn't running or the mod isn't installed → point the user
at the repo README for install/build steps.

## The perception-action loop

1. **Check** `mc.ps1 health`. If the error mentions focus → `mc.ps1 focus`.
2. **See**: `mc.ps1 shot <out.png>` then **read_image** that file.
   The screenshot is the raw framebuffer (window pixels, includes HUD and open GUIs);
   `/cursor` and `/clickAt` coordinates use these same pixels 1:1.
3. **Ground** decisions with state (cheaper and more reliable than vision for numbers):
   `mc.ps1 state` → position, yaw/pitch, health, food, xp, gamemode, selected hotbar slot, full
   hotbar + non-empty inventory, crosshair target (block/entity), biome, dimension, time of day,
   open screen name, focused flag, plus:
   - `resourcepacks`: enabled client resource pack ids — verify the server-forced pack
     (id `server`) is present after joining; if missing, the pack prompt was declined.
   - `gui`: when a container GUI is open — `title`, `rows`, and non-empty `slots[]` with
     `slot` index, `id`, `count`, `name` (display name), `cmd` (CustomModelData int — how
     ItemsAdder-style custom item icons are bound), and `lore[]`. This is the ground truth for
     verifying chest-style server GUIs (DeluxeMenus/TrMenu/MeowGUI) against design specs.
4. **Act** in small steps, then screenshot again to verify the effect.

Keep held keys held only as long as needed — always pair `move ... down` with `move ... up`.

## Act endpoints (via mc.ps1)

| goal | command |
|---|---|
| look (aim) | `mc.ps1 look <yaw> <pitch>` (absolute) or `mc.ps1 look 30 -10 rel` (relative) |
| walk / jump / sneak / sprint | `mc.ps1 move forward down` … `mc.ps1 move forward up` (keys: forward back left right jump sneak sprint) |
| press a key (GUI, F3, ESC…) | `mc.ps1 key E tap` (`down`/`up` for hold) |
| type into an open text field | `mc.ps1 char "hello world"` |
| move cursor (GUIs only) | `mc.ps1 cursor 400 300` then `mc.ps1 click left` |
| move + click atomically | `mc.ps1 clickat 400 300 left` (preferred inside menus) |
| click / hold mouse | `mc.ps1 click left` (or `right`/`middle`; `down`/`up` to hold) |
| scroll | `mc.ps1 scroll -1` (inventory/hotbar/zoom) |
| hotbar select | `mc.ps1 hotbar 2` (0..8) |
| send chat / run command | `mc.ps1 chat "hi"` or `mc.ps1 chat "/time set day"` |
| open GUI | `mc.ps1 gui inventory` / `mc.ps1 gui chat` / `mc.ps1 gui close` |
| free the pointer from your real mouse | `mc.ps1 virtual on` / `mc.ps1 virtual off` |
| bring window to front | `mc.ps1 focus` |
| restore pause-on-lost-focus | `mc.ps1 pause on` |

Raw endpoints (if scripting directly): `GET /health /state /screenshot`,
`POST /look /move /key /char /mouse /cursor /clickAt /virtual /scroll /chat /hotbar /gui /focus /pause`
with JSON bodies, e.g. `POST /move {"key":"forward","pressed":true}`.
Port override: `-Port` (server side: JVM arg `-Daipilot.port=`).

## Key names

`A`–`Z`, `0`–`9`, `F1`–`F12`, `SPACE`, `ENTER`, `ESC`/`ESCAPE`, `TAB`, `BACKSPACE`,
`SHIFT`/`LEFT_SHIFT`, `CTRL`/`LEFT_CONTROL`, `ALT`/`LEFT_ALT`, `UP` `DOWN` `LEFT` `RIGHT`,
`DELETE` `HOME` `END` `PAGE_UP` `PAGE_DOWN` `INSERT`, `MINUS` `EQUAL` `LEFT_BRACKET`
`RIGHT_BRACKET` `SEMICOLON` `APOSTROPHE` `GRAVE` `BACKSLASH` `COMMA` `PERIOD` `SLASH`, `KP_0`–`KP_9`.

## Tips

- Movement keys are injected as keybinding presses: they work while the game world is open; when a
  GUI screen is open, movement is ignored by the game (use cursor+click or `key ESC` to close first).
- In-world aiming is `/look` (works regardless of GUI); the raw cursor is only meaningful inside
  menus/chests (`/cursor` then `/mouse`, or just `/clickAt`).
- Attack/use are clicks: `mc.ps1 click left` attacks, `click right` uses/places — aim with `/look` first.
- The mod keeps the game running when the window loses focus (pause-on-lost-focus auto-off).
  Don't fight it: while the user is typing in another window, either `focus` first or wait.
- If the user's own mouse keeps stealing the GUI cursor, arm the virtual pointer
  (`mc.ps1 virtual on`) — see [`../docs/VIRTUAL-POINTER.md`](../docs/VIRTUAL-POINTER.md).
- Screenshots are ~100–500 KB PNG; a 60–120 tick (3–6 s) real-time gap between step and verify is
  normal — actions land on the next game tick.
