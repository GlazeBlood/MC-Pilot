# HTTP API reference

The bridge listens on **`http://127.0.0.1:8765`** by default and **binds to loopback only** — nothing
on your LAN can reach it. If a token is configured (`-Daipilot.token=...`), every request must carry
the header `X-Ai-Pilot-Token: <token>`, otherwise the bridge answers `401`.

Response envelope:

```jsonc
{ "ok": true,  "message": "optional", "data": { /* endpoint-specific */ } }
{ "ok": false, "error": "human-readable reason" }
```

Action endpoints report failures **inside** the JSON body (`ok:false`) rather than as HTTP 500, so a
client should check `ok` and not just the status code. A task that does not complete within 15 s
fails with a timeout error.

---

## `GET /` — index

Returns a tiny HTML page listing the endpoints. Handy for a browser smoke test.

## `GET /health` — liveness

Cheap check; run this first. Useful fields:

| field | meaning |
|---|---|
| `player`, `world` | whether a player entity / world is loaded yet |
| `focused` | whether the game window currently has OS focus |
| `screen` | `none`, or the open screen's class name |
| `window` | framebuffer size, e.g. `854x480` |
| `pauseOnLostFocus` | current value of the vanilla pause option |

If this call is refused, Minecraft is not running or the mod is not installed.

## `GET /state` — structured game state

The grounding call for an agent: prefer it over reading numbers off a screenshot.

| field | notes |
|---|---|
| `mc`, `focused`, `screen`, `windowWidth/Height` | always present |
| `dimension`, `timeOfDay` | when a world is loaded |
| `server` | server address, or `singleplayer/menu` |
| `resourcepacks` | `id \| display name` for each **enabled** pack — use it to confirm a server-forced pack (id `server`) actually got applied |
| `biome` | best-effort |
| `gui` | present when a `HandledScreen` is open — see below |
| `player` | present when in a world — see below |
| `target` | crosshair target |

`player`: `x y z yaw pitch health maxHealth food air xpLevel xpProgress gamemode selectedSlot`,
plus `hotbar` (9 entries, always present) and `inventory` (non-empty main-inventory slots, each with
its `slot` index).

`target`: `type` is `block` (`pos`, `block`) or `entity` (`entity`, `name`, `id`).

### The `gui` object

This is **ground truth for verifying server GUIs** (chest-style menus from DeluxeMenus, TrMenu,
ItemsAdder, …) against a design spec:

```jsonc
"gui": {
  "title": "§6Shop",
  "rows": 6,
  "slots": [
    { "slot": 13, "id": "minecraft:paper", "count": 1,
      "name": "§bDiamond Sword", "cmd": 10021,
      "lore": ["§7Damage: §c+7", "§8Right-click to buy"] }
  ]
}
```

Only **non-empty** slots are listed. `slot` is the index within the container `0..rows*9-1`.
`cmd` is the item's CustomModelData value — the integer that binds ItemsAdder-style custom item
icons, so it is the field that tells you *which* texture the player will actually see.
`name` and `lore` are the resolved display strings (section signs `§` included).

## `GET /screenshot` — PNG framebuffer

Full window pixels including HUD and any open GUI. Coordinates used by `/cursor` and `/clickAt` are
**the same pixel space** as this image, 1:1 — so you can locate a button in the PNG and click it
directly. Note it is the raw framebuffer, so it is scaled by the GUI scale setting.

Screenshots are ~100–500 KB. They are not written to the log.

---

## Action endpoints (all `POST`, JSON body)

### `POST /look` — aim

```jsonc
{ "yaw": 90, "pitch": -10, "relative": false }
```

Absolute by default; `"relative": true` adds to the current angles. Either field may be omitted.
Pitch is clamped to `[-90, 90]`. Works in-world and while a GUI is open. Returns the resulting angles.

### `POST /move` — hold/release a movement key

```jsonc
{ "key": "forward", "pressed": true }
```

`key` ∈ `forward | back | left | right | jump | sneak | sprint`. This presses the *keybinding*, so it
behaves exactly like holding the key — it only acts while the world is open, and the game ignores it
while a GUI screen is up. **Always pair a `true` with a `false`** or the key stays held.

