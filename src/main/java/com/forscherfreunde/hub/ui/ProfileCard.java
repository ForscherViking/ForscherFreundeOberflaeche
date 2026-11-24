package com.forscherfreunde.hub.ui;

import com.forscherfreunde.hub.model.Camp;
import com.forscherfreunde.hub.model.UserProfile;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Kachel für ein Profil: Klick öffnet das Profil, Symbol-Buttons für Doku/Backup/Löschen. */
public final class ProfileCard extends HoverCard {

    public static final int WIDTH = 340;
    public static final int HEIGHT = 196;
    private static final int PAD = 18;
    private static final int THUMB = 76;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM., HH:mm")
            .withZone(ZoneId.systemDefault());

    /** Aktionen, die die Karte auslösen kann. */
    public interface Actions {
        void open(UserProfile p);
        void docs(UserProfile p);
        void backup(UserProfile p);
        void restore(UserProfile p);
        void delete(UserProfile p);
        void export(UserProfile p);
    }

    private final UserProfile profile;
    private final Camp module;
    private final Color accent;
    private final FittedImage thumb = new FittedImage(this, 18, 0f);
    private final String backupText;

    public ProfileCard(UserProfile profile, Instant lastBackup, Actions actions) {
        this.profile = profile;
        this.module = profile.module();
        this.accent = module != null ? module.accent() : Theme.ACCENT;
        this.backupText = !profile.isCodeProfile() ? "Minecraft-Welten"
                : lastBackup != null ? "Backup: " + DATE.format(lastBackup) : "Noch kein Backup";
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setToolTipText("Profil von " + profile.getName() + " öffnen");
        getAccessibleContext().setAccessibleName("Profil " + profile.getName());
        setOnClick(() -> actions.open(profile));

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        bar.setOpaque(false);
        bar.setBorder(BorderFactory.createEmptyBorder(0, PAD, 12, PAD - 6));
        add(bar, BorderLayout.SOUTH);

        addAction(bar, Icons.Type.BOOK, "Dokumentation", Theme.MUTED, () -> actions.docs(profile));
        if (profile.isCodeProfile()) {
            addAction(bar, Icons.Type.BACKUP, "Backup erstellen", Theme.MUTED, () -> actions.backup(profile));
            addAction(bar, Icons.Type.RESTORE, "Backup wiederherstellen", Theme.MUTED, () -> actions.restore(profile));
        }
        addAction(bar, Icons.Type.USB, "Auf USB-Stick speichern", Theme.MUTED, () -> actions.export(profile));
        addAction(bar, Icons.Type.TRASH, "Profil löschen", new Color(0xE8857E), () -> actions.delete(profile));

        if (module != null) Images.load(module.image(), thumb::setSource);
    }

    private void addAction(JPanel bar, Icons.Type icon, String tip, Color color, Runnable r) {
        JButton b = Ui.iconButton(icon, tip, color);
        b.addActionListener(e -> r.run());
        trackHover(b);
        bar.add(b);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Theme.quality(g2);
        int lift = Math.round(hover.get() * 3 - press.get() * 2);
        g2.translate(0, -lift);
        int w = getWidth() - 8;
        int h = getHeight() - 10;
        paintChrome(g2, 4, 4, w, h, 22, accent, 0f);

        int x = 4 + PAD;
        int y = 4 + PAD;
        // Vorschaubild des Moduls
        if (thumb.hasImage()) {
            thumb.paint(g2, x, y, THUMB, THUMB);
        } else {
            g2.setColor(Theme.SURFACE_HI);
            g2.fillRoundRect(x, y, THUMB, THUMB, 18, 18);
        }

        int tx = x + THUMB + 16;
        int maxW = 4 + w - PAD - tx;
        g2.setFont(Theme.font(Font.BOLD, 21f));
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(Theme.TEXT);
        g2.drawString(ellipsize(profile.getName(), fm, maxW), tx, y + fm.getAscent() - 2);

        g2.setFont(Theme.font(Font.PLAIN, 14f));
        FontMetrics fm2 = g2.getFontMetrics();
        g2.setColor(Theme.MUTED);
        String title = module != null ? module.title() : profile.getModuleName();
        g2.drawString(ellipsize(title, fm2, maxW), tx, y + 30 + fm2.getAscent());

        // Art-Plakette
        String badge = profile.isCodeProfile() ? "Programmieren" : "Minecraft";
        g2.setFont(Theme.font(Font.BOLD, 12f));
        FontMetrics fm3 = g2.getFontMetrics();
        int bw = fm3.stringWidth(badge) + 34;
        int by = y + 56;
        g2.setColor(Theme.alpha(accent, 0.16f));
        g2.fillRoundRect(tx, by, bw, 22, 22, 22);
        Icons.paint(g2, profile.isCodeProfile() ? Icons.Type.CODE : Icons.Type.CUBE, tx + 8, by + 4, 14, accent, 2.4f);
        g2.setColor(accent);
        g2.drawString(badge, tx + 26, by + (22 - fm3.getHeight()) / 2 + fm3.getAscent());

        // Fußzeile
        int fy = 4 + h - 52;
        g2.setColor(Theme.BORDER);
        g2.fillRect(x, fy, w - 2 * PAD, 1);
        g2.setFont(Theme.font(Font.PLAIN, 12.5f));
        FontMetrics fm4 = g2.getFontMetrics();
        g2.setColor(Theme.MUTED);
        g2.drawString(backupText, x, fy + 10 + (34 - fm4.getHeight()) / 2 + fm4.getAscent());
        g2.dispose();
    }
}
