import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Standalone relay for Sharepoint's "share across networks" mode.
 * <p>
 * This is a plain Java program - it has nothing to do with Minecraft or
 * Fabric, and does not need to run on the same machine as your Minecraft
 * server (though that's a convenient place to run it, since you already
 * have a public IP and know how to forward a port there).
 * <p>
 * Why this exists: having one player open a listening port and having the
 * other player connect straight to it only works if that port is actually
 * reachable from the outside (LAN, or a manual port-forward). Most home
 * routers block unsolicited inbound connections by default, which is what
 * causes "connection refused". This relay flips that around: BOTH players'
 * game clients make outbound connections to this relay (which routers
 * essentially always allow), and the relay pairs them up and streams the
 * waypoint data through.
 * <p>
 * Protocol (plain text handshake, then raw bytes):
 *   Host connects, sends:  "HOST <code>\n"
 *   Relay replies:         "OK\n"                  (or an error + closes)
 *   ... relay waits (up to 5 min) for a JOIN with the same code ...
 *   Relay tells host:      "READY\n"
 *   Host then writes:      [4-byte length][waypoints.json bytes]
 *
 *   Joiner connects, sends: "JOIN <code>\n"
 *   Relay replies:           "OK\n"                (or NO_SUCH_CODE + closes)
 *   ... relay pipes the host's bytes straight through to the joiner ...
 *
 * There is no authentication beyond the shared code, and no encryption.
 * This is meant for sharing waypoints with people you trust, not as a
 * general-purpose secure relay. Pick reasonably hard-to-guess codes.
 * <p>
 * Usage:
 *   java RelayServer.java [port]      (default port 34600)
 * Requires a JDK 11+ (uses single-file source launching, no compile step).
 * Forward the chosen port on whichever machine you run this on.
 */
public class RelayServer {
    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Za-z0-9]{4,16}");
    private static final ConcurrentHashMap<String, CompletableFuture<Socket>> waiting = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 34600;
        try (ServerSocket server = new ServerSocket(port)) {
            System.out.println("Sharepoint relay listening on port " + port + ". Ctrl+C to stop.");
            while (true) {
                Socket socket = server.accept();
                Thread thread = new Thread(() -> handle(socket));
                thread.setDaemon(true);
                thread.start();
            }
        }
    }

    private static void handle(Socket socket) {
        try {
            socket.setSoTimeout(5 * 60_000);
            String request = readLine(socket);
            String[] parts = request.split(" ", 2);
            if (parts.length != 2 || !CODE_PATTERN.matcher(parts[1]).matches()) {
                writeLine(socket, "BAD_REQUEST");
                closeQuietly(socket);
                return;
            }

            String action = parts[0];
            String code = parts[1];

            if ("HOST".equals(action)) {
                handleHost(socket, code);
            } else if ("JOIN".equals(action)) {
                handleJoin(socket, code);
            } else {
                writeLine(socket, "BAD_REQUEST");
                closeQuietly(socket);
            }
        } catch (IOException e) {
            closeQuietly(socket);
        }
    }

    private static void handleHost(Socket socket, String code) throws IOException {
        if (waiting.containsKey(code)) {
            writeLine(socket, "CODE_IN_USE");
            closeQuietly(socket);
            return;
        }

        CompletableFuture<Socket> future = new CompletableFuture<>();
        waiting.put(code, future);
        writeLine(socket, "OK");
        System.out.println("Waiting for a JOIN with code '" + code + "'...");

        try {
            Socket peer = future.get(5, TimeUnit.MINUTES);
            writeLine(socket, "READY");
            System.out.println("Paired code '" + code + "' - relaying waypoints.");
            // Stream whatever the host writes straight through to the joiner.
            socket.getInputStream().transferTo(peer.getOutputStream());
        } catch (Exception e) {
            System.out.println("Code '" + code + "' timed out or failed: " + e.getMessage());
        } finally {
            waiting.remove(code, future);
            closeQuietly(socket);
        }
    }

    private static void handleJoin(Socket socket, String code) throws IOException {
        CompletableFuture<Socket> future = waiting.get(code);
        if (future == null) {
            writeLine(socket, "NO_SUCH_CODE");
            closeQuietly(socket);
            return;
        }

        writeLine(socket, "OK");
        // Hand this socket off to the HOST thread, which owns it from here on
        // (it does the actual byte-piping and closes both sockets when done).
        future.complete(socket);
    }

    private static void writeLine(Socket socket, String line) throws IOException {
        socket.getOutputStream().write((line + "\n").getBytes(StandardCharsets.UTF_8));
        socket.getOutputStream().flush();
    }

    private static String readLine(Socket socket) throws IOException {
        InputStream in = socket.getInputStream();
        StringBuilder builder = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') {
                break;
            }
            if (c != '\r') {
                builder.append((char) c);
            }
            if (builder.length() > 128) {
                break;
            }
        }
        return builder.toString();
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }
}
