package com.forscherfreunde.hub.ui.screens;

import com.forscherfreunde.hub.AppPaths;
import com.forscherfreunde.hub.model.UserProfile;
import com.forscherfreunde.hub.service.WorkspaceService;
import com.forscherfreunde.hub.ui.AddProfileCard;
import com.forscherfreunde.hub.ui.AppWindow;
import com.forscherfreunde.hub.ui.CardGrid;
import com.forscherfreunde.hub.ui.HoverCard;
import com.forscherfreunde.hub.ui.Icons;
import com.forscherfreunde.hub.ui.ProfileCard;
import com.forscherfreunde.hub.ui.Screen;
import com.forscherfreunde.hub.ui.Theme;
import com.forscherfreunde.hub.ui.Ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Startbildschirm: alle Profile als Karten. Ein Klick öffnet das Profil. */
public final class ProfilesScreen extends Screen implements ProfileCard.Actions {

    private final List<HoverCard> cards = new ArrayList<>();

    public ProfilesScreen(AppWindow app) {
        super(app);
        setBorder(BorderFactory.createEmptyBorder(30, 40, 24, 40));

        JPanel head = new JPanel();
        head.setOpaque(false);
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        JLabel title = Ui.label("Wer forscht heute?", Font.BOLD, 32f, Theme.TEXT);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel sub = Ui.label(app.store().all().isEmpty()
                ? "Lege dein erstes Profil an, um loszulegen."
                : "Klicke auf dein Profil, um loszulegen.", Font.PLAIN, 16f, Theme.MUTED);
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);
        head.add(title);
        head.add(Box.createVerticalStrut(6));
        head.add(sub);
        if (!Files.isDirectory(AppPaths.root())) {
            head.add(Box.createVerticalStrut(14));
            JLabel warn = Ui.label("Datenordner nicht gefunden: " + AppPaths.root(), Font.PLAIN, 13.5f, Theme.WARM);
            warn.setIcon(Icons.of(Icons.Type.ALERT, 16, Theme.WARM));
            warn.setIconTextGap(8);
            warn.setAlignmentX(Component.CENTER_ALIGNMENT);
            head.add(warn);
        }
        add(head, BorderLayout.NORTH);

        CardGrid grid = new CardGrid(ProfileCard.WIDTH, ProfileCard.HEIGHT, 18, 3);
        grid.setBorder(BorderFactory.createEmptyBorder(24, 8, 24, 8));
        for (UserProfile p : app.store().all()) {
            ProfileCard card = new ProfileCard(p, p.isCodeProfile() ? WorkspaceService.lastBackup(p) : null, this);
            cards.add(card);
            grid.add(card);
        }
        if (!app.store().isFull()) {
            AddProfileCard add = new AddProfileCard(app::showCreateProfile);
            cards.add(add);
            grid.add(add);
        }

        JScrollPane scroll = new JScrollPane(grid);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        add(scroll, BorderLayout.CENTER);
    }

    @Override public String title() { return ""; }

    @Override
    public void onShown() {
        for (int i = 0; i < cards.size(); i++) cards.get(i).playAppear(60 + i * 45);
    }

    // ------------------------------------------------------------------ Aktionen der Karten

    @Override public void open(UserProfile p) { app.showProfile(p); }

    @Override public void docs(UserProfile p) { app.showDocs(p, app::showProfiles); }

    @Override public void backup(UserProfile p) { ProfileActions.backup(app, p, app::showProfiles); }

    @Override public void restore(UserProfile p) { ProfileActions.restore(app, p, null); }

    @Override public void export(UserProfile p) { app.showExport(p, app::showProfiles); }

    @Override
    public void delete(UserProfile p) {
        if (!app.confirm("Profil löschen",
                "Soll das Profil „" + p.getName() + "“ wirklich gelöscht werden?\n"
                        + "Alle Dateien und Backups dieses Profils werden dauerhaft entfernt.", "Löschen")) return;
        app.runBusy("Profil wird gelöscht …", pr -> {
            WorkspaceService.delete(p, pr);
            return null;
        }, r -> removeProfile(p), e -> {
            app.error("Löschen nicht vollständig", e.getMessage()
                    + "\n\nVielleicht ist noch eine Datei geöffnet (z. B. in IntelliJ).");
        });
    }

    private void removeProfile(UserProfile p) {
        try {
            app.store().remove(p);
            app.toast("Profil „" + p.getName() + "“ gelöscht.");
        } catch (java.io.IOException ex) {
            app.error("Profilliste konnte nicht gespeichert werden", ex);
        }
        app.showProfiles();
    }
}
