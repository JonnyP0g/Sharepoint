import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Short-lived, in-memory packs. Expose through an HTTPS reverse proxy/tunnel. */
public final class HostedRelayServer implements AutoCloseable {
    static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final HttpServer server;
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(8, 8, 0,
        TimeUnit.SECONDS, new ArrayBlockingQueue<>(32), new ThreadPoolExecutor.AbortPolicy());
    private final ScheduledExecutorService cleanup = Executors.newSingleThreadScheduledExecutor();
    private final Map<String, Pack> packs = new HashMap<>();
    private final Map<String, Integer> requests = new HashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;
    private final int ttlSeconds;
    private final long storageLimit;
    private long storedBytes;
    private long rateMinute = -1;
    private int requestCount;
    private record Pack(byte[] data, long expiresAt) {}

    HostedRelayServer(String bind, int port, int ttlSeconds, long storageLimit, Clock clock) throws IOException {
        this.clock = clock;
        this.ttlSeconds = ttlSeconds;
        this.storageLimit = storageLimit;
        server = HttpServer.create(new InetSocketAddress(bind, port), 32);
        server.setExecutor(workers);
        server.createContext("/", this::handle);
        cleanup.scheduleAtFixedRate(this::expire, 10, 10, TimeUnit.SECONDS);
    }

    public static void main(String[] args) throws IOException {
        System.setProperty("sun.net.httpserver.maxReqTime", "20");
        System.setProperty("sun.net.httpserver.maxRspTime", "20");
        System.setProperty("sun.net.httpserver.maxConnections", "128");
        System.setProperty("sun.net.httpserver.maxReqHeaders", "32");
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        String bind = System.getenv().getOrDefault("BIND_ADDRESS", "127.0.0.1");
        HostedRelayServer relay = new HostedRelayServer(bind, port, 600, 64L * 1024 * 1024, Clock.systemUTC());
        Runtime.getRuntime().addShutdownHook(new Thread(relay::close));
        relay.start();
        System.out.println("Sharepoint relay listening at http://" + bind + ":" + relay.port());
    }

    void start() { server.start(); }
    int port() { return server.getAddress().getPort(); }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            if (path.equals("/healthz") && method.equals("GET")) {
                reply(exchange, 200, "ok");
                return;
            }
            if (!allowRequest(exchange.getRemoteAddress().getAddress().getHostAddress())) {
                exchange.getResponseHeaders().set("Retry-After", "60");
                reply(exchange, 429, "Too many requests. Try again in a minute.");
                return;
            }
            if (path.equals("/v1/shares")) {
                if (!method.equals("POST")) {
                    exchange.getResponseHeaders().set("Allow", "POST");
                    reply(exchange, 405, "Use POST.");
                    return;
                }
                byte[] data = exchange.getRequestBody().readNBytes(MAX_BYTES + 1);
                if (data.length == 0 || data.length > MAX_BYTES) {
                    reply(exchange, 413, "Pack must be between 1 byte and 5 MiB.");
                    return;
                }
                String code = store(data);
                if (code == null) {
                    reply(exchange, 503, "Relay is full. Try again shortly.");
                    return;
                }
                exchange.getResponseHeaders().set("X-Share-TTL-Seconds", String.valueOf(ttlSeconds));
                reply(exchange, 201, code);
            } else if (path.matches("/v1/shares/[A-Z2-9]{10}")) {
                if (!method.equals("GET")) {
                    exchange.getResponseHeaders().set("Allow", "GET");
                    reply(exchange, 405, "Use GET.");
                    return;
                }
                Pack pack = find(path.substring("/v1/shares/".length()));
                if (pack == null) reply(exchange, 404, "Code not found or expired.");
                else {
                    exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
                    exchange.sendResponseHeaders(200, pack.data.length);
                    exchange.getResponseBody().write(pack.data);
                }
            } else reply(exchange, 404, "Not found.");
        } catch (IOException ignored) {
            // Never log payloads or share codes when clients disconnect.
        }
    }

    private synchronized boolean allowRequest(String address) {
        long minute = clock.millis() / 60_000;
        if (minute != rateMinute) {
            rateMinute = minute;
            requestCount = 0;
            requests.clear();
        }
        // Do not trust forwarded headers. Friends behind a proxy share its quota.
        if (++requestCount > 120) return false;
        return requests.merge(address, 1, Integer::sum) <= 60;
    }

    private synchronized String store(byte[] data) {
        expire();
        if (packs.size() >= 128 || storedBytes + data.length > storageLimit) return null;
        String code;
        do {
            StringBuilder value = new StringBuilder();
            for (int i = 0; i < 10; i++) value.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            code = value.toString();
        } while (packs.containsKey(code));
        packs.put(code, new Pack(data, clock.millis() + ttlSeconds * 1000L));
        storedBytes += data.length;
        return code;
    }

    private synchronized Pack find(String code) { expire(); return packs.get(code); }

    private synchronized void expire() {
        packs.values().removeIf(pack -> {
            if (pack.expiresAt > clock.millis()) return false;
            storedBytes -= pack.data.length;
            return true;
        });
    }

    private static void reply(HttpExchange exchange, int status, String text) throws IOException {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }

    @Override public void close() {
        server.stop(0);
        cleanup.shutdownNow();
        workers.shutdownNow();
    }
}
