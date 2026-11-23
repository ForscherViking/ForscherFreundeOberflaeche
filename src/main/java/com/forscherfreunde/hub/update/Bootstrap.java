package com.forscherfreunde.hub.update;

import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Optional;

/**
 * Startet beim Programmstart die neueste heruntergeladene und geprüfte Version – im selben Prozess
 * (kein zweiter JVM-Start), über einen eigenen ClassLoader. Die installierte JAR bleibt unverändert
 * und dient als sicherer Rückfall: Ist kein gültiges Update vorhanden, das Update beschädigt oder
 * ist es zweimal nicht erfolgreich gestartet, läuft einfach die installierte Version.
 *
 * <p>Darf keine AWT-/Swing-Klassen laden.
 */
public final class Bootstrap {

    private Bootstrap() { }

    /** @return {@code true}, wenn eine neuere Version übernommen hat – der Aufrufer muss dann sofort zurückkehren. */
    public static boolean launchStagedUpdate(String[] args) {
        if (!UpdateChecker.enabled()) return false;
        UpdateVerifier verifier;
        try {
            verifier = UpdateVerifier.fromResource();
        } catch (Throwable t) {
            return false;
        }
        if (verifier == null) return false;
        return launch(args, UpdateStore.standard(), verifier, AppVersion.current());
    }

    static boolean launch(String[] args, UpdateStore store, UpdateVerifier verifier, Version current) {
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try {
            Optional<UpdateStore.Staged> staged = store.newestUsable(current, verifier);
            if (staged.isEmpty()) return false;

            UpdateStore.Staged s = staged.get();
            store.recordAttempt(s.version());
            URLClassLoader loader = new URLClassLoader("ffhub-" + s.version(),
                    new URL[] {s.jar().toUri().toURL()}, ClassLoader.getPlatformClassLoader());
            Thread.currentThread().setContextClassLoader(loader);
            Class<?> main = Class.forName("com.forscherfreunde.hub.FFHub", true, loader);
            main.getMethod("main", String[].class).invoke(null, (Object) args);
            return true;
        } catch (InvocationTargetException e) {
            System.err.println("Update konnte nicht gestartet werden, nutze installierte Version: " + e.getCause());
        } catch (Throwable t) {
            System.err.println("Update-Start übersprungen: " + t);
        }
        Thread.currentThread().setContextClassLoader(previous);
        return false;
    }
}
