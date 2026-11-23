package com.forscherfreunde.hub.update;

import com.google.gson.GsonBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Werkzeug für Releases (wird nicht von der App selbst benutzt).
 *
 * <pre>
 * keygen  &lt;privaterSchlüssel.txt&gt; &lt;public-key.txt&gt;
 * release --version 2.0.1 --jar app.jar --url https://.../ForscherFreundeHub.jar --notes "…" --out dir
 *         (privater Schlüssel in Umgebungsvariable UPDATE_SIGNING_KEY)
 * </pre>
 */
public final class ReleaseTool {

    private ReleaseTool() { }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) usage();
        switch (args[0]) {
            case "keygen" -> keygen(args);
            case "release" -> release(args);
            default -> usage();
        }
    }

    private static void keygen(String[] args) throws Exception {
        if (args.length != 3) usage();
        Path priv = Paths.get(args[1]);
        Path pub = Paths.get(args[2]);
        if (Files.exists(priv)) {
            System.err.println("Abbruch: " + priv + " existiert bereits – ein vorhandener Schlüssel wird nicht überschrieben.");
            System.exit(1);
        }
        KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String privB64 = Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded());
        String pubB64 = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());
        Files.writeString(priv, privB64 + "\n", StandardCharsets.UTF_8);
        Files.createDirectories(pub.toAbsolutePath().getParent());
        Files.writeString(pub, "# Öffentlicher Schlüssel für Updates (Ed25519). Darf öffentlich sein.\n" + pubB64 + "\n",
                StandardCharsets.UTF_8);
        System.out.println("Öffentlicher Schlüssel → " + pub + " (committen)");
        System.out.println("Privater Schlüssel     → " + priv);
        System.out.println();
        System.out.println("Den Inhalt der privaten Datei als GitHub-Secret UPDATE_SIGNING_KEY hinterlegen");
        System.out.println("und die Datei danach sicher verwahren (z. B. Passwortmanager). NICHT committen.");
    }

    private static void release(String[] args) throws Exception {
        Map<String, String> o = new HashMap<>();
        for (int i = 1; i + 1 < args.length; i += 2) o.put(args[i], args[i + 1]);
        String key = System.getenv("UPDATE_SIGNING_KEY");
        if (key == null || key.isBlank()) throw new IllegalStateException("UPDATE_SIGNING_KEY ist nicht gesetzt");
        Version version = Version.parse(require(o, "--version"));
        Path jar = Paths.get(require(o, "--jar"));
        Path out = Paths.get(require(o, "--out"));
        Files.createDirectories(out);

        UpdateManifest m = new UpdateManifest(version.toString(), require(o, "--url"),
                UpdateVerifier.sha256(jar), Files.size(jar), o.getOrDefault("--notes", ""));
        m.validate();
        byte[] json = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
                .toJson(m).getBytes(StandardCharsets.UTF_8);

        PrivateKey pk = KeyFactory.getInstance("Ed25519")
                .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(key.strip())));
        Signature s = Signature.getInstance("Ed25519");
        s.initSign(pk);
        s.update(json);
        String sig = Base64.getEncoder().encodeToString(s.sign());

        Files.write(out.resolve("update.json"), json);
        Files.writeString(out.resolve("update.json.sig"), sig + "\n", StandardCharsets.US_ASCII);
        Files.copy(jar, out.resolve("ForscherFreundeHub.jar"), StandardCopyOption.REPLACE_EXISTING);
        System.out.println(new String(json, StandardCharsets.UTF_8));
    }

    private static String require(Map<String, String> o, String k) {
        String v = o.get(k);
        if (v == null || v.isBlank()) throw new IllegalArgumentException("Fehlt: " + k);
        return v;
    }

    private static void usage() {
        System.err.println("Verwendung: keygen <privat.txt> <public-key.txt> | release --version V --jar J --url U [--notes N] --out D");
        System.exit(2);
    }
}
