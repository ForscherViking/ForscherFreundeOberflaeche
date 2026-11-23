package com.forscherfreunde.hub.update;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/** Startet den Hub neu (für die Installation eines Updates). */
public final class Restart {

    private Restart() { }

    /**
     * Startet eine neue Instanz, die wartet, bis diese beendet ist. Gestartet wird immer über die
     * installierte JAR – deren {@link Bootstrap} wählt dann die neueste geprüfte Version.
     */
    public static void spawnNewInstance() throws IOException {
        Path bin = Paths.get(System.getProperty("java.home"), "bin");
        Path java = bin.resolve("javaw.exe");
        if (!Files.exists(java)) java = bin.resolve("java.exe");
        if (!Files.exists(java)) java = bin.resolve("java");

        List<String> cmd = new ArrayList<>(List.of(java.toString(),
                "-XX:+UseSerialGC", "-Xms24m", "-Xmx192m", "-XX:TieredStopAtLevel=1", "-Xss512k"));
        for (String key : System.getProperties().stringPropertyNames()) {
            if (key.startsWith("ffhub.") && !key.equals("ffhub.waitForLock")) {
                cmd.add("-D" + key + "=" + System.getProperty(key));
            }
        }
        cmd.add("-Dffhub.waitForLock=true");
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add("com.forscherfreunde.hub.FFHub");
        new ProcessBuilder(cmd)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
    }
}
