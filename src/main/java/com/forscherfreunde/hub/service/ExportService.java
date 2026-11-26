package com.forscherfreunde.hub.service;

import com.forscherfreunde.hub.model.Camp;
import com.forscherfreunde.hub.model.UserProfile;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Speichert das Ergebnis eines Kindes als ZIP auf einen USB-Stick: bei Code-Camps das Projekt
 * (ohne Build-Dateien und Minecraft-Laufzeit), bei Minecraft-Modulen die Welten.
 *
 * <p>Eine einzige ZIP-Datei ist auf langsamen Sticks viel schneller als tausende Einzeldateien
 * und lässt sich zu Hause per Doppelklick öffnen.
 */
public final class ExportService {

    public static final String FOLDER = "ForscherFreunde";

    /** Was exportiert wird. */
    public record Plan(List<Entry> files, long bytes, int worlds, int myFiles) {
        public boolean isEmpty() { return files.isEmpty(); }
    }

    /** Eine Datei und ihr Name im ZIP. */
    public record Entry(Path file, String zipName) { }

    private ExportService() { }

    /** Ermittelt die zu speichernden Dateien (nur lesen). */
    public static Plan plan(UserProfile p) throws IOException {
        Path source = p.isCodeProfile() ? p.modulePath() : MinecraftWorlds.worldsDir(p);
        String rootName = p.isCodeProfile() ? p.getModuleName() : "Welten";
        List<Entry> files = new ArrayList<>();
        long[] bytes = {0};
        int worlds = 0;
        if (Files.isDirectory(source)) {
            boolean code = p.isCodeProfile();
            Files.walkFileTree(source, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes a) {
                    if (dir.equals(source)) return FileVisitResult.CONTINUE;
                    return code && excludedCodeDir(source.relativize(dir)) ? FileVisitResult.SKIP_SUBTREE
                            : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes a) {
                    Path rel = source.relativize(file);
                    if (!a.isRegularFile() || file.getFileName().toString().equals("session.lock")) {
                        return FileVisitResult.CONTINUE;
                    }
                    if (code && rel.getNameCount() >= 1 && rel.getName(0).toString().equals("run")
                            && !(rel.getNameCount() > 2 && rel.getName(1).toString().equals("saves"))) {
                        return FileVisitResult.CONTINUE; // run/* außer run/saves
                    }
                    files.add(new Entry(file, rootName + "/" + zipPath(rel)));
                    bytes[0] += a.size();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException e) { return FileVisitResult.CONTINUE; }
            });
            if (!code) {
                try (var s = Files.list(source)) {
                    worlds = (int) s.filter(Files::isDirectory).count();
                }
            }
        }
        // Bilder, mBlock-/Blockbench-Projekte usw. aus dem Programme-Bereich
        int myFiles = 0;
        Path mine = ProgramLauncher.filesDir(p);
        if (Files.isDirectory(mine)) {
            try (var s = Files.walk(mine)) {
                for (Path f : (Iterable<Path>) s.filter(Files::isRegularFile)::iterator) {
                    files.add(new Entry(f, ProgramLauncher.FILES_FOLDER + "/" + zipPath(mine.relativize(f))));
                    bytes[0] += Files.size(f);
                    myFiles++;
                }
            }
        }
        return new Plan(files, bytes[0], worlds, myFiles);
    }

    private static String zipPath(Path rel) {
        return rel.toString().replace('\\', '/');
    }

    /**
     * Build-Ausgaben und die 42 MB große Minecraft-Startumgebung gehören nicht zum Ergebnis –
     * die selbst gebauten Testwelten in run/saves aber schon.
     */
    static boolean excludedCodeDir(Path rel) {
        String first = rel.getName(0).toString();
        if (first.equals("build") || first.equals(".gradle") || first.equals("out") || first.equals(".git")) return true;
        if (first.equals("run")) return rel.getNameCount() >= 2 && !rel.getName(1).toString().equals("saves");
        return false;
    }

    /**
     * Schreibt das ZIP nach {@code <ziel>/ForscherFreunde/}. Erst als {@code .part}, dann geprüft
     * und umbenannt – eine fertige {@code .zip} auf dem Stick ist also immer vollständig.
     *
     * @return Pfad der fertigen ZIP-Datei
     */
    public static Path export(UserProfile p, Path targetRoot, Progress progress) throws IOException {
        if (!p.isCodeProfile()) MinecraftWorlds.syncToProfile(p, progress);
        Plan plan = plan(p);
        if (plan.isEmpty()) {
            throw new IOException(p.isCodeProfile() ? "Im Projekt gibt es noch keine Dateien zum Speichern."
                    : "Es gibt noch keine Minecraft-Welten zum Speichern.");
        }
        long free = Files.getFileStore(targetRoot).getUsableSpace();
        if (free < plan.bytes() * 0.6 + 2_000_000) {
            throw new IOException(String.format("Auf dem USB-Stick ist nicht genug Platz (ca. %d MB nötig, %d MB frei).",
                    Math.round(plan.bytes() * 0.6 / 1e6) + 2, free / 1_000_000));
        }

        Path dir = targetRoot.resolve(FOLDER);
        Files.createDirectories(dir);
        Path zip = uniqueName(dir, baseName(p));
        Path part = zip.resolveSibling(zip.getFileName() + ".part");
        int written = 0;
        try {
            try (FileOutputStream fos = new FileOutputStream(part.toFile());
                 ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(fos, 1 << 18), StandardCharsets.UTF_8)) {
                out.setLevel(Deflater.BEST_SPEED); // schwache CPUs; Welten sind ohnehin schon komprimiert
                writeEntry(out, "LIESMICH.txt", readme(p).getBytes(StandardCharsets.UTF_8));
                written++;
                long done = 0;
                byte[] buf = new byte[1 << 16];
                for (Entry entry : plan.files()) {
                    Path f = entry.file();
                    ZipEntry e = new ZipEntry(entry.zipName());
                    e.setLastModifiedTime(Files.getLastModifiedTime(f));
                    out.putNextEntry(e);
                    try (InputStream in = Files.newInputStream(f)) {
                        int n;
                        while ((n = in.read(buf)) > 0) {
                            out.write(buf, 0, n);
                            done += n;
                        }
                    }
                    out.closeEntry();
                    written++;
                    progress.update("Wird auf den USB-Stick geschrieben …", plan.bytes() == 0 ? -1 : done / (double) plan.bytes());
                }
                out.finish();
                out.flush();
                fos.getFD().sync(); // wirklich auf den Stick schreiben, nicht nur in den Cache
            }
            progress.update("Wird überprüft …", -1);
            try (ZipFile check = new ZipFile(part.toFile(), StandardCharsets.UTF_8)) {
                if (check.size() != written) throw new IOException("Die Datei auf dem USB-Stick ist unvollständig.");
            }
            Files.move(part, zip, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.deleteIfExists(part);
            String msg = e.getMessage() != null && e.getMessage().toLowerCase().contains("space")
                    ? "Auf dem USB-Stick ist nicht genug Platz." : e.getMessage();
            throw new IOException("Speichern auf dem USB-Stick fehlgeschlagen: " + msg
                    + "\nWurde der Stick zu früh abgezogen?", e);
        }
        return zip;
    }

    static String baseName(UserProfile p) {
        Camp c = p.module();
        String camp = c != null ? c.title() : p.getModuleName();
        String raw = p.getName() + " - " + camp + " - " + LocalDate.now();
        return raw.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
    }

    private static Path uniqueName(Path dir, String base) {
        Path p = dir.resolve(base + ".zip");
        for (int i = 2; Files.exists(p); i++) p = dir.resolve(base + " (" + i + ").zip");
        return p;
    }

    private static void writeEntry(ZipOutputStream out, String name, byte[] data) throws IOException {
        out.putNextEntry(new ZipEntry(name));
        out.write(data);
        out.closeEntry();
    }

    private static String readme(UserProfile p) {
        Camp c = p.module();
        String camp = c != null ? c.title() : p.getModuleName();
        StringBuilder sb = new StringBuilder();
        sb.append("Hallo ").append(p.getName()).append("!\r\n\r\n")
          .append("Das ist dein Ergebnis aus dem ForscherFreunde-Camp \"").append(camp).append("\" vom ")
          .append(LocalDate.now()).append(".\r\n\r\n");
        if (p.isCodeProfile()) {
            sb.append("So machst du zu Hause weiter:\r\n")
              .append("1. Diese ZIP-Datei entpacken (Rechtsklick -> \"Alle extrahieren\").\r\n")
              .append("2. IntelliJ IDEA Community Edition installieren (kostenlos: jetbrains.com/idea).\r\n")
              .append("3. In IntelliJ \"Open\" waehlen und den Ordner \"").append(p.getModuleName())
              .append("\" oeffnen.\r\n")
              .append("4. Fehlt der Ordner \"run\" (Minecraft-Startumgebung), wird er beim ersten Start\r\n")
              .append("   ueber die Gradle-Aufgabe \"runClient\" automatisch neu angelegt.\r\n");
        } else {
            sb.append("So spielst du deine Welten zu Hause:\r\n")
              .append("1. Diese ZIP-Datei entpacken (Rechtsklick -> \"Alle extrahieren\").\r\n")
              .append("2. Windows-Taste + R druecken, %appdata%\\.minecraft\\saves eingeben, Enter.\r\n")
              .append("3. Die Ordner aus \"Welten\" in diesen saves-Ordner kopieren.\r\n")
              .append("4. Minecraft starten -> Einzelspieler: deine Welten sind da!\r\n");
        }
        sb.append("\r\nIm Ordner \"").append(ProgramLauncher.FILES_FOLDER)
          .append("\" findest du deine Bilder und Projekte aus Paint.NET, mBlock, Blockbench & Co. (falls vorhanden).\r\n");
        sb.append("\r\nViel Spass beim Weiterforschen!\r\nDein ForscherFreunde-Team\r\n");
        return sb.toString();
    }
}
