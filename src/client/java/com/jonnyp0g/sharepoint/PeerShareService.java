package com.jonnyp0g.sharepoint;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.function.Consumer;

/**
 * Direct player-to-player waypoint transfer over a plain TCP socket.
 * <p>
 * One player "hosts" (opens a listening socket and waits for a peer to
 * connect), the other player "receives" (connects to the host's address
 * and pulls the current waypoints file). This does not go through
 * Minecraft's own networking or require a server-side mod - it only
 * requires the two players' machines to be able to reach each other
 * (e.g. same LAN, a Minecraft server IP both are already connected to
 * that also allows the port through, or a VPN/port-forward for two
 * friends playing remotely).
 * <p>
 * There is no authentication or encryption - this is meant for sharing
 * with a friend you already trust, not for exposing to the open internet.
 */
class PeerShareService {
    private final WaypointShareService service;
    private Thread hostThread;
    private volatile boolean hosting;

    PeerShareService(WaypointShareService service) {
        this.service = service;
    }

    boolean isHosting() {
        return hosting;
    }

    /** Starts listening on the given port and sends the current waypoints to the first peer that connects. */
    synchronized void host(int port, Consumer<String> statusCallback) {
        if (hosting) {
            statusCallback.accept("Already hosting a share.");
            return;
        }

        hosting = true;
        hostThread = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket()) {
                serverSocket.setReuseAddress(true);
                serverSocket.bind(new InetSocketAddress(port));
                serverSocket.setSoTimeout(120_000); // give up after 2 minutes if nobody connects

                statusCallback.accept("Hosting on " + describeLocalAddresses() + ":" + port + " - waiting for a peer (2 min timeout)...");

                try (Socket client = serverSocket.accept()) {
                    byte[] data = service.readWaypointsBytes();
                    try (DataOutputStream out = new DataOutputStream(client.getOutputStream())) {
                        out.writeInt(data.length);
                        out.write(data);
                        out.flush();
                    }
                    statusCallback.accept("Sent waypoints to " + client.getInetAddress().getHostAddress() + ".");
                }
            } catch (SocketTimeoutException e) {
                statusCallback.accept("Hosting stopped: no peer connected in time.");
            } catch (IOException e) {
                statusCallback.accept("Hosting failed: " + e.getMessage());
            } finally {
                hosting = false;
            }
        }, "sharepoint-host");
        hostThread.setDaemon(true);
        hostThread.start();
    }

    synchronized void stopHosting() {
        hosting = false;
        if (hostThread != null) {
            hostThread.interrupt();
            hostThread = null;
        }
    }

    /**
     * Connects to a hosting peer, downloads their waypoints, and stores the
     * result as a named share (so the player can review/import it explicitly
     * rather than it silently overwriting their live waypoints).
     */
    void receive(String hostAddress, int port, String saveAsName, Consumer<String> statusCallback) {
        Thread receiveThread = new Thread(() -> {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(hostAddress, port), 10_000);
                try (DataInputStream in = new DataInputStream(socket.getInputStream())) {
                    int length = in.readInt();
                    if (length < 0 || length > 50 * 1024 * 1024) {
                        throw new IOException("Refusing to read implausible payload size: " + length);
                    }
                    byte[] data = new byte[length];
                    in.readFully(data);

                    Path saved = service.saveShare(saveAsName, data);
                    statusCallback.accept("Received waypoints from " + hostAddress + ", saved as share '" + saveAsName + "' (" + saved + "). Import it from the list when ready.");
                }
            } catch (IOException e) {
                statusCallback.accept("Receive failed: " + e.getMessage());
            }
        }, "sharepoint-receive");
        receiveThread.setDaemon(true);
        receiveThread.start();
    }

    /**
     * Hosts a share via a relay server instead of listening directly.
     * This works even when the receiving player is behind NAT/a firewall
     * that would otherwise cause "connection refused", because both sides
     * only ever make outbound connections - to the relay, never to each
     * other directly. See relay/RelayServer.java for the relay itself.
     */
    void hostViaRelay(String relayHost, int relayPort, String code, Consumer<String> statusCallback) {
        Thread thread = new Thread(() -> {
            try (Socket relay = new Socket()) {
                relay.connect(new InetSocketAddress(relayHost, relayPort), 10_000);
                relay.setSoTimeout(6 * 60_000);

                writeLine(relay, "HOST " + code);
                String response = readLine(relay);
                if (!"OK".equals(response)) {
                    statusCallback.accept("Relay rejected code '" + code + "': " + response);
                    return;
                }

                statusCallback.accept("Waiting for your friend to join with code " + code + " (5 min timeout)...");
                String ready = readLine(relay);
                if (!"READY".equals(ready)) {
                    statusCallback.accept("Relay error: " + ready);
                    return;
                }

                byte[] data = service.readWaypointsBytes();
                try (DataOutputStream out = new DataOutputStream(relay.getOutputStream())) {
                    out.writeInt(data.length);
                    out.write(data);
                    out.flush();
                }
                statusCallback.accept("Sent waypoints via relay using code " + code + ".");
            } catch (IOException e) {
                statusCallback.accept("Relay hosting failed: " + e.getMessage());
            }
        }, "sharepoint-relay-host");
        thread.setDaemon(true);
        thread.start();
    }

    /** Joins a relay-hosted share using the code the host gave you. */
    void receiveViaRelay(String relayHost, int relayPort, String code, String saveAsName, Consumer<String> statusCallback) {
        Thread thread = new Thread(() -> {
            try (Socket relay = new Socket()) {
                relay.connect(new InetSocketAddress(relayHost, relayPort), 10_000);
                relay.setSoTimeout(6 * 60_000);

                writeLine(relay, "JOIN " + code);
                String response = readLine(relay);
                if (!"OK".equals(response)) {
                    statusCallback.accept("Relay rejected code '" + code + "': " + response);
                    return;
                }

                try (DataInputStream in = new DataInputStream(relay.getInputStream())) {
                    int length = in.readInt();
                    if (length < 0 || length > 50 * 1024 * 1024) {
                        throw new IOException("Refusing to read implausible payload size: " + length);
                    }
                    byte[] data = new byte[length];
                    in.readFully(data);

                    Path saved = service.saveShare(saveAsName, data);
                    statusCallback.accept("Received waypoints via relay, saved as share '" + saveAsName + "' (" + saved + "). Import it from the list when ready.");
                }
            } catch (IOException e) {
                statusCallback.accept("Relay receive failed: " + e.getMessage());
            }
        }, "sharepoint-relay-receive");
        thread.setDaemon(true);
        thread.start();
    }

    private static void writeLine(Socket socket, String line) throws IOException {
        socket.getOutputStream().write((line + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        socket.getOutputStream().flush();
    }

    private static String readLine(Socket socket) throws IOException {
        java.io.InputStream in = socket.getInputStream();
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

    private static String describeLocalAddresses() {
        try {
            Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            StringBuilder builder = new StringBuilder();
            while (interfaces != null && interfaces.hasMoreElements()) {
                java.net.NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) {
                    continue;
                }
                Enumeration<java.net.InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress address = addresses.nextElement();
                    if (address instanceof java.net.Inet4Address) {
                        if (builder.length() > 0) {
                            builder.append("/");
                        }
                        builder.append(address.getHostAddress());
                    }
                }
            }
            return builder.length() > 0 ? builder.toString() : "your-local-ip";
        } catch (IOException e) {
            return "your-local-ip";
        }
    }
}
