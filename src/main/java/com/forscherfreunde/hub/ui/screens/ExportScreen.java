package com.forscherfreunde.hub.ui.screens;

import com.forscherfreunde.hub.model.UserProfile;
import com.forscherfreunde.hub.service.Background;
import com.forscherfreunde.hub.service.ExportService;
import com.forscherfreunde.hub.service.UsbDrives;
import com.forscherfreunde.hub.ui.Anim;
import com.forscherfreunde.hub.ui.AppWindow;
import com.forscherfreunde.hub.ui.HoverCard;
import com.forscherfreunde.hub.ui.Icons;
import com.forscherfreunde.hub.ui.Screen;
import com.forscherfreunde.hub.ui.Theme;
import com.forscherfreunde.hub.ui.Ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * „Auf USB-Stick speichern“: wartet auf einen Stick und speichert dann automatisch.
 * Ist genau ein Stick eingesteckt, geht es sofort los; bei mehreren wählt das Kind per Klick.
 * Gestartet wird nur über den Knopf – ein bloß eingesteckter Stick wird nie ungefragt beschrieben.
 */
public final class ExportScreen extends Screen {

    private enum State { PREPARING, WAITING, CHOOSE, DONE, NOTHING }

    private final UserProfile profile;
    private final Runnable back;
    private final StatusArt art = new StatusArt();
    private final JLabel heading = Ui.label(" ", Font.BOLD, 28f, Theme.TEXT);
    private final JLabel text = Ui.label(" ", Font.PLAIN, 16f, Theme.MUTED);
    private final JPanel drives = new JPanel();
    private final JButton other = Ui.ghost("Anderen Ordner wählen …", Icons.Type.FOLDER);
    private final JButton finish = Ui.primary("Fertig", Icons.Type.CHECK);
    private final Timer poll;
    private State state = State.PREPARING;
    private String what = "";
    private boolean autoStart = true;
    private boolean polling;

    public ExportScreen(AppWindow app, UserProfile profile, Runnable back) {
        super(app);
        this.profile = profile;
        this.back = back;
        setBorder(BorderFactory.createEmptyBorder(30, 40, 30, 40));

        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        art.setAlignmentX(Component.CENTER_ALIGNMENT);
        heading.setAlignmentX(Component.CENTER_ALIGNMENT);
        text.setAlignmentX(Component.CENTER_ALIGNMENT);
        text.setHorizontalAlignment(SwingConstants.CENTER);
        drives.setOpaque(false);
        drives.setLayout(new BoxLayout(drives, BoxLayout.Y_AXIS));
        drives.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        buttons.setOpaque(false);
        buttons.setAlignmentX(Component.CENTER_ALIGNMENT);
        other.setToolTipText("Falls der USB-Stick nicht automatisch erkannt wird");
        other.addActionListener(e -> chooseFolder());
        finish.addActionListener(e -> back.run());
        finish.setVisible(false);
        buttons.add(other);
        buttons.add(finish);

        col.add(art);
        col.add(Box.createVerticalStrut(22));
        col.add(heading);
        col.add(Box.createVerticalStrut(10));
        col.add(text);
        col.add(Box.createVerticalStrut(26));
        col.add(drives);
        col.add(Box.createVerticalStrut(20));
        col.add(buttons);

        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        center.add(col);
        add(center, BorderLayout.CENTER);

        poll = new Timer(1500, e -> detect());
        render(List.of());
    }

    @Override public String title() { return "Auf USB-Stick speichern"; }

    @Override public Runnable backAction() { return back; }

