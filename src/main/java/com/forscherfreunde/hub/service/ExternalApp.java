package com.forscherfreunde.hub.service;

import java.util.Locale;
import java.util.function.Predicate;

/**
 * Externe Programme, die der Hub startet und überwacht. Erkannt werden sie am Programmpfad
 * der laufenden Prozesse – das funktioniert auch, wenn sie nicht vom Hub gestartet wurden.
 */
public enum ExternalApp {

    /** IntelliJ IDEA: Hauptprozess ist {@code idea64.exe} (unter Windows) bzw. {@code idea}. */
    INTELLIJ("IntelliJ", cmd -> {
        String n = fileName(cmd);
        return n.equals("idea64.exe") || n.equals("idea.exe") || n.equals("idea64") || n.equals("idea");
    }),

    /**
     * Minecraft: Launcher ({@code MinecraftLauncher.exe}, {@code Minecraft.exe}) und das Spiel selbst
     * (dessen {@code javaw.exe} liegt in einem „runtime“-Ordner des Launchers, Pfad enthält „minecraft“).
     */
    MINECRAFT("Minecraft", cmd -> {
        String l = cmd.toLowerCase(Locale.ROOT);
        return l.contains("minecraft") && !l.contains("forscherfreunde");
    });

    private final String title;
    private final Predicate<String> commandMatcher;

    ExternalApp(String title, Predicate<String> commandMatcher) {
        this.title = title;
        this.commandMatcher = commandMatcher;
    }

    public String title() { return title; }

    /** Läuft das Programm gerade (egal von wem gestartet)? Dauert einige Millisekunden → nicht auf dem Swing-Thread. */
    public boolean isRunning() {
        long self = ProcessHandle.current().pid();
        return ProcessHandle.allProcesses()
                .filter(p -> p.pid() != self)
                .anyMatch(p -> p.info().command().map(commandMatcher::test).orElse(false));
    }

    boolean matchesCommand(String command) { return commandMatcher.test(command); }

    private static String fileName(String cmd) {
        int i = Math.max(cmd.lastIndexOf('/'), cmd.lastIndexOf('\\'));
        return cmd.substring(i + 1).toLowerCase(Locale.ROOT);
    }
}
