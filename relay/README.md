# Hosted waypoint relay

Run this on the **same PC as your Minecraft server**. It is a separate Java process;
it does not go in Minecraft's `mods` or `plugins` folder. The Minecraft tunnel and
the relay tunnel serve different protocols and must have separate endpoints.

## 1. Start the relay on Windows

Install Java **21 or newer** (the mod's Java 25 also works), then double-click
`start-relay.bat` in this folder. Leave it running while friends use the relay.
Alternatively, in PowerShell from this folder:

```powershell
./start-relay.ps1
```

It listens only on `127.0.0.1:8080`. Visit <http://127.0.0.1:8080/healthz>:
the response should be `ok`. There is no web interface. Stop with Ctrl+C.
To use another port: `./start-relay.ps1 -Port 8081` (update the tunnel too).

## 2. Give it a public HTTPS address

### Existing Playit setup

Playit's **HTTPS tunnels require Premium**. Its HTTPS tunnel does not terminate
TLS itself: use Caddy on this PC, following the
[official Playit HTTPS guide](https://playit.gg/support/https-tunnel/).

1. Create a separate **HTTPS** tunnel in Playit using the agent on this PC and a
   Playit/custom domain. Keep the origin settings specified in that guide.
2. Install Caddy from its [official downloads](https://caddyserver.com/download).
3. Copy `Caddyfile.example` to `Caddyfile`, replacing `your-relay.playit.plus`
   with the HTTPS tunnel's actual domain. Keep `127.0.0.1:8080` as the upstream.
4. Run `caddy run --config Caddyfile` in this folder. Keep Caddy, Playit and the
   relay running. Follow Playit's guide for the local TLS origin port.
5. Check `https://YOUR-RELAY-DOMAIN/healthz` from outside your home network.

Do not paste your Minecraft TCP tunnel address into the relay settings.

### Temporary test without Playit Premium

Install `cloudflared` using [Cloudflare's official instructions](https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/downloads/).
While the relay is running:

```powershell
cloudflared tunnel --url http://127.0.0.1:8080
```

Use the printed `https://...trycloudflare.com` URL. A
[Quick Tunnel](https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/do-more-with-tunnels/trycloudflare/)
is for testing, has no uptime guarantee and gets a new URL when restarted. Keep
it running with the relay. For ongoing use, configure a named Cloudflare tunnel
with a stable domain and HTTP origin `http://127.0.0.1:8080` instead.

### Cloud hosting instead of your PC

`Dockerfile` packages the relay for a container host with HTTPS termination.
From the project root: `docker build -t sharepoint-relay ./relay`.
`render.yaml` is a Render Blueprint using the **Free service** plan, not
automatically deployed. Free instances sleep after 15 idle minutes and may take
about a minute to wake up; the client allows up to two minutes for a request.
See [Render's Docker documentation](https://render.com/docs/docker).
The health path is `/healthz`; the host supplies `PORT`.
Use one instance: storage is in memory, so multiple independent replicas cannot
resolve one another's share codes. Redeploys and restarts invalidate active codes.

## 3. Configure the mod

In **Settings**, save the full public HTTPS URL. Both players use the same URL.
On the host PC only, `http://127.0.0.1:8080` works for local testing.

To distribute a mod with a stable relay already configured, run from the project
root (Java 25 required):

```powershell
./gradlew.bat build '-PrelayUrl=https://YOUR-RELAY-DOMAIN'
```

Distribute `build/libs/sharepoint-1.0.0.jar`. A player's saved `relayUrl` overrides
the bundled URL; update Settings if that player previously used another relay.
The default build currently bundles `https://theorem-dui-dvd-elliott.trycloudflare.com`
from `gradle.properties`. This Quick Tunnel URL changes when the tunnel restarts;
update the default and rebuild, or change Settings, if that happens.

## Sharing behavior and limits

- POST `/v1/shares` uploads 1 byte–5 MiB of opaque data and returns a random
  10-character code with `X-Share-TTL-Seconds: 600`.
- GET `/v1/shares/CODE` downloads the pack. Codes work for multiple downloads
  until expiry, allowing friends to retry. The uploader can disconnect after upload.
- Packs live only in relay memory for 10 minutes; expired data is purged at most
  10 seconds later. Restarting clears all packs. No waypoint files are saved on
  the host, and this program does not log share codes or contents.
- Up to 128 packs / 64 MiB stored. Payloads, worker count, queue size, connection
  count, request duration and request rates are bounded. 60 requests/minute per
  direct address and 120/minute globally. Tunnel users may share one address and
  quota; proxy-provided client headers are not trusted.
- HTTPS protects transit to your TLS endpoint. The relay/tunnel operator can
  access data; this is not end-to-end encryption. Anyone with a code can download
  its pack before expiry. No accounts are required.
- The client validates UTF-8 and JSON before saving received packs. Import is
  explicit and replaces the waypoint file after backing up the original.

## Tests

From the project root: `./gradlew.bat testHostedRelay` (also part of `build`).
Tests use an ephemeral loopback server, temporary files and a controllable clock;
they do not need a hosting account or modify real Minecraft data.

`RelayServer.java` is the old plain TCP implementation, retained only for legacy
clients. It is **not** used by the new screens, launchers or Docker image. Its old
commands are now under `/sharepoint legacy-relay`; do not expose it as the HTTPS
service described here.