    @Override
    public void onShown() {
        Background.run(() -> ExportService.plan(profile), plan -> {
            if (plan.isEmpty()) {
                state = State.NOTHING;
                render(List.of());
                return;
            }
            String main = profile.isCodeProfile() ? "dein Projekt"
                    : plan.worlds() == 1 ? "deine Minecraft-Welt"
                    : plan.worlds() > 1 ? "deine " + plan.worlds() + " Minecraft-Welten" : null;
            String mine = plan.myFiles() == 0 ? null
                    : plan.myFiles() == 1 ? "1 eigene Datei" : plan.myFiles() + " eigene Dateien";
            what = (main != null && mine != null ? main + " und " + mine : main != null ? main : mine)
                    + " (" + mb(plan.bytes()) + ")";
            state = State.WAITING;
            detect();
            poll.start();
        }, e -> {
            state = State.NOTHING;
            render(List.of());
        });
    }

    @Override
    public void removeNotify() {
        poll.stop(); // nur suchen, solange dieser Bildschirm sichtbar ist
        super.removeNotify();
    }

    private void detect() {
        if (polling || app.isBusy() || state == State.DONE || state == State.NOTHING) return;
        polling = true;
        Background.run(UsbDrives::detect, found -> {
            polling = false;
            if (state == State.DONE || !isShowing()) return;
            if (autoStart && found.size() == 1) {
                export(found.get(0).root(), found.get(0).displayName());
                return;
            }
            state = found.isEmpty() ? State.WAITING : State.CHOOSE;
            render(found);
        }, e -> polling = false);
    }

    private void export(Path root, String name) {
        autoStart = false; // nach einem Versuch nie wieder ungefragt starten
        poll.stop();
        app.runBusy("Wird auf „" + name + "“ gespeichert …", p -> ExportService.export(profile, root, p), zip -> {
            state = State.DONE;
            heading.setText("Gespeichert!");
            text.setText(html("Deine Datei <b>" + esc(zip.getFileName().toString()) + "</b><br>liegt auf <b>"
                    + esc(name) + "</b> im Ordner „" + ExportService.FOLDER + "“.<br><br>"
                    + "Du kannst den USB-Stick jetzt abziehen."));
            drives.removeAll();
            other.setVisible(false);
            finish.setVisible(true);
            art.show(State.DONE);
            revalidate();
            repaint();
            app.toast("Auf dem USB-Stick gespeichert.");
        }, e -> {
            app.error("Speichern hat nicht geklappt", e);
            state = State.WAITING;
            poll.start();
            detect();
        });
    }

