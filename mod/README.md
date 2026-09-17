# aipilot — the in-game bridge mod

The Fabric mod half of [MCpilot](../README.md). It runs inside a Minecraft 1.20.6 **client** and
serves a loopback HTTP API on `http://127.0.0.1:8765` that lets a local AI agent see the game and
inject input.

For install instructions and the bigger picture, see the [root README](../README.md).
For the endpoint reference, see [`../docs/API.md`](../docs/API.md).

## Build

```bash
./gradlew build
```

Output: `build/libs/aipilot-<version>.jar`.

First build downloads and remaps Minecraft via Fabric Loom (network required, a few minutes).
`sourceCompatibility`/`targetCompatibility` and `options.release` are pinned to **Java 21**.

## Run

Copy the jar into the target version's `mods/` folder and launch. System properties:

| property | default | meaning |
|---|---|---|
| `-Daipilot.port=` | `8765` | TCP port to bind |
| `-Daipilot.token=` | *(empty)* | if set, requests must send `X-Ai-Pilot-Token` |

The listener is always bound to `127.0.0.1`.

## Source map

| file | role |
|---|---|
| `AiPilotClient.java` | `ClientModInitializer`; reads the properties and starts the server |
| `PilotServer.java` | HTTP server, routing, token check, JSON envelope |
| `Actions.java` | one method per endpoint; everything runs on the game thread |
| `VirtualPointer.java` | state for the GUI pointer that survives real mouse movement |
| `KeyNames.java` | key-name → GLFW key code table |
| `mixin/` | minimal accessors and invokers into vanilla `Mouse`, `Keyboard`, `MinecraftClient`, `HandledScreen` |

### Threading

Every endpoint that touches the game runs through `PilotServer.onClient(...)`, which hops onto the
Minecraft game thread via `mc.execute(...)` and waits up to **15 s**. Endpoints that only inject
input (`/key`, `/mouse`, `/cursor`, `/char`) call the vanilla handlers directly, matching what the
real GLFW callbacks do.

### Mixins

The mod is intentionally light on mixins — it uses accessors/invokers rather than rewriting game
logic, plus a single `@ModifyVariable` in `MouseVirtualMixin` for the virtual pointer.

## License

MIT — see [`../LICENSE`](../LICENSE).
