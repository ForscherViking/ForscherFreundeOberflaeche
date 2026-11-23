package com.forscherfreunde.hub.update;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * Sucht im Hintergrund nach Updates und lädt sie herunter. Installiert wird erst beim nächsten Start
 * (siehe {@link Bootstrap}) – so wird nie mitten in der Arbeit etwas ausgetauscht.
 */
public final class UpdateChecker {

    public static final String DEFAULT_URL =
            "https://github.com/ForscherViking/ForscherFreundeOberflaeche/releases/latest/download/update.json";
    private static final Duration INTERVAL = Duration.ofHours(4);

    private final URI manifestUrl;
    private final UpdateStore store;
    private final UpdateVerifier verifier;
    private final Version current;
    private final HttpClient http;
    /** Nur für Tests: leitet die (https-)Download-Adresse auf einen lokalen Server um. */
    java.util.function.UnaryOperator<URI> downloadRedirect = u -> u;

    public UpdateChecker(URI manifestUrl, UpdateStore store, UpdateVerifier verifier, Version current) {
        this.manifestUrl = manifestUrl;
        this.store = store;
        this.verifier = verifier;
        this.current = current;
        this.http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15))
                .proxy(ProxySelector.getDefault())
                .build();
    }

    /** Update-Adresse: {@code -Dffhub.updateUrl}, Umgebungsvariable {@code FFHUB_UPDATE_URL} oder GitHub. */
    public static URI configuredUrl() {
        String u = System.getProperty("ffhub.updateUrl");
        if (u == null || u.isBlank()) u = System.getenv("FFHUB_UPDATE_URL");
        return URI.create(u == null || u.isBlank() ? DEFAULT_URL : u.strip());
    }

    public static boolean enabled() {
        return !"false".equalsIgnoreCase(System.getProperty("ffhub.updates"));
    }

    /**
     * Startet die regelmäßige Prüfung auf einem Hintergrund-Thread. Die erste Prüfung erfolgt mit
     * zufälliger Verzögerung, damit nicht alle 130 Laptops zur selben Sekunde laden.
     */
    public void startBackground(Consumer<UpdateManifest> onReady) {
        Thread t = new Thread(() -> {
            long delay = 20_000 + ThreadLocalRandom.current().nextLong(100_000);
            while (true) {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    return;
                }
                try {
                    checkOnce().ifPresent(onReady);
                } catch (IOException | GeneralSecurityException | RuntimeException e) {
                    System.err.println("Update-Prüfung fehlgeschlagen: " + e.getMessage());
                } catch (InterruptedException e) {
                    return;
                }
                delay = INTERVAL.toMillis();
            }
        }, "ffhub-updater");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    /** Eine Prüfung: liefert das Manifest, wenn ein neueres, geprüftes Update bereitliegt. */
    public Optional<UpdateManifest> checkOnce() throws IOException, InterruptedException, GeneralSecurityException {
        byte[] json = fetch(manifestUrl, UpdateVerifier.MAX_MANIFEST_BYTES);
        byte[] sig = fetch(URI.create(manifestUrl + ".sig"), 4096);
        UpdateManifest m = verifier.verifyManifest(json, sig);
        Version v = m.version();
        if (!v.isNewerThan(current) || store.isBad(v)) return Optional.empty();

        if (store.staged().contains(v)) {
            try {
                store.verify(v, verifier);
                return Optional.of(m);
            } catch (IOException | GeneralSecurityException e) {
                store.delete(v); // beschädigt → neu laden
            }
        }

        Files.createDirectories(store.dir());
        Path tmp = Files.createTempFile(store.dir(), "download-", ".part");
        try {
            download(downloadRedirect.apply(URI.create(m.url())), tmp, m.size());
            verifier.verifyJar(tmp, m);
            Files.move(tmp, store.jar(v), StandardCopyOption.REPLACE_EXISTING);
            Files.write(store.signature(v), sig);
            Files.write(store.manifest(v), json); // zuletzt: markiert die Version als vollständig
        } finally {
            Files.deleteIfExists(tmp);
        }
        store.cleanup(current);
        return Optional.of(m);
    }

    private byte[] fetch(URI uri, int maxBytes) throws IOException, InterruptedException {
        HttpResponse<InputStream> r = http.send(request(uri), HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream in = r.body()) {
            if (r.statusCode() != 200) throw new IOException("HTTP " + r.statusCode() + " für " + uri);
            byte[] data = in.readNBytes(maxBytes + 1);
            if (data.length > maxBytes) throw new IOException("Antwort zu groß: " + uri);
            return data;
        }
    }

    private void download(URI uri, Path target, long expectedSize) throws IOException, InterruptedException {
        HttpResponse<InputStream> r = http.send(request(uri), HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream in = r.body(); OutputStream out = Files.newOutputStream(target)) {
            if (r.statusCode() != 200) throw new IOException("HTTP " + r.statusCode() + " für " + uri);
            byte[] buf = new byte[64 * 1024];
            long total = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > expectedSize) throw new IOException("Download größer als angekündigt");
                out.write(buf, 0, n);
            }
        }
    }

    private static HttpRequest request(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(3))
                .header("User-Agent", "ForscherFreundeHub-Updater")
                .GET().build();
    }
}