    private void chooseFolder() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Wohin soll gespeichert werden?");
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setAcceptAllFileFilterUsed(false);
        if (fc.showDialog(this, "Hier speichern") == JFileChooser.APPROVE_OPTION) {
            Path p = fc.getSelectedFile().toPath();
            export(p, p.toString());
        }
    }

    private void render(List<UsbDrives.Drive> found) {
        drives.removeAll();
        switch (state) {
            case PREPARING -> {
                heading.setText("Einen Moment …");
                text.setText(" ");
            }
            case NOTHING -> {
                heading.setText("Noch nichts zum Speichern");
                text.setText(html(profile.isCodeProfile()
                        ? "In deinem Projekt gibt es noch keine Dateien."
                        : "Du hast noch keine Minecraft-Welten. Starte Minecraft und bau los!"));
                other.setVisible(false);
                finish.setText("Zurück");
                finish.setVisible(true);
            }
            case WAITING -> {
                heading.setText("Steck deinen USB-Stick ein");
                text.setText(html("Sobald er erkannt wird, speichern wir " + esc(what) + " automatisch."));
            }
            case CHOOSE -> {
                heading.setText(found.size() == 1 ? "USB-Stick gefunden" : "Auf welchen USB-Stick?");
                text.setText(html("Klicke auf den Stick, auf dem " + esc(what) + " gespeichert werden soll."));
                for (UsbDrives.Drive d : found) {
                    DriveRow row = new DriveRow(d);
                    row.setAlignmentX(Component.CENTER_ALIGNMENT);
                    drives.add(row);
                    drives.add(Box.createVerticalStrut(10));
                }
            }
            default -> { }
        }
        art.show(state);
        revalidate();
        repaint();
    }

    private static String html(String s) {
        return "<html><div style='text-align:center; width:440px'>" + s + "</div></html>";
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    static String mb(long bytes) {
        if (bytes < 1_000_000) return Math.max(1, bytes / 1000) + " KB";
        return String.format(Locale.GERMANY, "%.1f MB", bytes / 1e6);
    }

    /** Großes Symbol: USB-Stick mit pulsierendem Ring (wartet) bzw. Häkchen (fertig). */
    private static final class StatusArt extends JComponent {
        private final Anim.Value pulse = new Anim.Value(this, 0f);
        private State state = State.PREPARING;

        StatusArt() {
            Dimension d = new Dimension(150, 150);
            setPreferredSize(d);
            setMaximumSize(d);
        }

        void show(State s) {
            boolean wasWaiting = state == State.WAITING;
            state = s;
            if (s == State.WAITING && !wasWaiting) loop();
            repaint();
        }

        private void loop() {
            if (state != State.WAITING || !isShowing() || !Anim.enabled()) {
                pulse.set(0f);
                return;
            }
            pulse.set(0f);
            pulse.animateTo(1f, 1600, 200, this::loop);
        }

        @Override
        public void addNotify() {
            super.addNotify();
            if (state == State.WAITING) loop();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Theme.quality(g2);
            int cx = getWidth() / 2;
            int cy = getHeight() / 2;
            boolean done = state == State.DONE;
            Color c = done ? Theme.ACCENT : state == State.NOTHING ? Theme.MUTED : new Color(0x6BB8FF);
            if (state == State.WAITING) {
                float t = pulse.get();
                int r = Math.round(46 + 26 * t);
                g2.setColor(Theme.alpha(c, 0.35f * (1f - t)));
                g2.setStroke(new BasicStroke(3f));
                g2.drawOval(cx - r, cy - r, 2 * r, 2 * r);
            }
            int r = 48;
            g2.setColor(Theme.alpha(c, 0.14f));
            g2.fillOval(cx - r, cy - r, 2 * r, 2 * r);
            Icons.paint(g2, done ? Icons.Type.CHECK : Icons.Type.USB, cx - 26, cy - 26, 52, c, done ? 3f : 2f);
            g2.dispose();
        }
    }

    /** Ein erkannter USB-Stick zum Anklicken. */
    private final class DriveRow extends HoverCard {
        private final UsbDrives.Drive drive;

        DriveRow(UsbDrives.Drive drive) {
            this.drive = drive;
            Dimension d = new Dimension(440, 70);
            setPreferredSize(d);
            setMaximumSize(d);
            setToolTipText("Hier speichern");
            getAccessibleContext().setAccessibleName(drive.displayName());
            setOnClick(() -> export(drive.root(), drive.displayName()));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Theme.quality(g2);
            Color c = new Color(0x6BB8FF);
            int lift = Math.round(hover.get() * 2 - press.get() * 2);
            g2.translate(0, -lift);
            int w = getWidth() - 8;
            int h = getHeight() - 10;
            paintChrome(g2, 4, 4, w, h, 16, c, 0f);
            int s = h - 18;
            g2.setColor(Theme.alpha(c, 0.15f));
            g2.fillRoundRect(13, 13, s, s, 12, 12);
            Icons.paint(g2, Icons.Type.USB, 13 + s * 0.2, 13 + s * 0.2, s * 0.6, c, 2.2f);
            int x = 13 + s + 16;
            g2.setFont(Theme.font(Font.BOLD, 16f));
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(Theme.TEXT);
            g2.drawString(ellipsize(drive.displayName(), fm, w - x - 20), x, 4 + h / 2 - 2);
            g2.setFont(Theme.font(Font.PLAIN, 13f));
            g2.setColor(Theme.MUTED);
            g2.drawString(String.format(Locale.GERMANY, "%.1f GB frei", drive.freeBytes() / 1e9), x,
                    4 + h / 2 + g2.getFontMetrics().getAscent());
            g2.dispose();
        }
    }
}
