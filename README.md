# Sharepoint

Sharepoint is a Fabric mod for Minecraft that aims to make it easy to share Lunar Client waypoint files between players or across devices through a simple interface.

> Starter status: this repository is currently a **starter Fabric mod template**. The actual Lunar waypoint transfer/sync feature is not implemented yet.

## Target versions

This starter currently targets:

- Minecraft: **1.20.4**
- Fabric Loader: **0.15.11**
- Fabric API: **0.97.2+1.20.4**
- Fabric Loom: **1.7-SNAPSHOT**
- Java: **17**

These versions are defined in `/home/runner/work/Sharepoint/Sharepoint/gradle.properties` and `/home/runner/work/Sharepoint/Sharepoint/build.gradle`.

## Prerequisites

- Git
- JDK 17 (Temurin/OpenJDK recommended)

## Setup

Clone and enter the project:

```bash
git clone https://github.com/JonnyP0g/Sharepoint.git
cd Sharepoint
```

## Build

### Linux / macOS

```bash
./gradlew build
```

### Windows (PowerShell or CMD)

```bat
gradlew.bat build
```

## Run in development

### Client (recommended)

- Linux / macOS: `./gradlew runClient`
- Windows: `gradlew.bat runClient`

### Dedicated server

- Linux / macOS: `./gradlew runServer`
- Windows: `gradlew.bat runServer`

## Project layout

```text
.
├── build.gradle
├── gradle.properties
├── settings.gradle
├── gradle/wrapper/
├── src/
│   ├── main/
│   │   ├── java/com/jonnypog/sharepoint/
│   │   │   └── SharepointMod.java
│   │   └── resources/
│   │       ├── fabric.mod.json
│   │       └── assets/sharepoint/lang/en_us.json
│   ├── client/java/com/jonnypog/sharepoint/
│   │   └── SharepointClientMod.java
│   └── test/java/
└── README.md
```

## Where to implement waypoint sharing

Planned implementation points:

- Common initialization: `/home/runner/work/Sharepoint/Sharepoint/src/main/java/com/jonnypog/sharepoint/SharepointMod.java`
- Client-side UI and interactions: `/home/runner/work/Sharepoint/Sharepoint/src/client/java/com/jonnypog/sharepoint/SharepointClientMod.java`

Look for `TODO` markers in these files for the intended extension points.

## Notes

- A mod icon path is declared in `fabric.mod.json` (`assets/sharepoint/icon.png`). Add a PNG icon there before release.
- The repository currently does not implement Lunar waypoint parsing/transfer logic yet; this scaffold only provides a clean Fabric starting point.