### `POST /key` — raw key press

```jsonc
{ "key": "E", "action": "tap" }
```

`action` ∈ `tap | press | release` (aliases `down`/`up`). Key names are listed in
[Key names](#key-names). This goes through the keyboard handler, so it drives GUIs, F3, ESC, etc.

### `POST /char` — type text

```jsonc
{ "text": "hello world" }
```

Injects characters into the currently open text field (e.g. an open chat screen). Not affected by
the OS keyboard layout.

### `POST /mouse` — click

```jsonc
{ "button": "left", "action": "click" }
```

`button` ∈ `left | right | middle` (or `0|1|2`); `action` ∈ `click | press | release`
(aliases `down`/`up`). In-world `left` attacks, `right` uses/places — after aiming with `/look`.
Refused when the window is unfocused **unless** the virtual pointer is armed.

### `POST /cursor` — move the pointer (GUIs)

```jsonc
{ "x": 426, "y": 285 }
```

Screenshot-pixel coordinates. Meaningful inside menus/chests; for in-world aiming use `/look`.
The response message gains `" (virtual)"` when the virtual pointer is armed.

### `POST /clickAt` — move and click in one call

```jsonc
{ "x": 426, "y": 285, "button": "left" }
```

Equivalent to `/cursor` immediately followed by `/mouse`, but **atomically inside one game-thread
task**, so nothing can move the pointer in between. Prefer this over the two-call sequence — it
removes a whole class of flaky GUI misclicks.

### `POST /virtual` — arm the virtual pointer

```jsonc
{ "enabled": true, "guiOnly": true, "x": 0, "y": 0 }
```

All fields optional; omitted fields keep their current value. See
[VIRTUAL-POINTER.md](VIRTUAL-POINTER.md). With `guiOnly: true` (the default) it only takes effect
while a screen is open, leaving in-world camera look untouched. Returns the current state.

### `POST /scroll` — scroll wheel

```jsonc
{ "dy": -1, "dx": 0 }
```

`dy` scrolls the hotbar / inventory / zoom; at least one of `dy`, `dx` must be non-zero.

### `POST /chat` — chat or command

```jsonc
{ "message": "hi" }             // → sent as chat
{ "message": "/time set day" }  // → sent as a command
```

A leading `/` routes to `sendChatCommand`, otherwise `sendChatMessage`. Requires a live connection.
Note this **sends immediately**; it does not open the chat screen (`/gui chat` does that).

### `POST /hotbar` — select a hotbar slot

```jsonc
{ "slot": 2 }
```

`0..8`.

### `POST /gui` — open or close a screen

```jsonc
{ "open": "inventory" }
{ "open": "chat", "text": "prefilled" }
{ "close": true }
```

`open` ∈ `inventory | chat`. To close any screen, pass `"close": true` (equivalent to ESC).

### `POST /focus` — foreground the game window

Requests OS focus for the Minecraft window. Run this when another call reports the window is
unfocused. An empty JSON body (`{}`) is fine.

### `POST /pause` — pause-on-lost-focus

```jsonc
{ "enabled": false }
```

The bridge turns `pauseOnLostFocus` **off** automatically on first use so the game keeps running
while you work in other windows. Calling `/pause` explicitly pins the option and stops the bridge
from changing it again.

---

## Key names

`A`–`Z`, `0`–`9`, `F1`–`F12`, `SPACE`, `ENTER`, `ESC`/`ESCAPE`, `TAB`, `BACKSPACE`,
`SHIFT`/`LEFT_SHIFT`, `CTRL`/`LEFT_CONTROL`, `ALT`/`LEFT_ALT`, `UP`, `DOWN`, `LEFT`, `RIGHT`,
`DELETE`, `HOME`, `END`, `PAGE_UP`, `PAGE_DOWN`, `INSERT`, `MINUS`, `EQUAL`, `LEFT_BRACKET`,
`RIGHT_BRACKET`, `SEMICOLON`, `APOSTROPHE`, `GRAVE`, `BACKSLASH`, `COMMA`, `PERIOD`, `SLASH`,
`KP_0`–`KP_9`.

Unknown names return `ok:false` with an example list rather than throwing.
