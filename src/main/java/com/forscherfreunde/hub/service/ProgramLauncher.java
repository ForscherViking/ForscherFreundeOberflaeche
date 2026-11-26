package com.forscherfreunde.hub.service;

import com.forscherfreunde.hub.model.Program;
import com.forscherfreunde.hub.model.UserProfile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Startet Programme aus dem Programme-Bereich so, dass die Kinder in ihrem eigenen Ordner arbeiten:
 * {@code <Profil>\Meine Dateien\<Programm-Ordner>}. Programme mit „neue Datei“ (z. B. Paint.NET) bekommen
 * eine frisch angelegte, leere Datei in diesem Ordner – Strg+S speichert dann ohne Dialog dorthin.
 */
public final class ProgramLauncher {

    public static final String FILES_FOLDER = "Meine Dateien";
    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    /** Vom Hub angelegte leere Dateien → (Größe, Zeitstempel) beim Anlegen; unverändert = wieder löschen. */
    private static final Map<Path, long[]> BLANKS = new ConcurrentHashMap<>();

    /** Wie wurde gestartet? */
    public enum How { PROGRAM, PROTOCOL, BROWSER }

    private ProgramLauncher() { }

    public static Path filesDir(UserProfile p) { return p.workDir().resolve(FILES_FOLDER); }

    public static Path folder(Program prog, UserProfile p) { return filesDir(p).resolve(prog.folderName()); }

    /** Erster vorhandener Programmpfad oder {@code null}. */
    public static Path findExecutable(Program prog) {
        for (String raw : prog.paths()) {
            try {
                Path p = Paths.get(Program.expand(raw));
                if (Files.isRegularFile(p)) return p;
            } catch (RuntimeException ignored) { }
        }
        return null;
    }

    /** Kann das Programm auf diesem Rechner gestartet werden – und wie? {@code null} = gar nicht. */
    public static How availability(Program prog) {
        if (findExecutable(prog) != null) return How.PROGRAM;
        if (WINDOWS && prog.protocol() != null) return How.PROTOCOL;
        if (prog.webUrl() != null) return How.BROWSER;
        return null;
    }

    /** Nächster freier Name ohne Endung: „bild_1“, „bild_2“ … (streng) bzw. „Bild 1“ … */
    public static String suggestNewName(Program prog, UserProfile p) {
        Program.NeueDatei nd = prog.newFile();
        String base = nd == null ? "datei" : nd.name();
        String sep = prog.strictNames() ? "_" : " ";
        if (prog.strictNames()) base = FileNames.suggestStrict(base).isEmpty() ? "bild" : FileNames.suggestStrict(base);
        Path dir = folder(prog, p);
        String ext = nd == null ? "" : "." + nd.endung();
        for (int i = 1; i < 10_000; i++) {
            String n = base + sep + i;
            if (!Files.exists(dir.resolve(n + ext))) return n;
        }
        return base + sep + System.currentTimeMillis();
    }

    /** Legt eine leere Datei mit dem nächsten freien Namen an. */
    public static Path createBlankFile(Program prog, UserProfile p) throws IOException {
        return createBlankFile(prog, p, suggestNewName(prog, p));
    }

    /** Legt eine leere Datei {@code name + Endung} im Ordner des Kindes an. */
    public static Path createBlankFile(Program prog, UserProfile p, String name) throws IOException {
        Program.NeueDatei nd = prog.newFile();
        if (nd == null) throw new IllegalStateException("Programm hat keine Vorlage für neue Dateien");
        String problem = FileNames.validate(name, prog.strictNames());
        if (problem != null) throw new IOException(problem);
        Path dir = folder(prog, p);
        Files.createDirectories(dir);
        Path file = dir.resolve(name + "." + nd.endung());
        if (Files.exists(file)) throw new IOException("Es gibt schon eine Datei „" + file.getFileName() + "“. Bitte wähle einen anderen Namen.");
        String fmt = nd.endung().equals("jpg") || nd.endung().equals("jpeg") ? "jpg"
                : nd.endung().equals("bmp") ? "bmp" : "png";
        if (List.of("png", "jpg", "jpeg", "bmp").contains(nd.endung())) {
            BufferedImage img = new BufferedImage(nd.breite(), nd.hoehe(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, nd.breite(), nd.hoehe());
            g.dispose();
            if (!ImageIO.write(img, fmt, file.toFile())) throw new IOException("Bildformat nicht unterstützt: " + fmt);
        } else {
            Files.createFile(file);
        }
        BLANKS.put(file, new long[] {Files.size(file), Files.getLastModifiedTime(file).toMillis()});
        return file;
    }

    /**
     * Startet das Programm. {@code file} wird – falls angegeben – darin geöffnet. Arbeitsordner ist
     * immer der Ordner des Kindes.
     */
    public static How open(Program prog, UserProfile p, Path file) throws IOException {
        Path dir = folder(prog, p);
        Files.createDirectories(dir);
        Path exe = findExecutable(prog);
        if (exe != null) {
            List<String> cmd = new ArrayList<>();
            cmd.add(exe.toString());
            if (file != null) cmd.add(file.toString());
            new ProcessBuilder(cmd).directory(dir.toFile())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            return How.PROGRAM;
        }
        if (WINDOWS && prog.protocol() != null) {
            // z. B. paintdotnet:"C:\...\Bild 1.png" – funktioniert auch mit der Store-Version.
            // Der Pfad geht über eine Umgebungsvariable, damit Leerzeichen/Anführungszeichen sicher sind.
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c",
                    "start \"\" " + prog.protocol() + "\"%FFHUB_DATEI%\"");
            pb.environment().put("FFHUB_DATEI", file != null ? file.toString() : "");
            pb.directory(dir.toFile()).start();
            return How.PROTOCOL;
        }
        if (prog.webUrl() != null) {
            openInBrowser(prog.webUrl());
            return How.BROWSER;
        }
        throw new IOException("„" + prog.title() + "“ ist auf diesem Laptop nicht installiert.");
    }

