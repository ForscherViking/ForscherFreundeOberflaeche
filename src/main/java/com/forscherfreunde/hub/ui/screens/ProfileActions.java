package com.forscherfreunde.hub.ui.screens;

import com.forscherfreunde.hub.model.UserProfile;
import com.forscherfreunde.hub.service.WorkspaceService;
import com.forscherfreunde.hub.ui.AppWindow;
import com.forscherfreunde.hub.ui.Icons;
import com.forscherfreunde.hub.ui.Theme;

/** Backup-Aktionen, die sowohl auf der Übersicht als auch auf der Profilseite verfügbar sind. */
final class ProfileActions {

    private ProfileActions() { }

    static void backup(AppWindow app, UserProfile p, Runnable after) {
        app.runBusy("Backup für " + p.getName() + " wird erstellt …", pr -> {
            WorkspaceService.backup(p, pr);
            return null;
        }, r -> {
            app.toast("Backup erstellt.");
            if (after != null) after.run();
        }, e -> app.error("Backup fehlgeschlagen", e));
    }

    static void restore(AppWindow app, UserProfile p, Runnable after) {
        if (WorkspaceService.lastBackup(p) == null) {
            app.toast("Für " + p.getName() + " gibt es noch kein Backup.", Icons.Type.ALERT, Theme.WARM);
            return;
        }
        if (!app.confirm("Backup wiederherstellen",
                "Soll das Backup von " + p.getName() + " wiederhergestellt werden?\n"
                        + "Alle Änderungen seit dem Backup gehen dabei verloren.", "Wiederherstellen")) return;
        app.runBusy("Backup wird wiederhergestellt …", pr -> {
            WorkspaceService.restore(p, pr);
            return null;
        }, r -> {
            app.toast("Backup wiederhergestellt.");
            if (after != null) after.run();
        }, e -> app.error("Wiederherstellen fehlgeschlagen", e));
    }
}
