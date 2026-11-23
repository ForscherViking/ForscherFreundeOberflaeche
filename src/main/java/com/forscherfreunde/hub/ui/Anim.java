package com.forscherfreunde.hub.ui;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimaler Animations-Motor: Ein einziger Swing-Timer für alle Animationen, der nur läuft,
 * solange tatsächlich etwas animiert wird. Im Leerlauf verursacht die App so keine CPU-Last.
 *
 * <p>Mit {@code -Dffhub.animations=false} lassen sich alle Animationen abschalten
 * (Werte springen dann sofort ans Ziel).
 */
public final class Anim {

    private static boolean enabled = !"false".equalsIgnoreCase(System.getProperty("ffhub.animations"));
    private static final List<Value> ACTIVE = new ArrayList<>();
    private static final Timer TIMER = new Timer(15, e -> tick());

    static {
        TIMER.setCoalesce(true);
    }

    private Anim() { }

    public static boolean enabled() { return enabled; }

    public static void setEnabled(boolean on) { enabled = on; }

    private static void tick() {
        long now = System.nanoTime();
        // Rückwärts iterieren, damit Callbacks neue Animationen hinzufügen dürfen.
        for (int i = ACTIVE.size() - 1; i >= 0; i--) {
            if (i >= ACTIVE.size()) continue;
            Value v = ACTIVE.get(i);
            if (v.step(now)) {
                ACTIVE.remove(v);
                v.finish();
            }
        }
        if (ACTIVE.isEmpty()) TIMER.stop();
    }

    private static void schedule(Value v) {
        if (!ACTIVE.contains(v)) ACTIVE.add(v);
        if (!TIMER.isRunning()) TIMER.start();
    }

    /** Ein animierbarer Wert, der bei jeder Änderung seine Komponente neu zeichnet. */
    public static final class Value {
        private final JComponent owner;
        private float value;
        private float from;
        private float to;
        private long start;
        private long durationNs;
        private Runnable onDone;

        public Value(JComponent owner, float initial) {
            this.owner = owner;
            this.value = initial;
            this.to = initial;
        }

        public float get() { return value; }

        public float target() { return to; }

        public void set(float v) {
            ACTIVE.remove(this);
            value = v;
            to = v;
            if (owner != null) owner.repaint();
        }

        public void animateTo(float target, int millis) { animateTo(target, millis, 0, null); }

        public void animateTo(float target, int millis, int delayMillis, Runnable done) {
            if (!enabled || millis <= 0 || owner == null || !owner.isShowing()) {
                set(target);
                if (done != null) done.run();
                return;
            }
            if (target == to && ACTIVE.contains(this)) return;
            if (target == value && !ACTIVE.contains(this)) {
                to = target;
                if (done != null) done.run();
                return;
            }
            from = value;
            to = target;
            start = System.nanoTime() + delayMillis * 1_000_000L;
            durationNs = millis * 1_000_000L;
            onDone = done;
            schedule(this);
        }

        private boolean step(long now) {
            if (now < start) return false;
            float t = Math.min(1f, (now - start) / (float) durationNs);
            float eased = 1f - (float) Math.pow(1f - t, 3); // ease-out cubic
            value = from + (to - from) * eased;
            if (owner != null) owner.repaint();
            return t >= 1f;
        }

        private void finish() {
            value = to;
            Runnable r = onDone;
            onDone = null;
            if (r != null) r.run();
        }
    }
}
