# Sharepoint
A Minecraft Fabric client mod that allows sharing Lunar Client `waypoints.json` through simple in-game commands.

## What it does
- Reads your waypoint file from:
  - `<minecraft game dir>/waypoints.json` (if present), otherwise
  - `~/.lunarclient/settings/game/waypoints.json`
- Lets you export waypoints into `<minecraft game dir>/sharepoint-shared/`
- Lets you import another shared waypoint file back into your active `waypoints.json`

## Commands
Run these in-game (client commands):
- `/sharepoint export <name>` → saves your current `waypoints.json` as `sharepoint-shared/<name>.json`
- `/sharepoint import <name>` → replaces your active `waypoints.json` with `sharepoint-shared/<name>.json`
- `/sharepoint list` → lists available shared waypoint files

## Development notes
This repository now contains a minimal Fabric Loom scaffold targeting Minecraft `1.21.6` / Fabric API `0.126.0+1.21.6` and Java 21.
