# Deploy on Render Free

1. Put this project in your own GitHub repository (private is fine). At minimum,
   upload `render.yaml` at the repository root and the `relay` folder containing
   `Dockerfile` and `HostedRelayServer.java`. No game data or tunnel binaries are needed.
2. Sign in at https://dashboard.render.com and choose **New > Blueprint**.
3. Connect that repository, select its branch, and use `render.yaml` as the
   Blueprint path. Confirm the service uses the **Free** instance type.
4. Deploy and wait for the service to show **Live**. Copy the assigned
   `https://....onrender.com` address; do not guess it from the service name.
5. Open that URL with `/healthz` appended. It should return `ok`.
6. Put the base URL (without `/healthz`) into the mod's Settings on both clients.
   To bundle it, set `relayUrl` in `gradle.properties` and run `gradlew.bat build`.
   Existing non-empty Settings override bundled defaults and must be updated.

The blueprint uses Docker, the `relay` build context and `/healthz` health checks.
Render supplies the HTTP port and public HTTPS. No database or paid disk is needed.
Free hosting sleeps after 15 minutes of inactivity; the mod waits up to two minutes
for a response. Free services may also restart, clearing active codes. Packs are
temporary (10 minutes) regardless of hosting plan. Your PC and Cloudflare tunnel
can be off once the Render deployment is live and clients use its address.

Official instructions: https://render.com/docs/infrastructure-as-code
Free limits: https://render.com/docs/free
