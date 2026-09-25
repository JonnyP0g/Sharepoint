package com.jonnyp0g.sharepoint;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodySubscriber;
import java.net.http.HttpResponse.BodySubscribers;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/** HTTPS transport with bounded bodies, deadlines, and no redirect following. */
final class HostedRelayClient {
    static final int MAX_BYTES = 5 * 1024 * 1024;
    // Free hosts can take about a minute to wake up after inactivity.
    private static final int REQUEST_TIMEOUT_SECONDS = 120;
    record Upload(String code, int ttlSeconds) {}

    static String validateUrl(String value) {
        URI uri;
        try { uri = URI.create(value.trim()); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Enter a valid HTTPS relay URL."); }
        boolean local = "http".equals(uri.getScheme()) &&
            ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()) || "[::1]".equals(uri.getHost()));
        if ((!"https".equals(uri.getScheme()) && !local) || uri.getHost() == null ||
            uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null ||
            (uri.getPort() != -1 && (uri.getPort() < 1 || uri.getPort() > 65535)) ||
            !(uri.getPath().isEmpty() || uri.getPath().equals("/"))) {
            throw new IllegalArgumentException("Use an HTTPS host URL. HTTP only works on localhost.");
        }
        return uri.toString().replaceAll("/+$", "");
    }

    Upload upload(String base, byte[] data) throws IOException {
        if (data.length == 0 || data.length > MAX_BYTES) throw new IOException("Packs must be between 1 byte and 5 MiB.");
        HttpResponse<byte[]> response = request(base, "/v1/shares", data, 1024);
        expect(response, 201);
        String code = new String(response.body(), StandardCharsets.UTF_8).trim();
        if (!code.matches("[A-Z2-9]{10}")) throw new IOException("Relay returned an invalid share code.");
        int ttl;
        try { ttl = Integer.parseInt(response.headers().firstValue("X-Share-TTL-Seconds").orElse("600")); }
        catch (NumberFormatException e) { throw new IOException("Relay returned an invalid expiry."); }
        if (ttl < 1 || ttl > 86400) throw new IOException("Relay returned an invalid expiry.");
        return new Upload(code, ttl);
    }

    byte[] download(String base, String code) throws IOException {
        String normalized = normalizeCode(code);
        if (!normalized.matches("[A-Z2-9]{10}")) throw new IOException("Enter the 10-character share code.");
        HttpResponse<byte[]> response = request(base, "/v1/shares/" + normalized, null, MAX_BYTES);
        expect(response, 200);
        if (response.body().length == 0) throw new IOException("The shared pack is empty.");
        return response.body();
    }

    static String normalizeCode(String code) { return code.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT); }

    private HttpResponse<byte[]> request(String base, String path, byte[] data, int limit) throws IOException {
        String validated;
        try { validated = validateUrl(base); }
        catch (IllegalArgumentException e) { throw new IOException(e.getMessage()); }
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(validated + path))
            .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS)).header("Accept", "application/octet-stream, text/plain");
        if (data == null) builder.GET();
        else builder.header("Content-Type", "application/octet-stream").POST(HttpRequest.BodyPublishers.ofByteArray(data));
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER).build()) {
            var pending = client.sendAsync(builder.build(), info -> new LimitedBody(limit));
            try {
                return pending.get(REQUEST_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                pending.cancel(true);
                client.shutdownNow();
                throw new IOException("Relay did not wake up within two minutes. Try again.", e);
            } catch (java.util.concurrent.ExecutionException e) {
                if (e.getCause() instanceof IOException io) throw io;
                throw new IOException("Could not connect to the relay.", e.getCause());
            } catch (InterruptedException e) {
                pending.cancel(true);
                client.shutdownNow();
                throw e;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Transfer cancelled.", e);
        }
    }

    private static void expect(HttpResponse<byte[]> response, int status) throws IOException {
        if (response.statusCode() == status) return;
        throw new IOException(switch (response.statusCode()) {
            case 404 -> "Code not found or expired. Ask for a new code.";
            case 413 -> "This pack is too large (maximum 5 MiB).";
            case 429 -> "Too many requests. Wait a minute and try again.";
            case 503 -> "The relay is full or starting up. Try again shortly.";
            default -> "Relay request failed (HTTP " + response.statusCode() + ").";
        });
    }

    private static final class LimitedBody implements BodySubscriber<byte[]> {
        private final BodySubscriber<byte[]> delegate = BodySubscribers.ofByteArray();
        private final int limit;
        private Flow.Subscription subscription;
        private int received;
        private boolean failed;
        LimitedBody(int limit) { this.limit = limit; }
        public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
        public void onSubscribe(Flow.Subscription value) { subscription = value; delegate.onSubscribe(value); }
        public void onNext(List<ByteBuffer> items) {
            if (failed) return;
            for (ByteBuffer item : items) {
                if (item.remaining() > limit - received) {
                    failed = true;
                    subscription.cancel();
                    delegate.onError(new IOException("Relay response exceeds the size limit."));
                    return;
                }
                received += item.remaining();
            }
            delegate.onNext(items);
        }
        public void onError(Throwable error) { if (!failed) delegate.onError(error); }
        public void onComplete() { if (!failed) delegate.onComplete(); }
    }
}