    /** Öffnet eine Web-App möglichst als eigenes Fenster ohne Browserleiste (Edge/Chrome im App-Modus). */
    static void openInBrowser(String url) throws IOException {
        if (WINDOWS) {
            for (String raw : new String[] {
                    "%ProgramFiles(x86)%\\Microsoft\\Edge\\Application\\msedge.exe",
                    "%ProgramFiles%\\Microsoft\\Edge\\Application\\msedge.exe",
                    "%ProgramFiles%\\Google\\Chrome\\Application\\chrome.exe",
                    "%LOCALAPPDATA%\\Google\\Chrome\\Application\\chrome.exe"}) {
                Path b = Paths.get(Program.expand(raw));
                if (Files.isRegularFile(b)) {
                    new ProcessBuilder(b.toString(), "--app=" + url).start();
                    return;
                }
            }
        }
        if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
            java.awt.Desktop.getDesktop().browse(URI.create(url));
        } else {
            new ProcessBuilder(WINDOWS ? new String[] {"cmd", "/c", "start", "\"\"", url} : new String[] {"xdg-open", url}).start();
        }
    }

    /** Löscht vom Hub angelegte leere Dateien, die nie bearbeitet wurden. @return Anzahl gelöschter Dateien */
    public static int cleanupUntouchedBlanks() {
        int n = 0;
        for (Map.Entry<Path, long[]> e : BLANKS.entrySet()) {
            Path f = e.getKey();
            try {
                if (!Files.exists(f)) {
                    BLANKS.remove(f);
                } else if (Files.size(f) == e.getValue()[0]
                        && Files.getLastModifiedTime(f).toMillis() == e.getValue()[1]) {
                    Files.delete(f);
                    BLANKS.remove(f);
                    n++;
                } else {
                    BLANKS.remove(f); // bearbeitet → bleibt
                }
            } catch (IOException ignored) {
                // evtl. noch geöffnet; beim nächsten Mal erneut versuchen
            }
        }
        return n;
    }

    /**
     * Benennt eine Datei des Kindes um (Endung bleibt). Das Programm muss geschlossen sein, sonst würde
     * es beim nächsten Speichern wieder unter dem alten Namen schreiben.
     */
    public static Path rename(Program prog, Path file, String newName) throws IOException {
        String problem = FileNames.validate(newName, prog.strictNames());
        if (problem != null) throw new IOException(problem);
        if (prog.isRunning()) {
            throw new IOException("Bitte schließe zuerst " + prog.title() + " – sonst speichert es wieder unter dem alten Namen.");
        }
        String ext = ext(file);
        Path target = file.resolveSibling(ext.isEmpty() ? newName : newName + "." + ext);
        if (target.equals(file)) return file;
        // Nur Groß-/Kleinschreibung geändert (Windows!) → über einen Zwischennamen
        if (Files.exists(target) && !Files.isSameFile(target, file)) {
            throw new IOException("Es gibt schon eine Datei „" + target.getFileName() + "“.");
        }
        Path tmp = file.resolveSibling(".umbenennen-" + System.nanoTime());
        Files.move(file, tmp);
        try {
            Files.move(tmp, target);
        } catch (IOException e) {
            Files.move(tmp, file);
            throw e;
        }
        long[] blank = BLANKS.remove(file);
        if (blank != null) BLANKS.put(target, blank);
        return target;
    }

    /** Eine Datei des Kindes mit dem Programm, zu dem sie gehört. */
    public record KidFile(Path path, Program program, FileTime modified) { }

    /** Die zuletzt bearbeiteten Dateien des Kindes (neueste zuerst). */
    public static List<KidFile> recentFiles(UserProfile p, List<Program> programs, int max) {
        List<KidFile> out = new ArrayList<>();
        for (Program prog : programs) {
            Path dir = folder(prog, p);
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> s = Files.list(dir)) {
                s.filter(Files::isRegularFile)
                 .filter(f -> !BLANKS.containsKey(f))
                 .filter(f -> prog.extensions().isEmpty() || prog.extensions().contains(ext(f)))
                 .forEach(f -> {
                     try {
                         out.add(new KidFile(f, prog, Files.getLastModifiedTime(f)));
                     } catch (IOException ignored) { }
                 });
            } catch (IOException ignored) { }
        }
        out.sort(Comparator.comparing(KidFile::modified).reversed());
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    static String ext(Path f) {
        String n = f.getFileName().toString();
        int i = n.lastIndexOf('.');
        return i < 0 ? "" : n.substring(i + 1).toLowerCase(Locale.ROOT);
    }
}
