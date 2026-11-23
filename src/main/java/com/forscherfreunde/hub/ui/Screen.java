package com.forscherfreunde.hub.ui;

import javax.swing.JPanel;
import java.awt.BorderLayout;

/** Basisklasse für alle Bildschirme im Hauptfenster. Bildschirme sind transparent (siehe {@link ScreenHost}). */
public abstract class Screen extends JPanel {

    protected final AppWindow app;

    protected Screen(AppWindow app) {
        super(new BorderLayout());
        this.app = app;
        setOpaque(false);
    }

    /** Titel in der Kopfzeile. */
    public abstract String title();

    /** Aktion des Zurück-Knopfs oder {@code null}, wenn keiner angezeigt werden soll. */
    public Runnable backAction() { return null; }

    /** Wird aufgerufen, nachdem der Bildschirm sichtbar geworden ist. */
    public void onShown() { }
}
