package com.forscherfreunde.hub;

import com.forscherfreunde.hub.model.ProfileStore;
import com.forscherfreunde.hub.ui.AppWindow;
import com.forscherfreunde.hub.ui.Images;
import com.forscherfreunde.hub.ui.Theme;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.io.IOException;

/** Einstiegspunkt des ForscherFreunde Hubs. */
public final class FFHub {

    private FFHub() { }

    public static void main(String[] args) {
        // 1) Einzelinstanz prüfen – bevor irgendetwas von AWT/Swing geladen wird.
        SingleInstance instance = SingleInstance.acquire(AppPaths.stateDir());
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
        });
    }
}
