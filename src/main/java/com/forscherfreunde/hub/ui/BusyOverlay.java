package com.forscherfreunde.hub.ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import java.awt.AlphaComposite;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;

/**
 * Halbtransparente Ebene über dem Fenster, die während längerer Vorgänge (Kopieren, Löschen …)
 * Eingaben blockiert und den Fortschritt anzeigt. Ersetzt die vielen kleinen Fortschrittsfenster.
 */
final class BusyOverlay extends JComponent {

    private final Anim.Value fade = new Anim.Value(this, 0f);
    private final JLabel title = Ui.label("", Font.BOLD, 18f, Theme.TEXT);
    private final JLabel detail = Ui.label(" ", Font.PLAIN, 14f, Theme.MUTED);
    private final JProgressBar bar = new JProgressBar(0, 1000);
    private final JPanel card;
    private boolean active;

    BusyOverlay() {
        setOpaque(false);
        setLayout(null);
        setFocusable(true);
        MouseAdapter swallow = new MouseAdapter() { };
        addMouseListener(swallow);
        addMouseMotionListener(swallow);
        addMouseWheelListener(swallow);
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) { e.consume(); }
            @Override public void keyTyped(KeyEvent e) { e.consume(); }
        });
        setFocusTraversalKeysEnabled(false);

        card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                Theme.quality(g2);
                g2.setColor(Theme.SURFACE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 24, 24);
                g2.setColor(Theme.BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 24, 24);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(26, 30, 26, 30));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setHorizontalAlignment(SwingConstants.CENTER);
        detail.setAlignmentX(Component.CENTER_ALIGNMENT);
        bar.setAlignmentX(Component.CENTER_ALIGNMENT);
        bar.setPreferredSize(new Dimension(320, 8));
        bar.setMaximumSize(new Dimension(320, 8));
        card.add(title);
        card.add(Box.createVerticalStrut(14));
        card.add(bar);
        card.add(Box.createVerticalStrut(12));
        card.add(detail);
        add(card);
        setVisible(false);
    }

    boolean isActive() { return active; }

    void start(String heading) {
        active = true;
        title.setText(heading);
        detail.setText(" ");
        bar.setIndeterminate(true);
        setVisible(true);
        requestFocusInWindow();
        doLayout();
        fade.animateTo(1f, 160);
    }

    void update(String message, double fraction) {
        if (!active) return;
        if (message != null && !message.isBlank()) detail.setText(message);
        if (fraction < 0) {
            bar.setIndeterminate(true);
        } else {
            bar.setIndeterminate(false);
            bar.setValue((int) Math.round(Math.min(1, fraction) * 1000));
        }
    }

    void finish() {
        active = false;
        bar.setIndeterminate(false); // stoppt die Animation des Balkens
        fade.animateTo(0f, 160, 0, () -> {
            if (!active) setVisible(false);
        });
    }

    @Override
    public void doLayout() {
        Dimension d = card.getPreferredSize();
        int w = Math.max(400, d.width);
        card.setBounds((getWidth() - w) / 2, (getHeight() - d.height) / 2, w, d.height);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(Theme.alpha(Theme.BG, 0.72f * fade.get()));
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.dispose();
    }

    @Override
    public void paint(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setComposite(AlphaComposite.SrcOver.derive(Math.max(0f, Math.min(1f, fade.get()))));
        super.paint(g2);
        g2.dispose();
    }
}
