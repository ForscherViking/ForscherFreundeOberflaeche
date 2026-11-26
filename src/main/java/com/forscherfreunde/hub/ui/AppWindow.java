package com.forscherfreunde.hub.ui;

import com.forscherfreunde.hub.model.Camp;
import com.forscherfreunde.hub.model.ProfileStore;
import com.forscherfreunde.hub.model.UserProfile;
import com.forscherfreunde.hub.service.Background;
import com.forscherfreunde.hub.service.MinecraftWorlds;
import com.forscherfreunde.hub.service.Progress;
import com.forscherfreunde.hub.ui.screens.CreateProfileScreen;
import com.forscherfreunde.hub.ui.screens.DocsScreen;
import com.forscherfreunde.hub.ui.screens.ProfileScreen;
import com.forscherfreunde.hub.ui.screens.ProfilesScreen;
import com.forscherfreunde.hub.update.Restart;
import com.forscherfreunde.hub.update.UpdateManifest;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Das einzige Fenster der Anwendung. Enthält Kopfzeile, Bildschirm-Bereich, Fortschritts-Overlay
 * und Hinweis-Blasen und steuert die Navigation zwischen den Bildschirmen.
 */
public final class AppWindow extends JFrame {

    /** Hintergrundaufgabe mit Fortschrittsmeldung. */
    @FunctionalInterface
    public interface BusyTask<T> {
        T run(Progress progress) throws Exception;
    }

    private final ProfileStore store;
    private final ScreenHost host = new ScreenHost();
    private final BusyOverlay busy = new BusyOverlay();
    private final Toast toast = new Toast();
    private final JLabel headerTitle = Ui.label("", Font.BOLD, 17f, Theme.TEXT);
    private final JButton backButton = Ui.ghost("Zurück", Icons.Type.BACK);
    private final JComponent backDivider = divider();
    private final JButton updateButton = Ui.secondary("Update bereit", Icons.Type.RESTORE);
    private UpdateManifest pendingUpdate;
    private final JLabel versionLabel = new JLabel();
    private com.forscherfreunde.hub.update.UpdateChecker updateChecker;
    private boolean checking;
    private boolean exiting;

    public AppWindow(ProfileStore store) {
        super("ForscherFreunde Hub");
        this.store = store;
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setIconImages(appIcons());
        setMinimumSize(new Dimension(980, 640));

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG);
        root.add(createHeader(), BorderLayout.NORTH);
        root.add(host, BorderLayout.CENTER);
        setContentPane(root);
        setGlassPane(busy);

