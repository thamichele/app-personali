package it.michele.calendario;

import javax.imageio.ImageIO;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Promemoria degli eventi (VALARM): ogni 30 secondi controlla quelli scaduti e li mostra con la notifica di
 * sistema e una piccola finestra con "Posticipa". Funziona finché l'app è aperta (anche solo nell'area di notifica).
 * Quelli scattati mentre l'app era chiusa vengono mostrati all'avvio se sono degli ultimi 15 minuti.
 */
final class Reminders {
    private static final long MISSED_MINUTES = 15;

    private final Sync sync;
    private final Store store;
    private final Runnable showMain;
    TrayIcon tray;
    private Set<String> fired;
    private final List<Object[]> snoozed = new ArrayList<>(); // {Instant, titolo, testo}
    private final List<JDialog> open = new ArrayList<>();

    Reminders(Sync sync, Store store, Runnable showMain) {
        this.sync = sync;
        this.store = store;
        this.showMain = showMain;
        fired = Store.loadFired();
        boolean first = fired == null;
        if (first) fired = new java.util.HashSet<>();
        check(first);
        new Timer(30_000, e -> check(false)).start();
    }

    /** Ricontrolla subito (dopo una sincronizzazione o una modifica). */
    void check() { check(false); }

    private void check(boolean silent) {
        Instant now = Instant.now();
        ZonedDateTime z = ZonedDateTime.now();
        boolean changed = false;
        for (Model.Occ o : Model.occurrences(sync.colls, store.hidden(), false, z.minusDays(1), z.plusDays(8))) {
            for (Instant a : o.alarms()) {
                if (a.isAfter(now)) continue;
                String key = a.getEpochSecond() + "|" + o.res.href + "|" + o.start.toEpochSecond();
                if (fired.contains(key)) continue;
                fired.add(key);
                changed = true;
                if (!silent && a.isAfter(now.minus(MISSED_MINUTES, ChronoUnit.MINUTES)) && o.end.toInstant().isAfter(now.minus(1, ChronoUnit.HOURS)))
                    show(o.title(), describe(o));
            }
        }
        for (Iterator<Object[]> it = snoozed.iterator(); it.hasNext(); ) {
            Object[] s = it.next();
            if (!((Instant) s[0]).isAfter(now)) { it.remove(); show((String) s[1], (String) s[2]); }
        }
        if (changed) {
            long old = now.minus(3, ChronoUnit.DAYS).getEpochSecond();
            fired.removeIf(k -> {
                try { return Long.parseLong(k.substring(0, k.indexOf('|'))) < old; } catch (Exception e) { return true; }
            });
            Store.saveFired(fired);
        }
    }

    private static String describe(Model.Occ o) {
        ZonedDateTime now = ZonedDateTime.now();
        String when;
        if (o.allDay) when = o.start.toLocalDate().equals(now.toLocalDate()) ? "Oggi" : Ui.day(o.start.toLocalDate());
        else {
            long min = ChronoUnit.MINUTES.between(now, o.start);
            String day = o.start.toLocalDate().equals(now.toLocalDate()) ? "" : Ui.day(o.start.toLocalDate()) + ", ";
            when = day + Ui.time(o.start) + "–" + Ui.time(o.end)
                    + (min > 0 ? " (tra " + (min >= 120 ? min / 60 + " ore" : min + " min") + ")" : min > -5 ? " (adesso)" : "");
        }
        String loc = o.location();
        return loc != null && !loc.isBlank() ? when + "\n" + loc.trim() : when;
    }

    void show(String title, String text) {
        system(title, text);
        if (GraphicsEnvironment.isHeadless()) return;
        Toolkit.getDefaultToolkit().beep();
        JDialog w = new JDialog((java.awt.Frame) null, "Promemoria");
        w.setUndecorated(true);
        w.setAlwaysOnTop(true);
        w.setFocusableWindowState(false);
        w.setIconImages(Ui.appIcons());
        JPanel card = Ui.card(Ui.PANEL, Ui.ACCENT_BORDER, 12);
        card.setLayout(new BorderLayout(0, 10));
        card.setBorder(Ui.pad(14, 16, 12, 16));
        JPanel top = new JPanel(new BorderLayout(10, 0));
        top.setOpaque(false);
        javax.swing.JLabel icon = new javax.swing.JLabel(new Ui.Glyph("bell", 20, Ui.ACCENT_TEXT));
        icon.setVerticalAlignment(javax.swing.SwingConstants.TOP);
        top.add(icon, BorderLayout.WEST);
        JPanel col = Ui.column();
        col.add(Ui.label("<html><div style='width:260px'>" + Ui.html(title) + "</div></html>", 14, Ui.TEXT, true));
        col.add(Ui.gap(4));
        col.add(Ui.label("<html><div style='width:260px'>" + Ui.html(text) + "</div></html>", 12, Ui.OFF, false));
        top.add(col, BorderLayout.CENTER);
        card.add(top, BorderLayout.CENTER);
        JPanel b = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        b.setOpaque(false);
        b.add(Ui.button("Posticipa 5 min", Ui.GHOST, () -> {
            snoozed.add(new Object[]{Instant.now().plus(5, ChronoUnit.MINUTES), title, text});
            close(w);
        }));
        b.add(Ui.button("Apri", Ui.SECONDARY, () -> { close(w); showMain.run(); }));
        b.add(Ui.button("Chiudi", Ui.PRIMARY, () -> close(w)));
        card.add(b, BorderLayout.SOUTH);
        w.setContentPane(card);
        try { w.setBackground(new java.awt.Color(0, 0, 0, 0)); } catch (Exception ignored) { } // angoli arrotondati se il sistema lo permette
        w.pack();
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int y = screen.y + screen.height - w.getHeight() - 16;
        for (JDialog o : open) y -= o.getHeight() + 10;
        w.setLocation(screen.x + screen.width - w.getWidth() - 16, Math.max(screen.y + 16, y));
        open.add(w);
        w.setVisible(true);
    }

    private void close(JDialog w) {
        open.remove(w);
        w.dispose();
    }

    /** Notifica del sistema: notify-send su Linux, area di notifica su Windows. */
    private void system(String title, String text) {
        if (!Main.WINDOWS) {
            try {
                Path icon = Store.DIR.resolve("icona.png");
                File f = icon.toFile();
                if (!f.exists()) ImageIO.write(Ui.appIcon(128, Ui.themeColor(0)), "png", f);
                // gli argomenti vanno diretti al programma, senza shell; il testo è escapato perché alcuni demoni
                // di notifica interpretano un po' di HTML
                new ProcessBuilder("notify-send", "-a", "Calendario", "-i", f.getAbsolutePath(), "-u", "critical",
                        markup(title), markup(text)).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
                return;
            } catch (Exception ignored) { }
        }
        if (tray != null) tray.displayMessage(title, text, TrayIcon.MessageType.INFO);
    }

    private static String markup(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
