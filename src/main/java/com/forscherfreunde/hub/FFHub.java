package com.forscherfreunde.hub;

import com.forscherfreunde.hub.model.ProfileStore;
import com.forscherfreunde.hub.ui.AppWindow;
import com.forscherfreunde.hub.ui.Images;
import com.forscherfreunde.hub.ui.Theme;
import com.forscherfreunde.hub.update.AppVersion;
import com.forscherfreunde.hub.update.Bootstrap;
import com.forscherfreunde.hub.update.UpdateChecker;
import com.forscherfreunde.hub.update.UpdateStore;
import com.forscherfreunde.hub.update.UpdateVerifier;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.io.IOException;

/** Einstiegspunkt des ForscherFreunde Hubs. */
public final class FFHub {

    private FFHub() { }

    public static void main(String[] args) {
        System.setProperty("java.net.useSystemProxies", "true"); // Schul-/Camp-Proxy für Updates nutzen

        // 0) Liegt ein geprüftes, neueres Update bereit? Dann übernimmt dieses (im selben Prozess).
        if (Bootstrap.launchStagedUpdate(args)) return;

        // 1) Einzelinstanz prüfen – bevor irgendetwas von AWT/Swing geladen wird.
        //    Nach einem Neustart für ein Update kurz warten, bis die alte Instanz beendet ist.
        SingleInstance instance = SingleInstance.acquire(AppPaths.stateDir(), Boolean.getBoolean("ffhub.waitForLock"));
        if (instance == null) {
            System.exit(0);
            return;
        }

        // 2) Oberfläche auf dem Swing-Thread aufbauen.
        SwingUtilities.invokeLater(() -> {
            Theme.install();
            ProfileStore store;
            try {
                store = ProfileStore.load();
            } catch (IOException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, e.getMessage(), "Profile konnten nicht geladen werden",
                        JOptionPane.WARNING_MESSAGE);
                store = new ProfileStore();
            }
            AppWindow window = new AppWindow(store);
            instance.setOnActivate(() -> SwingUtilities.invokeLater(window::bringToFront));
            window.setVisible(true);
            window.recoverOnStartup();
            // Banner im Hintergrund vordekodieren, damit spätere Bildschirme sofort bereit sind.
            Images.preload(AppWindow.moduleImages());
            startUpdates(window);
        });
    }

    private static void startUpdates(AppWindow window) {
        UpdateStore store = UpdateStore.standard();
        store.markHealthy(AppVersion.current()); // diese Version startet erfolgreich → kein Rollback nötig
        if (!UpdateChecker.enabled()) return;
        UpdateVerifier verifier = UpdateVerifier.fromResource();
        if (verifier == null) {
            System.err.println("Kein Update-Schlüssel eingerichtet – automatische Updates sind deaktiviert.");
            return;
        }
        UpdateChecker checker = new UpdateChecker(UpdateChecker.configuredUrl(), store, verifier, AppVersion.current());
        checker.startBackground(m -> SwingUtilities.invokeLater(() -> window.showUpdateReady(m)));
        window.setUpdateChecker(checker);
    }
}
