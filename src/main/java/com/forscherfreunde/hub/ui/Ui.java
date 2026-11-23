package com.forscherfreunde.hub.ui;

import com.formdev.flatlaf.FlatClientProperties;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Insets;

/** Fabrikmethoden für einheitlich gestaltete Standard-Komponenten. */
public final class Ui {

    private Ui() { }

    public static JButton primary(String text, Icons.Type icon) {
        JButton b = base(text, icon, Theme.ON_ACCENT, 16);
        style(b, "arc: 999; borderWidth: 0; focusWidth: 0; innerFocusWidth: 0;"
                + "background: " + Theme.hex(Theme.ACCENT) + ";"
                + "foreground: " + Theme.hex(Theme.ON_ACCENT) + ";"
                + "hoverBackground: " + Theme.hex(Theme.ACCENT_HI) + ";"
                + "pressedBackground: " + Theme.hex(Theme.ACCENT_LO) + ";"
                + "disabledBackground: #26323f; disabledText: #6b7a8c");
        b.setFont(Theme.font(Font.BOLD, 16f));
        b.setMargin(new Insets(12, 26, 12, 28));
        return b;
    }

    public static JButton secondary(String text, Icons.Type icon) {
        JButton b = base(text, icon, Theme.TEXT, 18);
        style(b, "arc: 999; borderWidth: 1; focusWidth: 0; innerFocusWidth: 0;"
                + "background: " + Theme.hex(Theme.SURFACE_HI) + ";"
                + "hoverBackground: #283748; pressedBackground: #1b2632;"
                + "borderColor: " + Theme.hex(Theme.BORDER) + ";"
                + "hoverBorderColor: #3a4b5f; disabledText: #6b7a8c");
        b.setFont(Theme.font(Font.BOLD, 15f));
        b.setMargin(new Insets(10, 20, 10, 22));
        return b;
    }

    public static JButton ghost(String text, Icons.Type icon) {
        JButton b = base(text, icon, Theme.MUTED, 18);
        b.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        style(b, "arc: 999; hoverBackground: #1f2a37; pressedBackground: #17212b");
        b.setForeground(Theme.MUTED);
        b.setFont(Theme.font(Font.PLAIN, 14.5f));
        b.setMargin(new Insets(8, 14, 8, 16));
        return b;
    }

    public static JButton iconButton(Icons.Type icon, String tooltip, Color color) {
        JButton b = new JButton(Icons.of(icon, 18, color));
        b.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        style(b, "arc: 12; hoverBackground: #2a3747; pressedBackground: #1b2632");
        b.setToolTipText(tooltip);
        b.getAccessibleContext().setAccessibleName(tooltip);
        b.setMargin(new Insets(7, 7, 7, 7));
        b.setFocusable(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    public static JLabel label(String text, int style, float size, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(style, size));
        l.setForeground(color);
        return l;
    }

    public static void style(JComponent c, String style) {
        c.putClientProperty(FlatClientProperties.STYLE, style);
    }

    public static void pad(JComponent c, int top, int left, int bottom, int right) {
        c.setBorder(BorderFactory.createEmptyBorder(top, left, bottom, right));
    }

    private static JButton base(String text, Icons.Type icon, Color iconColor, int iconSize) {
        JButton b = new JButton(text);
        if (icon != null) {
            b.setIcon(Icons.of(icon, iconSize, iconColor));
            b.setIconTextGap(10);
        }
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }
}
