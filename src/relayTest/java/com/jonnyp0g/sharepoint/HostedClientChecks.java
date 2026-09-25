package com.jonnyp0g.sharepoint;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class HostedClientChecks {
    private static int checks;
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
    interface Action { void run() throws Exception; }
    private static void fails(Action action, String message) throws Exception {
        try { action.run(); } catch (java.io.IOException | IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError(message);
    }
    public static void run(String base) throws Exception {
        HostedRelayClient client = new HostedRelayClient();
        byte[] original = "{\"waypoints\":[]}".getBytes(StandardCharsets.UTF_8);
        byte[] changed = "{\"waypoints\":[{\"name\":\"Mine\",\"x\":1}]}".getBytes(StandardCharsets.UTF_8);
        var uploaded = client.upload(base, changed);
        String friendly = uploaded.code().substring(0, 5).toLowerCase() + "-" + uploaded.code().substring(5).toLowerCase();
        check(Arrays.equals(changed, client.download(base, " " + friendly + " ")), "Client code normalization and round trip");
        fails(() -> client.download(base, "bad"), "Invalid code rejected locally");
        fails(() -> client.download(base, "AAAAAAAAAA"), "Missing code is readable failure");
        fails(() -> client.upload(base, new byte[HostedRelayClient.MAX_BYTES + 1]), "Oversize rejected locally");
        check(HostedRelayClient.validateUrl("https://example.com/").equals("https://example.com"), "URL normalization");
        for (String url : new String[]{"http://example.com", "https://user:pass@example.com", "https://example.com/path", "https://example.com?q=1", "file:///tmp/a", "https://example.com:0", ""}) {
            fails(() -> HostedRelayClient.validateUrl(url), "Unsafe URL rejected: " + url);
        }
        Path folder = Files.createTempDirectory("sharepoint-test-");
        try {
            Path target = folder.resolve("waypoints.json");
            Files.write(target, original);
            WaypointShareService service = new WaypointShareService(folder);
            String a = service.saveReceived(changed);
            String b = service.saveReceived(changed);
            check(!a.equals(b), "Received packs never overwrite each other");
            check(Arrays.equals(original, Files.readAllBytes(target)), "Receiving leaves current waypoints untouched");
            service.importWaypoints(a);
            check(Arrays.equals(changed, Files.readAllBytes(target)), "Explicit import replaces current file");
            try (var files = Files.list(folder.resolve("sharepoint-backups"))) {
                Path backup = files.findFirst().orElseThrow();
                check(Arrays.equals(original, Files.readAllBytes(backup)), "Backup preserves original bytes");
            }
            fails(() -> service.saveReceived("not-json".getBytes(StandardCharsets.UTF_8)), "Non-JSON rejected");
            fails(() -> service.saveReceived("{name:'not strict JSON'}".getBytes(StandardCharsets.UTF_8)), "Lenient JSON rejected");
            fails(() -> service.saveReceived("{} {}".getBytes(StandardCharsets.UTF_8)), "Trailing JSON rejected");
            fails(() -> service.saveReceived(new byte[]{'{', '"', (byte)0xff, '"', ':', '0', '}'}), "Invalid UTF-8 rejected");
            fails(() -> service.writeWaypointsBytes("null".getBytes(StandardCharsets.UTF_8)), "Invalid root rejected");
            check(Arrays.equals(changed, Files.readAllBytes(target)), "Failed import preserves current data");
            String deep = "[".repeat(70) + "0" + "]".repeat(70);
            fails(() -> service.saveReceived(deep.getBytes(StandardCharsets.UTF_8)), "Deep JSON rejected");
            SharepointConfig config = new SharepointConfig(folder);
            config.setRelayUrl(base);
            check(new SharepointConfig(folder).getRelayUrl().equals(base), "Relay URL survives reload");
            fails(() -> config.setRelayUrl("http://remote.example"), "Config rejects insecure remote URL");
        } finally {
            try (var paths = Files.walk(folder)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
        HttpServer bad = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        bad.createContext("/v1/shares", exchange -> {
            try (exchange) {
                exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().write(new byte[HostedRelayClient.MAX_BYTES + 1]);
            } catch (java.io.IOException ignored) { }
        });
        bad.start();
        try { fails(() -> client.download("http://127.0.0.1:" + bad.getAddress().getPort(), "AAAAAAAAAA"), "Untrusted oversized response bounded"); }
        finally { bad.stop(0); }
        HttpServer waking = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        waking.createContext("/v1/shares", exchange -> {
            try (exchange) {
                // Exceeds the previous 30-second deadline, like a sleeping free host.
                try { Thread.sleep(31_000); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                exchange.sendResponseHeaders(200, changed.length);
                exchange.getResponseBody().write(changed);
            }
        });
        waking.start();
        try {
            check(Arrays.equals(changed, client.download("http://127.0.0.1:" + waking.getAddress().getPort(), "AAAAAAAAAA")),
                "Sleeping relay can respond after the old 30-second deadline");
        } finally { waking.stop(0); }
        System.out.println("PASS: " + checks + " client/file checks.");
    }
}
