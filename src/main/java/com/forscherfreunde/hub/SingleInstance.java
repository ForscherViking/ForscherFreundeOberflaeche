package com.forscherfreunde.hub;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Sorgt dafür, dass der Hub nur einmal pro Benutzer läuft.
 *
 * <p>Die erste Instanz hält eine Dateisperre und lauscht auf einem lokalen Port (nur 127.0.0.1).
 * Jede weitere Instanz bemerkt die Sperre, bittet die erste Instanz per Socket, ihr Fenster
 * nach vorne zu holen, und beendet sich sofort wieder – noch bevor AWT/Swing geladen wird.
 * Dadurch kostet ein versehentlicher Mehrfachstart nur wenige Millisekunden.
 *
 * <p>Wichtig: Diese Klasse darf keine AWT-/Swing-Klassen referenzieren.
 */
public final class SingleInstance {

    private static final String LOCK_FILE = "instance.lock";
    private static final String PORT_FILE = "instance.port";
    private static final String CMD_ACTIVATE = "ACTIVATE";

    // Starke Referenzen halten, sonst könnte der GC Kanal und Sperre freigeben.
    private final FileChannel channel;
    private final FileLock lock;
    private final ServerSocket server;
    private final String token;
    private volatile Runnable onActivate = () -> { };

    private SingleInstance(FileChannel channel, FileLock lock, ServerSocket server, String token) {
        this.channel = channel;
        this.lock = lock;
        this.server = server;
        this.token = token;
    }

    /**
     * Versucht, die Einzelinstanz-Sperre zu erhalten.
     *
     * @return die Sperre, wenn diese Instanz die erste ist; {@code null}, wenn bereits eine
     *         Instanz läuft (diese wurde dann benachrichtigt).
     */
    public static SingleInstance acquire(Path stateDir) {
        Path lockFile = stateDir.resolve(LOCK_FILE);
        Path portFile = stateDir.resolve(PORT_FILE);
        FileChannel channel = null;
        try {
            Files.createDirectories(stateDir);
            channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock lock = channel.tryLock();
            if (lock == null) {
                channel.close();
                notifyRunningInstance(portFile);
                return null;
            }

            ServerSocket server = new ServerSocket();
            server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 4);
            byte[] raw = new byte[16];
            new SecureRandom().nextBytes(raw);
            String token = HexFormat.of().formatHex(raw);
            Files.writeString(portFile, server.getLocalPort() + "\n" + token, StandardCharsets.UTF_8);

            SingleInstance instance = new SingleInstance(channel, lock, server, token);
            instance.startListener();
            return instance;
        } catch (IOException | RuntimeException e) {
            // Lieber ohne Sperre starten, als gar nicht zu starten.
            System.err.println("Einzelinstanz-Prüfung nicht möglich: " + e);
            if (channel != null) {
                try { channel.close(); } catch (IOException ignored) { }
            }
            return new SingleInstance(null, null, null, null);
        }
    }

    /** Wird (auf einem Hintergrund-Thread) aufgerufen, wenn ein weiterer Start versucht wurde. */
    public void setOnActivate(Runnable onActivate) {
        this.onActivate = onActivate == null ? () -> { } : onActivate;
    }

    private void startListener() {
        Thread t = new Thread(() -> {
            while (!server.isClosed()) {
                try (Socket s = server.accept()) {
                    s.setSoTimeout(1000);
                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                    String line = in.readLine();
                    if ((CMD_ACTIVATE + " " + token).equals(line)) {
                        onActivate.run();
                    }
                } catch (IOException ignored) {
                    // nächster Versuch
                }
            }
        }, "ffhub-single-instance");
        t.setDaemon(true);
        t.start();
    }

    private static void notifyRunningInstance(Path portFile) {
        // Die erste Instanz schreibt die Portdatei evtl. gerade erst – kurz erneut versuchen.
        for (int attempt = 0; attempt < 15; attempt++) {
            try {
                String[] parts = Files.readString(portFile, StandardCharsets.UTF_8).trim().split("\\R");
                int port = Integer.parseInt(parts[0].trim());
                String token = parts.length > 1 ? parts[1].trim() : "";
                try (Socket s = new Socket()) {
                    s.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 300);
                    Writer out = new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8);
                    out.write(CMD_ACTIVATE + " " + token + "\n");
                    out.flush();
                    return;
                }
            } catch (IOException | RuntimeException e) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    boolean isLocked() {
        return lock != null && lock.isValid() && channel != null;
    }
}
