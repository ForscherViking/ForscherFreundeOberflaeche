package com.forscherfreunde.hub.service;

/** Ein externes Programm, dessen Laufen der Hub beobachten kann (Minimieren, Einzelinstanz-Sperre). */
public interface WatchedProgram {
    /** Anzeigename, z. B. „Paint.NET“. */
    String title();

    /** Läuft das Programm gerade? Dauert einige Millisekunden → nicht auf dem Swing-Thread aufrufen. */
    boolean isRunning();
}
