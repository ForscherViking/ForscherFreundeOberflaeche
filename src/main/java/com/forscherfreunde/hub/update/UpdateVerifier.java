package com.forscherfreunde.hub.update;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Prüft Updates: Die {@code update.json} muss mit dem privaten Release-Schlüssel (Ed25519) signiert
 * sein, und die heruntergeladene JAR muss exakt die darin genannte SHA-256-Prüfsumme haben.
 * Der öffentliche Schlüssel steckt in der App ({@code /update/public-key.txt}).
 */
public final class UpdateVerifier {

    static final long MAX_JAR_BYTES = 64L * 1024 * 1024;
    static final int MAX_MANIFEST_BYTES = 64 * 1024;
    private static final Gson GSON = new Gson();

    private final PublicKey key;

    public UpdateVerifier(PublicKey key) {
        this.key = key;
    }

    /** @return Prüfer mit dem eingebauten Schlüssel oder {@code null}, wenn keiner eingerichtet ist. */
    public static UpdateVerifier fromResource() {
        try (InputStream in = UpdateVerifier.class.getResourceAsStream("/update/public-key.txt")) {
            if (in == null) return null;
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            StringBuilder b64 = new StringBuilder();
            for (String line : text.split("\\R")) {
                String l = line.strip();
                if (!l.isEmpty() && !l.startsWith("#")) b64.append(l);
            }
            if (b64.length() == 0) return null;
            return new UpdateVerifier(decodePublicKey(b64.toString()));
        } catch (IOException | GeneralSecurityException | IllegalArgumentException e) {
            System.err.println("Update-Schlüssel ungültig, Updates deaktiviert: " + e);
            return null;
        }
    }

    public static PublicKey decodePublicKey(String base64) throws GeneralSecurityException {
        return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));
    }

    /** Prüft die Signatur und liefert das Manifest. */
    public UpdateManifest verifyManifest(byte[] json, byte[] signatureFile) throws GeneralSecurityException {
        byte[] sig;
        try {
            sig = Base64.getDecoder().decode(new String(signatureFile, StandardCharsets.US_ASCII).strip());
        } catch (IllegalArgumentException e) {
            throw new GeneralSecurityException("Signatur nicht lesbar", e);
        }
        Signature s = Signature.getInstance("Ed25519");
        s.initVerify(key);
        s.update(json);
        if (!s.verify(sig)) throw new GeneralSecurityException("Signatur ungültig");
        try {
            UpdateManifest m = GSON.fromJson(new String(json, StandardCharsets.UTF_8), UpdateManifest.class);
            if (m == null) throw new GeneralSecurityException("Manifest leer");
            m.validate();
            return m;
        } catch (JsonParseException | IllegalArgumentException e) {
            throw new GeneralSecurityException("Manifest ungültig: " + e.getMessage(), e);
        }
    }

    /** Prüft Größe und Prüfsumme der JAR. */
    public void verifyJar(Path jar, UpdateManifest m) throws IOException, GeneralSecurityException {
        if (Files.size(jar) != m.size()) throw new GeneralSecurityException("Dateigröße stimmt nicht");
        if (!sha256(jar).equals(m.sha256())) throw new GeneralSecurityException("Prüfsumme stimmt nicht");
    }

    public static String sha256(Path file) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }
}
