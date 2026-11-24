package com.forscherfreunde.hub.ui.screens;

import com.forscherfreunde.hub.model.Camp;
import com.forscherfreunde.hub.model.UserProfile;
import com.forscherfreunde.hub.service.Background;
import com.forscherfreunde.hub.service.ExternalApp;
import com.forscherfreunde.hub.service.Launcher;
import com.forscherfreunde.hub.service.MinecraftWorlds;
import com.forscherfreunde.hub.service.WorkspaceService;
import com.forscherfreunde.hub.ui.AppWindow;
import com.forscherfreunde.hub.ui.FittedImage;
import com.forscherfreunde.hub.ui.Icons;
import com.forscherfreunde.hub.ui.Images;
import com.forscherfreunde.hub.ui.Screen;
import com.forscherfreunde.hub.ui.Theme;
import com.forscherfreunde.hub.ui.Ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Profilseite: großes Camp-Banner und der Start-Knopf. */
public final class ProfileScreen extends Screen {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm")
            .withZone(ZoneId.systemDefault());

    private final UserProfile profile;
    private final Camp module;
    private final boolean loadWorlds;
    private final JButton start;
    private final String startText;
    private final JLabel info = Ui.label(" ", Font.PLAIN, 13.5f, Theme.MUTED);

    public ProfileScreen(AppWindow app, UserProfile profile) {
        this(app, profile, true);
    }