        getLayeredPane().add(toast, JLayeredPane.POPUP_LAYER);
        getLayeredPane().addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) { toast.relayout(); }
        });

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { requestExit(); }
        });
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "back");
        root.getActionMap().put("back", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                Runnable back = host.current() != null ? host.current().backAction() : null;
                if (back != null && !busy.isActive()) back.run();
            }
        });

        sizeToScreen();
        showProfiles();
    }

    // ------------------------------------------------------------------ Navigation

    public ProfileStore store() { return store; }

    public void showProfiles() { show(new ProfilesScreen(this)); }

    public void showCreateProfile() {
        if (store.isFull()) {
            toast("Es sind bereits " + ProfileStore.MAX_PROFILES + " Profile vorhanden.", Icons.Type.ALERT, Theme.WARM);
            return;
        }
        show(new CreateProfileScreen(this));
    }

    public void showProfile(UserProfile profile) { show(new ProfileScreen(this, profile)); }

    public void showDocs(UserProfile profile, Runnable back) { show(new DocsScreen(this, profile, back)); }

    public void showPrograms(UserProfile profile, Runnable back) {
        show(new com.forscherfreunde.hub.ui.screens.ProgramsScreen(this, profile, back));
    }

    public void showExport(UserProfile profile, Runnable back) {
        show(new com.forscherfreunde.hub.ui.screens.ExportScreen(this, profile, back));
    }

    public void show(Screen screen) {
        host.show(screen);
        headerTitle.setText(screen.title().isEmpty() ? " " : screen.title());
        Runnable back = screen.backAction();
        backButton.setVisible(back != null);
        backDivider.setVisible(back != null);
        for (var l : backButton.getActionListeners()) backButton.removeActionListener(l);
        if (back != null) backButton.addActionListener(e -> back.run());
        screen.onShown();
    }

    // ------------------------------------------------------------------ Hintergrundaufgaben

    /** Führt {@code task} im Hintergrund aus und zeigt solange das Fortschritts-Overlay. */
    public <T> void runBusy(String heading, BusyTask<T> task, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        busy.start(heading);
        Progress progress = Background.onEdt(busy::update);
        Background.run(() -> task.run(progress), result -> {
            busy.finish();
            if (onSuccess != null) onSuccess.accept(result);
        }, error -> {
            busy.finish();
            if (onError != null) onError.accept(error);
            else error("Das hat leider nicht geklappt", error);
        });
    }

    public boolean isBusy() { return busy.isActive(); }

    // ------------------------------------------------------------------ Dialoge & Hinweise

    public void toast(String message) { toast(message, Icons.Type.CHECK, Theme.ACCENT); }

    public void toast(String message, Icons.Type icon, Color color) { toast.show(message, icon, color); }

    public void error(String title, Throwable t) {
        String msg = t.getMessage() != null ? t.getMessage() : t.toString();
        error(title, msg);
    }

    public void error(String title, String message) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }

    public boolean confirm(String title, String message, String yes) {
        Object[] options = {yes, "Abbrechen"};
        int r = JOptionPane.showOptionDialog(this, message, title, JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE, null, options, options[1]);
        return r == 0;
    }

    // ------------------------------------------------------------------ Fenster

    /** Holt das Fenster nach vorne (z. B. wenn ein Kind die App ein zweites Mal startet). */
    public void bringToFront() {
        restoreWindow();
        toast("Der ForscherFreunde Hub ist schon geöffnet.", Icons.Type.ALERT, Theme.WARM);
    }

    private void restoreWindow() {
        if ((getExtendedState() & Frame.ICONIFIED) != 0) {
            setExtendedState(getExtendedState() & ~Frame.ICONIFIED);
        }
        setVisible(true);
        // Windows verweigert toFront() oft für Hintergrundprozesse; kurzes alwaysOnTop hilft.
        setAlwaysOnTop(true);
        toFront();
        requestFocus();
        setAlwaysOnTop(false);
    }

    /**
     * Minimiert den Hub in die Taskleiste, sobald {@code ext} läuft, und holt ihn zurück, wenn es
     * geschlossen wurde. Abschaltbar mit {@code -Dffhub.autoMinimize=false}.
     *
     * @param onEnd läuft auf dem Swing-Thread, wenn das Programm geschlossen wurde oder nie startete
     */
    public void minimizeWhileRunning(com.forscherfreunde.hub.service.WatchedProgram ext, long startTimeoutMillis,
                                     java.util.function.Consumer<com.forscherfreunde.hub.service.AppWatcher.End> onEnd) {
        boolean auto = !"false".equalsIgnoreCase(System.getProperty("ffhub.autoMinimize"));
        boolean[] minimized = {false};
        com.forscherfreunde.hub.service.AppWatcher.watch(ext, startTimeoutMillis,
                new com.forscherfreunde.hub.service.AppWatcher.Listener() {
            @Override public void started() {
                javax.swing.SwingUtilities.invokeLater(() -> {
                    if (auto && !exiting) {
                        minimized[0] = true;
                        setExtendedState(getExtendedState() | Frame.ICONIFIED);
                    }
                });
            }

            @Override public void ended(com.forscherfreunde.hub.service.AppWatcher.End how) {
                javax.swing.SwingUtilities.invokeLater(() -> {
                    if (exiting) return;
                    if (minimized[0]) {
                        restoreWindow();
                        toast("Willkommen zurück! " + ext.title() + " wurde geschlossen.", Icons.Type.CHECK, Theme.ACCENT);
                    }
                    onEnd.accept(how);
                });
            }
        });
    }

    /** Beim Start: Welten, die nach einem Absturz noch in Minecraft liegen, zurücksichern. */
    public void recoverOnStartup() {
        if (!MinecraftWorlds.hasCheckedOutWorlds()) return;
        runBusy("Welten vom letzten Mal werden gesichert …", p -> {
            MinecraftWorlds.checkOut(p);
            return null;
        }, r -> toast("Minecraft-Welten vom letzten Mal wurden gesichert."),
                e -> error("Welten konnten nicht gesichert werden", e));
    }

    public void requestExit() { leave(false); }

    /** Aktiviert die Prüfung per Klick auf die Versionsanzeige. */
    public void setUpdateChecker(com.forscherfreunde.hub.update.UpdateChecker checker) {
        this.updateChecker = checker;
        versionLabel.setToolTipText("Klicken, um jetzt nach Updates zu suchen");
        versionLabel.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
    }

    private void checkForUpdatesNow() {
        if (updateChecker == null || checking) return;
        if (pendingUpdate != null) {
            toast("Update " + pendingUpdate.version() + " ist bereit – oben rechts installieren.", Icons.Type.RESTORE, Theme.ACCENT);
            return;
        }
        checking = true;
        toast("Suche nach Updates …", Icons.Type.RESTORE, Theme.MUTED);
        updateChecker.checkNow((outcome, error) -> javax.swing.SwingUtilities.invokeLater(() -> {
            checking = false;
            switch (outcome) {
                case UP_TO_DATE -> toast("Du hast die neueste Version.");
                case FAILED -> toast("Update-Suche fehlgeschlagen – Internetverbindung prüfen.", Icons.Type.ALERT, Theme.WARM);
                default -> { } // showUpdateReady meldet sich selbst
            }
        }));
    }

    /** Zeigt in der Kopfzeile an, dass ein Update heruntergeladen und geprüft wurde. */
    public void showUpdateReady(UpdateManifest m) {
        boolean first = pendingUpdate == null;
        pendingUpdate = m;
        updateButton.setText("Update " + m.version() + " installieren");
        updateButton.setVisible(true);
        updateButton.getParent().revalidate();
        if (first) toast("Ein Update ist bereit – es wird beim nächsten Start installiert.", Icons.Type.RESTORE, Theme.ACCENT);
    }

    /** Beenden oder (für ein Update) neu starten – sichert vorher ggf. die Minecraft-Welten. */
    private void leave(boolean restart) {
        if (exiting) return;
        if (busy.isActive()) {
            toast("Bitte warte, bis der aktuelle Vorgang fertig ist.", Icons.Type.ALERT, Theme.WARM);
            return;
        }
        boolean ok;
        if (restart) {
            String notes = pendingUpdate != null && !pendingUpdate.notes().isEmpty()
                    ? "\n\nNeu in dieser Version:\n" + pendingUpdate.notes() : "";
            ok = confirm("Update installieren", "Der Hub startet jetzt kurz neu und installiert Version "
                    + pendingUpdate.version() + "." + notes, "Jetzt neu starten");
        } else {
            ok = confirm("Beenden", "Möchtest du den ForscherFreunde Hub wirklich beenden?", "Beenden");
        }
        if (!ok) return;
        Runnable finish = restart ? this::restartNow : this::exitNow;
        if (!MinecraftWorlds.hasCheckedOutWorlds()) {
            finish.run();
            return;
        }
        runBusy("Welten werden gesichert …", p -> {
            MinecraftWorlds.checkOut(p);
            return null;
        }, r -> finish.run(), e -> {
            if (confirm("Welten noch nicht vollständig gesichert", e.getMessage()
                    + "\n\nTrotzdem fortfahren? Die Welten werden dann beim nächsten Start gesichert.", "Trotzdem fortfahren")) {
                finish.run();
            }
        });
    }

    private void restartNow() {
        try {
            Restart.spawnNewInstance();
        } catch (java.io.IOException e) {
            error("Neustart nicht möglich", e.getMessage() + "\n\nDas Update wird beim nächsten normalen Start installiert.");
            return;
        }
        exitNow();
    }

    private void exitNow() {
        exiting = true;
        dispose();
        System.exit(0);
    }

    private void sizeToScreen() {
        Rectangle usable = getGraphicsConfiguration() != null
                ? getGraphicsConfiguration().getBounds() : new Rectangle(0, 0, 1366, 768);
        java.awt.Insets in = Toolkit.getDefaultToolkit().getScreenInsets(getGraphicsConfiguration());
        int aw = usable.width - in.left - in.right;
        int ah = usable.height - in.top - in.bottom;
        int w = Math.min(1320, Math.max(getMinimumSize().width, (int) (aw * 0.88)));
        int h = Math.min(860, Math.max(getMinimumSize().height, (int) (ah * 0.88)));
        setSize(w, h);
        setLocationRelativeTo(null);
        // Kleine Laptop-Bildschirme (z. B. 1366×768): direkt maximiert starten.
        if (aw <= 1440 || ah <= 820) setExtendedState(Frame.MAXIMIZED_BOTH);
    }

    // ------------------------------------------------------------------ Kopfzeile

    private JComponent createHeader() {
        JPanel header = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(Theme.BG_TOP);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(Theme.BORDER);
                g.fillRect(0, getHeight() - 1, getWidth(), 1);
            }
        };
        header.setBorder(BorderFactory.createEmptyBorder(12, 22, 12, 18));

        JPanel left = new JPanel();
        left.setLayout(new javax.swing.BoxLayout(left, javax.swing.BoxLayout.X_AXIS));
        left.setOpaque(false);
        left.add(new LogoView());
        left.add(Box.createHorizontalStrut(18));
        left.add(backDivider);
        left.add(Box.createHorizontalStrut(10));
        left.add(backButton);
        left.add(Box.createHorizontalStrut(8));
        headerTitle.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 0));
        backButton.setFocusable(false);
        left.add(headerTitle);
        backButton.setVisible(false);
        backDivider.setVisible(false);

        JButton exit = Ui.ghost("Beenden", Icons.Type.POWER);
        exit.setFocusable(false);
        exit.addActionListener(e -> requestExit());
        updateButton.setFocusable(false);
        updateButton.setVisible(false);
        updateButton.setFont(Theme.font(Font.BOLD, 13.5f));
        updateButton.setMargin(new java.awt.Insets(7, 14, 7, 16));
        updateButton.setIcon(Icons.of(Icons.Type.RESTORE, 16, Theme.ACCENT));
        Ui.style(updateButton, "arc: 999; borderWidth: 1; focusWidth: 0; innerFocusWidth: 0;"
                + "background: #1c3324; hoverBackground: #234230; pressedBackground: #183021;"
                + "borderColor: " + Theme.hex(Theme.ACCENT_LO) + "; hoverBorderColor: " + Theme.hex(Theme.ACCENT));
        updateButton.addActionListener(e -> leave(true));
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0)) {
            @Override public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                return new Dimension(d.width, Math.max(d.height, 40));
            }
        };
        right.setOpaque(false);
        versionLabel.setText("Version " + com.forscherfreunde.hub.update.AppVersion.current());
        versionLabel.setFont(Theme.font(Font.PLAIN, 12.5f));
        versionLabel.setForeground(Theme.alpha(Theme.MUTED, 0.7f));
        versionLabel.setToolTipText("Automatische Updates sind nicht eingerichtet.");
        versionLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) { checkForUpdatesNow(); }
        });
        right.add(versionLabel);
        right.add(updateButton);
        right.add(exit);

        header.add(left, BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    private static JComponent divider() {
        JComponent d = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(Theme.BORDER);
                g.fillRect(0, 6, 1, getHeight() - 12);
            }
        };
        d.setPreferredSize(new Dimension(1, 40));
        d.setMaximumSize(new Dimension(1, 40));
        return d;
    }

    private static List<Image> appIcons() {
        BufferedImage icon = Images.get("icon.png");
        List<Image> list = new ArrayList<>();
        for (int s : new int[] {16, 24, 32, 48, 64}) list.add(Images.scale(icon, s, s));
        return list;
    }

    /** Das ForscherFreunde-Logo in der Kopfzeile, scharf skaliert. */
    private static final class LogoView extends JComponent {
        private final BufferedImage logo = Images.get("logo.png");
        private BufferedImage scaled;

        LogoView() {
            int h = 40;
            Dimension d = new Dimension(Math.round(logo.getWidth() * h / (float) logo.getHeight()), h);
            setPreferredSize(d);
            setMinimumSize(d);
            setMaximumSize(d);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            double s = g2.getTransform().getScaleX();
            int dw = (int) Math.round(getWidth() * s);
            int dh = (int) Math.round(getHeight() * s);
            if (scaled == null || scaled.getWidth() != dw || scaled.getHeight() != dh) scaled = Images.scale(logo, dw, dh);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(scaled, 0, 0, getWidth(), getHeight(), null);
        }
    }

    /** Liste der Bildnamen aller Camp (zum Vorladen). */
    public static List<String> moduleImages() {
        List<String> l = new ArrayList<>();
        for (Camp m : Camp.values()) l.add(m.image());
        return l;
    }
}
