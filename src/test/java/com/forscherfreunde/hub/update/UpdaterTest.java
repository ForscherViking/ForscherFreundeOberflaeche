package com.forscherfreunde.hub.update;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdaterTest {

    @TempDir Path tmp;
    private HttpServer server;
    private final Map<String, byte[]> files = new HashMap<>();
    private KeyPair keys;
    private UpdateVerifier verifier;
    private UpdateStore store;
    private URI manifestUrl;

    @BeforeEach
    void setUp() throws Exception {
        keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        verifier = new UpdateVerifier(keys.getPublic());
        store = new UpdateStore(tmp.resolve("updates"));
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", ex -> {
            byte[] body = files.get(ex.getRequestURI().getPath());
            if (body == null) {
                ex.sendResponseHeaders(404, -1);
            } else {
                ex.sendResponseHeaders(200, body.length);
                try (OutputStream o = ex.getResponseBody()) { o.write(body); }
            }
            ex.close();
        });
        server.start();
        manifestUrl = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/latest/update.json");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void versionOrdering() {
        assertTrue(Version.parse("2.0.10").isNewerThan(Version.parse("2.0.9")));
        assertTrue(Version.parse("v2.1").isNewerThan(Version.parse("2.0.99")));
        assertEquals(Version.parse("2.0"), Version.parse("2.0.0"));
        assertThrows(IllegalArgumentException.class, () -> Version.parse("2.0-beta"));
    }

    @Test
    void downloadsVerifiesAndStagesNewerVersion() throws Exception {
        byte[] jar = "neue-app".getBytes(StandardCharsets.UTF_8);
        publish("2.0.1", jar, jar, keys);
        Optional<UpdateManifest> m = checker("2.0.0").checkOnce();
        assertTrue(m.isPresent());
        assertEquals("Fehler behoben", m.get().notes());
        Optional<UpdateStore.Staged> s = store.newestUsable(Version.parse("2.0.0"), verifier);
        assertTrue(s.isPresent());
        assertEquals(Version.parse("2.0.1"), s.get().version());
        assertTrue(checker("2.0.1").checkOnce().isEmpty(), "Gleiche Version ist kein Update");
    }

    @Test
    void rejectsTamperedJar() throws Exception {
        publish("2.0.1", "original".getBytes(), "manipul.".getBytes(), keys);
        assertThrows(GeneralSecurityException.class, () -> checker("2.0.0").checkOnce());
        assertTrue(store.staged().isEmpty());
    }

    @Test
    void rejectsForeignSignature() throws Exception {
        KeyPair attacker = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] jar = "boese".getBytes();
        publish("9.9.9", jar, jar, attacker);
        assertThrows(GeneralSecurityException.class, () -> checker("2.0.0").checkOnce());
        assertTrue(store.staged().isEmpty());
    }

    @Test
    void rollsBackAfterTwoFailedStarts() throws Exception {
        byte[] jar = "app".getBytes();
        publish("2.0.1", jar, jar, keys);
        checker("2.0.0").checkOnce();
        Version v = Version.parse("2.0.1");
        store.recordAttempt(v);
        assertTrue(store.newestUsable(Version.parse("2.0.0"), verifier).isPresent(), "ein Fehlstart ist noch ok");
        store.recordAttempt(v);
        assertTrue(store.newestUsable(Version.parse("2.0.0"), verifier).isEmpty(), "nach 2 Fehlstarts übersprungen");
        assertTrue(checker("2.0.0").checkOnce().isEmpty(), "defekte Version wird nicht erneut angeboten");
        store.markHealthy(v);
        assertTrue(store.newestUsable(Version.parse("2.0.0"), verifier).isPresent());
    }

    @Test
    void bootstrapLaunchesStagedVersionInSameProcess() throws Exception {
        byte[] jar = buildFakeApp("2.0.5");
        publish("2.0.5", jar, jar, keys);
        checker("2.0.0").checkOnce();
        System.clearProperty("ffhub.test.launched");
        assertTrue(Bootstrap.launch(new String[0], store, verifier, Version.parse("2.0.0")));
        assertEquals("2.0.5", System.getProperty("ffhub.test.launched"));
        assertEquals(1, store.attempts(Version.parse("2.0.5")));
        assertFalse(Bootstrap.launch(new String[0], store, verifier, Version.parse("2.0.5")), "nicht neuer → nichts tun");
    }

    @Test
    void bootstrapFallsBackWhenUpdateIsBroken() throws Exception {
        byte[] jar = "kein-jar".getBytes();
        publish("2.0.7", jar, jar, keys);
        checker("2.0.0").checkOnce();
        assertFalse(Bootstrap.launch(new String[0], store, verifier, Version.parse("2.0.0")));
    }

    // ------------------------------------------------------------------ Hilfen

    private UpdateChecker checker(String current) {
        UpdateChecker c = new UpdateChecker(manifestUrl, store, verifier, Version.parse(current));
        // https ist im Manifest Pflicht; im Test kommt die Datei vom lokalen Server.
        c.downloadRedirect = u -> URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                + u.getPath());
        return c;
    }

    /** Veröffentlicht ein Release; {@code served} darf zum Test von {@code announced} abweichen. */
    private void publish(String version, byte[] announced, byte[] served, KeyPair signer) throws Exception {
        Path a = Files.write(tmp.resolve("announced.bin"), announced);
        String url = "https://example.invalid/v" + version + "/app.jar";
        UpdateManifest m = new UpdateManifest(version, url, UpdateVerifier.sha256(a), announced.length, "Fehler behoben");
        byte[] json = new Gson().toJson(m).getBytes(StandardCharsets.UTF_8);
        Signature s = Signature.getInstance("Ed25519");
        s.initSign(signer.getPrivate());
        s.update(json);
        files.put("/latest/update.json", json);
        files.put("/latest/update.json.sig", Base64.getEncoder().encode(s.sign()));
        files.put("/v" + version + "/app.jar", served);
    }

    /** Baut eine Mini-JAR mit einer eigenen FFHub-Klasse, die beim Start eine Systemeigenschaft setzt. */
    private byte[] buildFakeApp(String version) throws IOException {
        Path src = tmp.resolve("src/com/forscherfreunde/hub/FFHub.java");
        Files.createDirectories(src.getParent());
        Files.writeString(src, "package com.forscherfreunde.hub; public class FFHub { public static void main(String[] a) {"
                + " System.setProperty(\"ffhub.test.launched\", \"" + version + "\"); } }");
        Path classes = tmp.resolve("classes");
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        assertEquals(0, javac.run(null, null, null, "-d", classes.toString(), src.toString()));
        Path jar = tmp.resolve("fake.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry("com/forscherfreunde/hub/FFHub.class"));
            out.write(Files.readAllBytes(classes.resolve("com/forscherfreunde/hub/FFHub.class")));
            out.closeEntry();
        }
        return Files.readAllBytes(jar);
    }
}