    /** @param loadWorlds bei Minecraft-Profilen die Welten laden (nicht nötig, wenn man aus der Doku zurückkommt) */
    ProfileScreen(AppWindow app, UserProfile profile, boolean loadWorlds) {
        super(app);
        this.profile = profile;
        this.module = profile.module();
        this.loadWorlds = loadWorlds;
        setBorder(BorderFactory.createEmptyBorder(28, 40, 32, 48));

        Color accent = module != null ? module.accent() : Theme.ACCENT;
        BannerView banner = new BannerView(module, accent);

        JPanel side = new JPanel();
        side.setOpaque(false);
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));

        JLabel badge = Ui.label(profile.isCodeProfile() ? "PROGRAMMIEREN" : "MINECRAFT", Font.BOLD, 12.5f, accent);
        badge.setIcon(Icons.of(profile.isCodeProfile() ? Icons.Type.CODE : Icons.Type.CUBE, 16, accent, 2.4f));
        badge.setIconTextGap(8);
        JLabel hello = Ui.label("<html>Hallo, " + escape(profile.getName()) + "!</html>", Font.BOLD, 34f, Theme.TEXT);
        JLabel camp = Ui.label(module != null ? module.title() : profile.getModuleName(), Font.BOLD, 18f, Theme.TEXT);
        JLabel tagline = Ui.label(module != null ? module.tagline() : " ", Font.PLAIN, 15f, Theme.MUTED);

        startText = profile.isCodeProfile() ? "IntelliJ starten" : "Minecraft starten";
        start = Ui.primary(startText, Icons.Type.PLAY);
        start.setFont(Theme.font(Font.BOLD, 19f));
        start.addActionListener(e -> launch());
        JButton docs = Ui.secondary("Dokumentation", Icons.Type.BOOK);
        docs.addActionListener(e -> app.showDocs(profile, () -> app.show(new ProfileScreen(app, profile, false))));
        JButton usb = Ui.secondary("Auf USB-Stick speichern", Icons.Type.USB);
        usb.setToolTipText(profile.isCodeProfile() ? "Speichert dein Projekt zum Mitnehmen"
                : "Speichert deine Minecraft-Welten zum Mitnehmen");
        usb.addActionListener(e -> app.showExport(profile, () -> app.show(new ProfileScreen(app, profile, false))));

        side.add(Box.createVerticalGlue());
        put(side, badge);
        side.add(Box.createVerticalStrut(12));
        put(side, hello);
        side.add(Box.createVerticalStrut(18));
        put(side, camp);
        side.add(Box.createVerticalStrut(4));
        put(side, tagline);
        side.add(Box.createVerticalStrut(34));
        put(side, stretch(start, 62));
        side.add(Box.createVerticalStrut(12));
        put(side, stretch(docs, 50));
        side.add(Box.createVerticalStrut(12));
        put(side, stretch(usb, 50));
        if (profile.isCodeProfile()) {
            JPanel row = new JPanel(new GridLayout(1, 2, 12, 0));
            row.setOpaque(false);
            JButton backup = Ui.secondary("Backup", Icons.Type.BACKUP);
            backup.setToolTipText("Sichert den aktuellen Stand deines Projekts");
            backup.addActionListener(e -> ProfileActions.backup(app, profile, this::updateInfo));
            JButton restore = Ui.secondary("Zurücksetzen", Icons.Type.RESTORE);
            backup.setMargin(new java.awt.Insets(10, 10, 10, 12));
            restore.setMargin(new java.awt.Insets(10, 10, 10, 12));
            restore.setToolTipText("Stellt das letzte Backup wieder her");
            restore.addActionListener(e -> ProfileActions.restore(app, profile, this::updateInfo));
            row.add(backup);
            row.add(restore);
            side.add(Box.createVerticalStrut(12));
            put(side, stretch(row, 50));
        }
        side.add(Box.createVerticalStrut(18));
        put(side, info);
        side.add(Box.createVerticalGlue());

        JPanel stage = new JPanel(new StageLayout(banner, side, banner.aspect));
        stage.setOpaque(false);
        stage.add(banner);
        stage.add(side);
        add(stage, BorderLayout.CENTER);
        updateInfo();
    }

    @Override public String title() { return profile.getName(); }

    @Override public Runnable backAction() { return this::goBack; }

    @Override
    public void onShown() {
        if (!profile.isCodeProfile() && loadWorlds) {
            app.runBusy("Deine Welten werden vorbereitet …", p -> {
                MinecraftWorlds.checkIn(profile, p);
                return null;
            }, r -> { }, e -> app.error("Welten konnten nicht geladen werden", e));
        }
    }

    private void goBack() {
        if (profile.isCodeProfile() || !MinecraftWorlds.hasCheckedOutWorlds()) {
            app.showProfiles();
            return;
        }
        app.runBusy("Deine Welten werden gesichert …", p -> {
            MinecraftWorlds.checkOut(p);
            return null;
        }, r -> {
            app.toast("Welten gesichert.");
            app.showProfiles();
        }, e -> app.error("Welten konnten nicht vollständig gesichert werden", e));
    }

    /**
     * Startet IntelliJ/Minecraft – aber nur, wenn es nicht schon läuft (Einzelinstanz-Sperre: Kinder
     * klicken auf langsamen Laptops gern mehrfach, während IntelliJ noch lädt). Solange das Programm
     * läuft, liegt der Hub minimiert in der Taskleiste und kommt danach von selbst zurück.
     */
    private void launch() {
        ExternalApp ext = profile.isCodeProfile() ? ExternalApp.INTELLIJ : ExternalApp.MINECRAFT;
        start.setEnabled(false);
        start.setText("Wird gestartet …");
        Background.run(() -> {
            if (ext.isRunning()) return false;
            if (profile.isCodeProfile()) Launcher.openIntelliJ(profile.modulePath());
            else Launcher.openMinecraft();
            return true;
        }, launched -> {
            if (launched) {
                app.toast(ext.title() + " wird geöffnet – einen Moment Geduld.");
            } else {
                app.toast(ext.title() + " ist schon geöffnet.", Icons.Type.ALERT, Theme.WARM);
            }
            start.setText(ext.title() + " läuft …");
            // IntelliJ braucht auf schwachen Laptops lange, bis der Prozess sicher erkennbar ist
            app.minimizeWhileRunning(ext, launched ? 120_000 : 10_000, how -> {
                start.setText(startText);
                start.setEnabled(true);
            });
        }, e -> {
            start.setText(startText);
            start.setEnabled(true);
            app.error("Start nicht möglich", e);
        });
    }

    private void updateInfo() {
        if (profile.isCodeProfile()) {
            Instant last = WorkspaceService.lastBackup(profile);
            info.setText(last != null ? "Letztes Backup: " + DATE.format(last) : "Noch kein Backup vorhanden.");
        } else {
            info.setText("<html>Deine Welten werden automatisch gesichert,<br>wenn du zurückgehst.</html>");
        }
    }

    private static void put(JPanel col, JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        col.add(c);
    }

    private static JComponent stretch(JComponent c, int height) {
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        c.setPreferredSize(new Dimension(300, height));
        return c;
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Banner links, Bedienspalte rechts – beides zusammen mittig, das Banner so groß wie möglich. */
    private static final class StageLayout implements java.awt.LayoutManager {
        private static final int SIDE = 360;
        private static final int GAP = 56;
        private final Component banner;
        private final Component side;
        private final double aspect;

        StageLayout(Component banner, Component side, double aspect) {
            this.banner = banner;
            this.side = side;
            this.aspect = aspect;
        }

        @Override
        public void layoutContainer(java.awt.Container parent) {
            int w = parent.getWidth();
            int h = parent.getHeight();
            int bw = (int) Math.min(h * aspect, Math.max(160, w - SIDE - GAP));
            int total = bw + GAP + SIDE;
            int x0 = Math.max(0, (w - total) / 2);
            banner.setBounds(x0, 0, bw, h);
            side.setBounds(x0 + bw + GAP, 0, SIDE, h);
        }

        @Override public Dimension preferredLayoutSize(java.awt.Container p) { return new Dimension(900, 500); }
        @Override public Dimension minimumLayoutSize(java.awt.Container p) { return new Dimension(600, 300); }
        @Override public void addLayoutComponent(String n, Component c) { }
        @Override public void removeLayoutComponent(Component c) { }
    }

    /** Das Camp-Banner, immer vollständig sichtbar und im richtigen Seitenverhältnis. */
    private static final class BannerView extends JComponent {
        private final FittedImage image = new FittedImage(this, 26, 0.5f);
        private final Color accent;
        private final double aspect;

        BannerView(Camp module, Color accent) {
            this.accent = accent;
            this.aspect = module != null ? module.imageAspect() : 1.0;
            if (module != null) Images.load(module.image(), image::setSource);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Theme.quality(g2);
            int pad = 4;
            int aw = getWidth() - 2 * pad;
            int ah = getHeight() - 2 * pad;
            int w = (int) Math.min(aw, ah * aspect);
            int h = (int) Math.round(w / aspect);
            int x = pad + (aw - w) / 2;
            int y = pad + (ah - h) / 2;
            for (int i = 1; i <= 4; i++) {
                g2.setColor(Theme.alpha(accent, 0.05f * (5 - i) / 4f));
                g2.setStroke(new BasicStroke(i * 4f));
                g2.drawRoundRect(x, y, w, h, 30, 30);
            }
            if (image.hasImage()) {
                image.paint(g2, x, y, w, h);
            } else {
                g2.setColor(Theme.SURFACE);
                g2.fillRoundRect(x, y, w, h, 26, 26);
            }
            g2.dispose();
        }
    }
}
