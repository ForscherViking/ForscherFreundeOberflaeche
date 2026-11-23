package com.forscherfreunde.hub.update;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Die Version des gerade laufenden Codes (aus {@code version.properties}, beim Bauen eingesetzt). */
public final class AppVersion {

    private static final Version CURRENT = load();

    private AppVersion() { }

    public static Version current() { return CURRENT; }

    private static Version load() {
        try (InputStream in = AppVersion.class.getResourceAsStream("/version.properties")) {
            if (in != null) {
                Properties p = new Properties();
                p.load(in);
                return Version.parse(p.getProperty("version", "0"));
            }
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("Version nicht lesbar: " + e);
        }
        return Version.parse("0");
    }
}
