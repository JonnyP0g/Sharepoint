# Sharepoint

A Fabric client mod for sharing Lunar-compatible `waypoints.json` packs with
friends. Press **P** or use `/sharepoint` to open the interface.

## Workflow

- **Share waypoints** uploads your current pack and gives you a copyable code.
  Friends have 10 minutes to download it; you can disconnect after uploading.
- **Receive with a code** downloads into a unique saved pack and opens its preview.
  Your active waypoints are untouched until you explicitly import.
- **Saved packs** lets you search, preview, share, rename or delete local packs.
- **Settings** holds one HTTPS relay URL shared by your group.

Import currently **replaces the whole waypoint file**. It backs up the old file in
`sharepoint-backups` inside the game directory before writing the new file.
This version does not merge/deduplicate waypoint entries or sync shared groups;
Lunar's file schema has not been established well enough to merge it safely.
The preview count is a heuristic. Minecraft/Lunar may need to reload the file
before imported waypoints appear; live reload behavior has not been verified.

## Host the relay on your server PC

Double-click `relay/start-relay.bat`, then expose its local port through an HTTPS
tunnel. Your existing Minecraft tunnel is a separate connection.
The [relay setup guide](relay/README.md) covers Playit HTTPS + Caddy, a temporary
Cloudflare tunnel, and optional Docker/Render hosting.

The default build uses `https://sharepoint-relay.onrender.com`, configured
in `gradle.properties`. The relay runs on Render Free, so your PC and local
tunnel can be off. After inactivity, allow up to two minutes for it to wake up.
Settings can override the bundled URL; blank settings use the bundled default.

## Build and install

This project targets Minecraft **26.3**, Fabric Loader **0.19.5**, Fabric API
**0.161.0+26.3**, and Java **25**, as specified in `gradle.properties`.

```powershell
./gradlew.bat build
# Optional: preconfigure a real, stable relay for your friends:
./gradlew.bat build '-PrelayUrl=https://YOUR-RELAY-DOMAIN'
```

Copy `build/libs/sharepoint-1.0.0.jar` into the compatible client's `mods` folder
with Fabric API. The separate relay needs Java 21+ and runs outside Minecraft.
These requirements do not establish that every Lunar launcher mode can load
arbitrary Fabric mods; use a setup that supports this project's Fabric target.

Waypoint lookup checks `waypoints.json` in the game directory first, then
`~/.lunarclient/settings/game/waypoints.json`. Saved packs live in
`sharepoint-shared`; configuration lives in `config/sharepoint.properties`.

## Commands

`/sharepoint relay set <https-url>` configures the new relay.
`/sharepoint relay share` and `/sharepoint relay join` open the focused screens.
Local `export`, `import`, `list`, `rename`, `delete`, LAN `host`/`connect` and
legacy TCP relay commands remain available. `/sharepoint autosync on` only
watches and exports to the local saved folder; it is not online group syncing.

## Verification

`./gradlew.bat build` compiles the mod and runs real HTTP integration checks:
upload/download, retry, expiry, concurrent codes, rate/storage/body limits,
URL validation, strict JSON validation, unique received files, import backups
and persisted settings. `testHostedRelay` runs those checks independently.

See [LICENSE](LICENSE) for the repository's license text.
