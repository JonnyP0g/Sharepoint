import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import com.jonnyp0g.sharepoint.HostedClientChecks;

public class HostedRelayIntegrationTest {
    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static int checks;
    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
    static HttpResponse<byte[]> request(String base, String path, String method, byte[] body) throws Exception {
        return CLIENT.send(HttpRequest.newBuilder(URI.create(base + path))
            .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body))
            .build(), HttpResponse.BodyHandlers.ofByteArray());
    }
    static String text(HttpResponse<byte[]> value) { return new String(value.body(), StandardCharsets.UTF_8); }

    public static void main(String[] args) throws Exception {
        MutableClock clock = new MutableClock();
        byte[] payload = "{\"waypoints\":[{\"name\":\"Home\",\"x\":10}]}".getBytes(StandardCharsets.UTF_8);
        try (HostedRelayServer relay = new HostedRelayServer("127.0.0.1", 0, 600, 64L * 1024 * 1024, clock)) {
            relay.start();
            String base = "http://127.0.0.1:" + relay.port();
            check(request(base, "/healthz", "GET", null).statusCode() == 200, "Health endpoint");
            var upload = request(base, "/v1/shares", "POST", payload);
            check(upload.statusCode() == 201, "Upload accepted");
            String code = text(upload);
            check(code.matches("[A-Z2-9]{10}"), "Random code format");
            check(upload.headers().firstValue("X-Share-TTL-Seconds").orElse("").equals("600"), "TTL header");
            var download = request(base, "/v1/shares/" + code, "GET", null);
            check(java.util.Arrays.equals(payload, download.body()), "Exact bytes round trip");
            check(download.headers().firstValue("Cache-Control").orElse("").equals("no-store"), "No caching");
            check(request(base, "/v1/shares/" + code, "GET", null).statusCode() == 200, "Downloads can retry");
            check(request(base, "/v1/shares", "GET", null).statusCode() == 405, "Wrong method");
            check(request(base, "/v1/shares/" + code, "POST", payload).statusCode() == 405, "No overwriting codes");
            check(request(base, "/v1/shares/BAD", "GET", null).statusCode() == 404, "Malformed code");
            check(request(base, "/v1/shares/AAAAAAAAAA", "GET", null).statusCode() == 404, "Unknown code");
            check(request(base, "/v1/shares", "POST", new byte[0]).statusCode() == 413, "Empty payload rejected");
            check(request(base, "/v1/shares", "POST", new byte[HostedRelayServer.MAX_BYTES + 1]).statusCode() == 413, "Oversized payload rejected");
            HostedClientChecks.run(base);
            clock.millis += 600_001;
            check(request(base, "/v1/shares/" + code, "GET", null).statusCode() == 404, "Expired code rejected");
            var uploads = new ArrayList<CompletableFuture<String>>();
            for (int i = 0; i < 12; i++) uploads.add(CompletableFuture.supplyAsync(() -> {
                try {
                    var r = request(base, "/v1/shares", "POST", payload);
                    if (r.statusCode() != 201) throw new AssertionError("Concurrent upload rejected");
                    return text(r);
                } catch (Exception e) { throw new RuntimeException(e); }
            }));
            var unique = new HashSet<String>();
            for (var future : uploads) unique.add(future.join());
            check(unique.size() == 12, "Concurrent shares have distinct codes");
            boolean limited = false;
            for (int i = 0; i < 61; i++) {
                var r = request(base, "/v1/shares/AAAAAAAAAA", "GET", null);
                if (r.statusCode() == 429) { limited = true; break; }
            }
            check(limited, "Request rate bounded");
            check(request(base, "/healthz", "GET", null).statusCode() == 200, "Health available while rate limited");
            clock.millis += 60_000;
            check(request(base, "/v1/shares/AAAAAAAAAA", "GET", null).statusCode() == 404, "Rate limit resets");
        }
        clock = new MutableClock();
        try (HostedRelayServer relay = new HostedRelayServer("127.0.0.1", 0, 2, payload.length, clock)) {
            relay.start();
            String base = "http://127.0.0.1:" + relay.port();
            check(request(base, "/v1/shares", "POST", payload).statusCode() == 201, "Fits storage budget");
            check(request(base, "/v1/shares", "POST", payload).statusCode() == 503, "Storage ceiling enforced");
            clock.millis += 2001;
            check(request(base, "/v1/shares", "POST", payload).statusCode() == 201, "Expiry releases memory budget");
        }
        CLIENT.close();
        System.out.println("PASS: " + checks + " relay checks plus client/file checks.");
    }
    static final class MutableClock extends Clock {
        volatile long millis = 1_800_000_000_000L;
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return Instant.ofEpochMilli(millis); }
        public long millis() { return millis; }
    }
}
