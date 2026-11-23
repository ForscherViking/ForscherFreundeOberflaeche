package com.forscherfreunde.hub.update;

import com.forscherfreunde.hub.AppPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Ablage heruntergeladener Versionen im Benutzerordner ({@code %LOCALAPPDATA%/ForscherFreundeHub/updates}),
 * denn der Installationsordner unter „Programme“ ist für Kinder-Konten nicht beschreibbar.
 *
 * <p>Pro Version: {@code app-X.jar}, {@code app-X.json}, {@code app-X.json.sig} (die JSON wird zuletzt
 * geschrieben – existiert sie, ist die Version vollständig), {@code app-X.attempts} (Startversuche) und
 * {@code app-X.healthy} (Version ist mindestens einmal erfolgreich gestartet).
 */
public final class UpdateStore {

    /** Nach so vielen Startversuchen ohne Erfolg wird eine Version übersprungen (automatischer Rollback). */
    static final int MAX_FAILED_STARTS = 2;

    private final Path dir;

    public UpdateStore(Path dir) {
        this.dir = dir;
    }

    public static UpdateStore standard() {
        return new UpdateStore(AppPaths.stateDir().resolve("updates"));
    }

    public Path dir() { return dir; }

    Path jar(Version v) { return dir.resolve("app-" + v + ".jar"); }

    Path manifest(Version v) { return dir.resolve("app-" + v + ".json"); }

    Path signature(Version v) { return dir.resolve("app-" + v + ".json.sig"); }

    private Path attemptsFile(Version v) { return dir.resolve("app-" + v + ".attempts"); }

    private Path healthyFile(Version v) { return dir.resolve("app-" + v + ".healthy"); }

    /** Eine geprüfte, startbereite Version. */
    public record Staged(Version version, Path jar, UpdateManifest manifest) { }

    /** Alle vollständig heruntergeladenen Versionen, neueste zuerst. */
    public List<Version> staged() {
        List<Version> list = new ArrayList<>();
        if (!Files.isDirectory(dir)) return list;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "app-*.json")) {
            for (Path p : ds) {
                String n = p.getFileName().toString();
                try {
                    list.add(Version.parse(n.substring(4, n.length() - 5)));
                } catch (IllegalArgumentException ignored) { }
            }
        } catch (IOException ignored) { }
        list.sort(Comparator.reverseOrder());
        return list;
    }

    /** Prüft eine abgelegte Version vollständig (Signatur + Prüfsumme). */
    public Staged verify(Version v, UpdateVerifier verifier) throws IOException, GeneralSecurityException {
        UpdateManifest m = verifier.verifyManifest(Files.readAllBytes(manifest(v)), Files.readAllBytes(signature(v)));
        if (!m.version().equals(v)) throw new GeneralSecurityException("Version passt nicht zum Dateinamen");
        verifier.verifyJar(jar(v), m);
        return new Staged(v, jar(v), m);
    }

    /** Neueste gültige Version, die neuer als {@code current} ist und nicht als defekt gilt. */
    public Optional<Staged> newestUsable(Version current, UpdateVerifier verifier) {
        for (Version v : staged()) {
            if (!v.isNewerThan(current)) break;
            if (isBad(v)) continue;
            try {
                return Optional.of(verify(v, verifier));
            } catch (IOException | GeneralSecurityException e) {
                System.err.println("Update " + v + " verworfen: " + e.getMessage());
                delete(v);
            }
        }
        return Optional.empty();
    }

    public boolean isBad(Version v) {
        return attempts(v) >= MAX_FAILED_STARTS && !Files.exists(healthyFile(v));
    }

    public int attempts(Version v) {
        try {
            return Integer.parseInt(Files.readString(attemptsFile(v), StandardCharsets.UTF_8).strip());
        } catch (IOException | NumberFormatException e) {
            return 0;
        }
    }

    public void recordAttempt(Version v) {
        try {
            Files.createDirectories(dir);
            Files.writeString(attemptsFile(v), String.valueOf(attempts(v) + 1), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Startversuch nicht speicherbar: " + e);
        }
    }

    public void markHealthy(Version v) {
        try {
            if (Files.isDirectory(dir) && !Files.exists(healthyFile(v))) Files.writeString(healthyFile(v), "ok");
        } catch (IOException ignored) { }
    }

    /** Löscht Versionen, die älter oder gleich {@code current} sind (außer der laufenden) und behält max. 2. */
    public void cleanup(Version current) {
        List<Version> all = staged();
        int kept = 0;
        for (Version v : all) {
            boolean keep = v.isNewerThan(current) ? kept++ < 2 : v.equals(current);
            if (!keep) delete(v);
        }
    }

    void delete(Version v) {
        for (Path p : new Path[] {manifest(v), signature(v), jar(v), attemptsFile(v), healthyFile(v)}) {
            try { Files.deleteIfExists(p); } catch (IOException ignored) { } // läuft die JAR noch, später erneut
        }
    }
}
