package com.forscherfreunde.hub.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Findet eingesteckte USB-Sticks (Wechseldatenträger). Kann kurz blockieren → im Hintergrund aufrufen. */
public final class UsbDrives {

    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    /** Ein beschreibbarer Wechseldatenträger. */
    public record Drive(Path root, String label, long freeBytes, long totalBytes) {
        /** z. B. „USB-STICK (E:)“ */
        public String displayName() {
            String r = root.toString();
            if (r.endsWith("\\") || r.endsWith("/")) r = r.substring(0, Math.max(1, r.length() - 1));
            String l = label == null || label.isBlank() ? "USB-Stick" : label;
            return WINDOWS ? l + " (" + r + ")" : l;
        }
    }

    private UsbDrives() { }

    public static List<Drive> detect() {
        List<Drive> out = new ArrayList<>();
        String override = System.getProperty("ffhub.usbDrives"); // für Tests: Pfade mit ; getrennt
        if (override != null) {
            for (String s : override.split(";")) {
                if (!s.isBlank()) addIfUsable(out, Paths.get(s.strip()), "USB-Stick");
            }
            return out;
        }
        if (WINDOWS) {
            File[] roots = File.listRoots();
            if (roots == null) return out;
            for (File f : roots) {
                String r = f.getPath().toUpperCase(Locale.ROOT);
                if (r.startsWith("A:") || r.startsWith("B:") || r.startsWith("C:")) continue; // Disketten/Systemplatte
                try {
                    FileStore store = Files.getFileStore(f.toPath());
                    if (!Boolean.TRUE.equals(store.getAttribute("volume:isRemovable"))) continue;
                    if (Boolean.TRUE.equals(store.getAttribute("volume:isCdrom"))) continue;
                    addIfUsable(out, f.toPath(), store.name());
                } catch (IOException | RuntimeException ignored) {
                    // leerer Kartenleser o. Ä.
                }
            }
        } else {
            // Linux: übliche Einhängepunkte für Wechselmedien
            String user = System.getProperty("user.name");
            for (Path base : new Path[] {Paths.get("/media", user), Paths.get("/run/media", user), Paths.get("/media")}) {
                if (!Files.isDirectory(base)) continue;
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(base)) {
                    for (Path p : ds) {
                        if (Files.isDirectory(p) && out.stream().noneMatch(d -> d.root().equals(p))) {
                            addIfUsable(out, p, p.getFileName().toString());
                        }
                    }
                } catch (IOException ignored) { }
            }
        }
        return out;
    }

    private static void addIfUsable(List<Drive> out, Path root, String label) {
        try {
            if (!Files.isDirectory(root) || !Files.isWritable(root)) return;
            FileStore store = Files.getFileStore(root);
            out.add(new Drive(root, label, store.getUsableSpace(), store.getTotalSpace()));
        } catch (IOException | RuntimeException ignored) { }
    }
}
