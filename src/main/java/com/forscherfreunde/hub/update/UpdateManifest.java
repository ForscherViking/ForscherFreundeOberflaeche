package com.forscherfreunde.hub.update;

/** Inhalt von {@code update.json} – wird nur nach erfolgreicher Signaturprüfung verwendet. */
public final class UpdateManifest {
    String version;
    String url;
    String sha256;
    long size;
    String notes;

    public UpdateManifest() { }

    public UpdateManifest(String version, String url, String sha256, long size, String notes) {
        this.version = version;
        this.url = url;
        this.sha256 = sha256;
        this.size = size;
        this.notes = notes;
    }

    public Version version() { return Version.parse(version); }

    public String url() { return url; }

    public String sha256() { return sha256; }

    public long size() { return size; }

    public String notes() { return notes == null ? "" : notes.strip(); }

    /** Prüft, ob alle Felder plausibel sind. */
    void validate() {
        version();
        if (url == null || !url.startsWith("https://")) throw new IllegalArgumentException("Update-URL muss https sein");
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Ungültige Prüfsumme");
        if (size <= 0 || size > UpdateVerifier.MAX_JAR_BYTES) throw new IllegalArgumentException("Ungültige Größe");
    }
}
