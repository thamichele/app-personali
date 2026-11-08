package it.michele.calendario;

import javax.swing.JComponent;
import javax.swing.Scrollable;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/** Viste del calendario disegnate a mano: mese, settimana/giorno, agenda e mini mese. */
final class Views {
    private Views() {}

    interface Host {
        void open(Model.Occ o);
        /** hour < 0: evento di tutto il giorno */
        void create(LocalDate day, int hour, int minute);
        void goDay(LocalDate day);
    }

    static String fit(FontMetrics fm, String s, int w) {
        if (fm.stringWidth(s) <= w) return s;
        while (s.length() > 1 && fm.stringWidth(s + "…") > w) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    static boolean onDay(Model.Occ o, LocalDate d) {
        ZonedDateTime s = d.atStartOfDay(ZoneId.systemDefault()), e = d.plusDays(1).atStartOfDay(ZoneId.systemDefault());
        ZonedDateTime oe = o.end.isAfter(o.start) ? o.end : o.start.plusMinutes(1);
        return o.start.isBefore(e) && oe.isAfter(s);
    }

    static Color colorOf(Model.Occ o) {
        if (o.birthdayName != null) return Ui.ACCENT_SOFT;
        return Ui.color(o.coll.color);
    }

    /** Evento in chip: tutto il giorno pieno tenue con barra colorata; con orario: pallino e ora. */
    static void chip(Graphics2D g, Model.Occ o, int x, int y, int w, int h, boolean showTime) {
        Color c = colorOf(o);
        FontMetrics fm = g.getFontMetrics();
        int base = y + (h + fm.getAscent() - fm.getDescent()) / 2;
        if (o.allDay) {
            g.setColor(Ui.mix(c, Ui.BG, 0.72f));
            g.fill(new RoundRectangle2D.Double(x, y, w, h, 6, 6));
            g.setColor(c);
            g.fill(new RoundRectangle2D.Double(x, y, 3, h, 3, 3));
            g.setColor(Ui.TEXT);
            g.drawString(fit(fm, o.title(), w - 12), x + 8, base);
        } else {
            g.setColor(c);
            g.fill(new Ellipse2D.Double(x + 3, y + h / 2.0 - 3, 6, 6));
            String t = showTime ? Ui.time(o.start) + " " : "";
            g.setColor(Ui.OFF);
            g.drawString(t, x + 14, base);
            int tw = fm.stringWidth(t);
            g.setColor(Ui.TEXT);
            g.drawString(fit(fm, o.title(), w - 16 - tw), x + 14 + tw, base);
        }
    }

    static final class Hit {
        final Rectangle r;
        final Model.Occ occ;
        final LocalDate more;
        Hit(Rectangle r, Model.Occ occ, LocalDate more) { this.r = r; this.occ = occ; this.more = more; }
    }

    /** Base comune: zone cliccabili ricalcolate a ogni disegno. */
    abstract static class View extends JComponent {
        final Host host;
        final List<Hit> hits = new ArrayList<>();
        List<Model.Occ> occs = new ArrayList<>();
        LocalDate anchor = LocalDate.now();

        View(Host host) {
            this.host = host;
            setOpaque(true);
            setBackground(Ui.BG);
            MouseAdapter m = new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    Hit h = hit(e.getX(), e.getY());
                    if (h != null && h.occ != null) host.open(h.occ);
                    else if (h != null) host.goDay(h.more);
                    else empty(e.getX(), e.getY(), e.getClickCount());
                }
                @Override public void mouseMoved(MouseEvent e) {
                    setCursor(Cursor.getPredefinedCursor(hit(e.getX(), e.getY()) != null ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        Hit hit(int x, int y) {
            for (int i = hits.size() - 1; i >= 0; i--) if (hits.get(i).r.contains(x, y)) return hits.get(i);
            return null;
        }

        /** Clic su uno spazio vuoto: il doppio clic crea un evento. */
        void empty(int x, int y, int clicks) { }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Ui.aa(g);
            g.setColor(Ui.BG);
            g.fillRect(0, 0, getWidth(), getHeight());
            hits.clear();
            paint(g);
            g.dispose();
        }

        abstract void paint(Graphics2D g);
    }

    // ---------- mese ----------

    static LocalDate monthStart(LocalDate d) {
        return d.withDayOfMonth(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    static final class Month extends View {
        Month(Host h) { super(h); }

        private int head() { return 30; }

        @Override void paint(Graphics2D g) {
            LocalDate first = monthStart(anchor), today = LocalDate.now();
            int w = getWidth(), h = getHeight() - head();
            double cw = w / 7.0, ch = h / 6.0;
            g.setFont(Ui.font(11, true));
            for (int i = 0; i < 7; i++) {
                g.setColor(i >= 5 ? Ui.DIM : Ui.OFF);
                g.drawString(Ui.DAYS_SHORT[i].toUpperCase(), (int) (i * cw) + 10, 20);
            }
            g.setColor(Ui.BORDER);
            for (int r = 0; r <= 6; r++) g.drawLine(0, head() + (int) (r * ch), w, head() + (int) (r * ch));
            for (int c = 1; c < 7; c++) g.drawLine((int) (c * cw), head(), (int) (c * cw), getHeight());
            int rowH = 20;
            for (int i = 0; i < 42; i++) {
                LocalDate d = first.plusDays(i);
                int x = (int) (i % 7 * cw), y = head() + (int) (i / 7 * ch), cellW = (int) cw;
                boolean inMonth = d.getMonthValue() == anchor.getMonthValue();
                String num = String.valueOf(d.getDayOfMonth());
                g.setFont(Ui.font(12, true));
                FontMetrics fm = g.getFontMetrics();
                if (d.equals(today)) {
                    int pw = Math.max(22, fm.stringWidth(num) + 12);
                    g.setColor(Ui.ACCENT);
                    g.fill(new RoundRectangle2D.Double(x + 6, y + 5, pw, 20, 20, 20));
                    g.setColor(Ui.BG);
                    g.drawString(num, x + 6 + (pw - fm.stringWidth(num)) / 2, y + 19);
                } else {
                    g.setColor(inMonth ? Ui.TEXT : Ui.DIM);
                    g.drawString(num, x + 12, y + 19);
                }
                List<Model.Occ> day = new ArrayList<>();
                for (Model.Occ o : occs) if (onDay(o, d)) day.add(o);
                int avail = (int) ((ch - 30) / (rowH + 2));
                g.setFont(Ui.font(11, false));
                for (int k = 0; k < day.size(); k++) {
                    int ey = y + 28 + k * (rowH + 2);
                    if (k == avail - 1 && day.size() > avail) {
                        String more = "+" + (day.size() - k) + " altri";
                        g.setColor(Ui.OFF);
                        g.drawString(more, x + 8, ey + 14);
                        hits.add(new Hit(new Rectangle(x + 4, ey, cellW - 8, rowH), null, d));
                        break;
                    }
                    if (k >= avail) break;
                    chip(g, day.get(k), x + 4, ey, cellW - 8, rowH, true);
                    hits.add(new Hit(new Rectangle(x + 4, ey, cellW - 8, rowH), day.get(k), null));
                }
            }
        }

        @Override void empty(int x, int y, int clicks) {
            if (y < head()) return;
            int c = Math.min(6, (int) (x / (getWidth() / 7.0))), r = Math.min(5, (int) ((y - head()) / ((getHeight() - head()) / 6.0)));
            LocalDate d = monthStart(anchor).plusDays(r * 7L + c);
            if (clicks == 2) host.create(d, 9, 0);
        }
    }

    // ---------- settimana e giorno ----------

    static final int HOUR = 44, GUTTER = 56;

    /** Intestazione fissa: giorni e fascia degli eventi di tutto il giorno. */
    static final class WeekHead extends View {
        int days = 7;
        WeekHead(Host h) { super(h); }

        LocalDate first() { return days == 1 ? anchor : anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); }

        int bandRows() {
            int max = 0;
            for (int i = 0; i < days; i++) {
                int n = 0;
                for (Model.Occ o : occs) if (o.allDay && onDay(o, first().plusDays(i))) n++;
                max = Math.max(max, n);
            }
            return Math.min(max, 4);
        }

        @Override public Dimension getPreferredSize() { return new Dimension(400, 52 + bandRows() * 22 + 8); }

        @Override void paint(Graphics2D g) {
            LocalDate first = first(), today = LocalDate.now();
            double cw = (getWidth() - GUTTER) / (double) days;
            for (int i = 0; i < days; i++) {
                LocalDate d = first.plusDays(i);
                int x = GUTTER + (int) (i * cw);
                boolean t = d.equals(today);
                g.setFont(Ui.font(11, true));
                g.setColor(t ? Ui.ACCENT_TEXT : Ui.OFF);
                g.drawString(Ui.DAYS_SHORT[d.getDayOfWeek().getValue() - 1].toUpperCase(), x + 10, 18);
                g.setFont(Ui.font(20, true));
                g.setColor(t ? Ui.ACCENT_TEXT : Ui.TEXT);
                g.drawString(String.valueOf(d.getDayOfMonth()), x + 10, 42);
                if (days > 1) hits.add(new Hit(new Rectangle(x, 0, (int) cw, 48), null, d));
                int k = 0, n = 0;
                g.setFont(Ui.font(11, false));
                for (Model.Occ o : occs) {
                    if (!o.allDay || !onDay(o, d)) continue;
                    n++;
                    if (k >= 4) continue;
                    int y = 52 + k * 22;
                    chip(g, o, x + 3, y, (int) cw - 6, 20, false);
                    hits.add(new Hit(new Rectangle(x + 3, y, (int) cw - 6, 20), o, null));
                    k++;
                }
                if (n > 4) { g.setColor(Ui.OFF); g.drawString("+" + (n - 4), x + (int) cw - 26, 46); }
            }
            g.setColor(Ui.BORDER);
            g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
            for (int i = 0; i <= days; i++) g.drawLine(GUTTER + (int) (i * cw), 48, GUTTER + (int) (i * cw), getHeight());
            g.setFont(Ui.font(10, false));
            g.setColor(Ui.DIM);
            if (bandRows() > 0) g.drawString("tutto il", 8, 64);
        }

        @Override void empty(int x, int y, int clicks) {
            if (clicks != 2 || x < GUTTER || y < 48) return;
            int i = Math.min(days - 1, (int) ((x - GUTTER) / ((getWidth() - GUTTER) / (double) days)));
            host.create(first().plusDays(i), -1, 0);
        }
    }

    /** Griglia delle ore con gli eventi affiancati quando si sovrappongono. */
    static final class WeekBody extends View implements Scrollable {
        int days = 7;
        WeekBody(Host h) { super(h); }

        LocalDate first() { return days == 1 ? anchor : anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); }

        @Override public Dimension getPreferredSize() { return new Dimension(400, HOUR * 24 + 1); }

        @Override void paint(Graphics2D g) {
            LocalDate first = first();
            double cw = (getWidth() - GUTTER) / (double) days;
            g.setFont(Ui.font(10, false));
            for (int hr = 0; hr < 24; hr++) {
                int y = hr * HOUR;
                g.setColor(Ui.BORDER);
                g.drawLine(GUTTER, y, getWidth(), y);
                g.setColor(Ui.mix(Ui.BORDER, Ui.BG, 0.55f));
                g.drawLine(GUTTER, y + HOUR / 2, getWidth(), y + HOUR / 2);
                if (hr > 0) { g.setColor(Ui.DIM); g.drawString(String.format("%02d:00", hr), 10, y + 4); }
            }
            g.setColor(Ui.BORDER);
            for (int i = 0; i <= days; i++) g.drawLine(GUTTER + (int) (i * cw), 0, GUTTER + (int) (i * cw), getHeight());
            ZonedDateTime now = ZonedDateTime.now();
            for (int i = 0; i < days; i++) {
                LocalDate d = first.plusDays(i);
                ZonedDateTime ds = d.atStartOfDay(ZoneId.systemDefault()), de = d.plusDays(1).atStartOfDay(ZoneId.systemDefault());
                List<Model.Occ> list = new ArrayList<>();
                for (Model.Occ o : occs) if (!o.allDay && onDay(o, d)) list.add(o);
                layout(g, list, ds, de, GUTTER + (int) (i * cw), cw);
                if (d.equals(now.toLocalDate())) {
                    int y = (int) (minutes(ds, now) * HOUR / 60);
                    int x = GUTTER + (int) (i * cw);
                    g.setColor(Ui.ACCENT);
                    g.fillRect(x, y - 1, (int) cw, 2);
                    g.fill(new Ellipse2D.Double(x - 4, y - 4, 8, 8));
                }
            }
        }

        private static double minutes(ZonedDateTime from, ZonedDateTime t) {
            return java.time.Duration.between(from, t).toMinutes();
        }

        private void layout(Graphics2D g, List<Model.Occ> list, ZonedDateTime ds, ZonedDateTime de, int x0, double cw) {
            // gruppi di eventi che si sovrappongono: ognuno prende una colonna del gruppo
            int i = 0;
            while (i < list.size()) {
                List<Model.Occ> group = new ArrayList<>();
                List<ZonedDateTime> colEnd = new ArrayList<>();
                List<Integer> cols = new ArrayList<>();
                ZonedDateTime groupEnd = null;
                while (i < list.size()) {
                    Model.Occ o = list.get(i);
                    ZonedDateTime oe = visibleEnd(o);
                    if (groupEnd != null && !o.start.isBefore(groupEnd)) break;
                    int col = -1;
                    for (int c = 0; c < colEnd.size(); c++) if (!o.start.isBefore(colEnd.get(c))) { col = c; break; }
                    if (col < 0) { col = colEnd.size(); colEnd.add(oe); } else colEnd.set(col, oe);
                    group.add(o);
                    cols.add(col);
                    if (groupEnd == null || oe.isAfter(groupEnd)) groupEnd = oe;
                    i++;
                }
                int n = colEnd.size();
                for (int k = 0; k < group.size(); k++) {
                    Model.Occ o = group.get(k);
                    ZonedDateTime s = o.start.isBefore(ds) ? ds : o.start, e = visibleEnd(o).isAfter(de) ? de : visibleEnd(o);
                    int y = (int) (minutes(ds, s) * HOUR / 60), h = Math.max(18, (int) (minutes(s, e) * HOUR / 60) - 2);
                    double w = (cw - 6) / n;
                    int x = x0 + 3 + (int) (cols.get(k) * w);
                    block(g, o, x, y + 1, (int) w - 2, h);
                    hits.add(new Hit(new Rectangle(x, y + 1, (int) w - 2, h), o, null));
                }
            }
        }

        private static ZonedDateTime visibleEnd(Model.Occ o) {
            ZonedDateTime min = o.start.plusMinutes(30);
            return o.end.isAfter(min) ? o.end : min;
        }

        private void block(Graphics2D g, Model.Occ o, int x, int y, int w, int h) {
            Color c = colorOf(o);
            g.setColor(Ui.mix(c, Ui.BG, 0.7f));
            g.fill(new RoundRectangle2D.Double(x, y, w, h, 8, 8));
            g.setColor(c);
            g.fill(new RoundRectangle2D.Double(x, y, 3, h, 3, 3));
            Graphics2D t = (Graphics2D) g.create();
            t.clipRect(x, y, w, h);
            t.setFont(Ui.font(11, true));
            t.setColor(Ui.TEXT);
            FontMetrics fm = t.getFontMetrics();
            t.drawString(fit(fm, o.title(), w - 12), x + 8, y + 14);
            t.setFont(Ui.font(10, false));
            t.setColor(Ui.OFF);
            if (h >= 32) {
                String sub = Ui.time(o.start) + "–" + Ui.time(o.end);
                String loc = o.location();
                if (loc != null && !loc.isBlank()) sub += " · " + loc.trim();
                t.drawString(fit(t.getFontMetrics(), sub, w - 12), x + 8, y + 28);
            }
            t.dispose();
        }

        @Override void empty(int x, int y, int clicks) {
            if (clicks != 2 || x < GUTTER) return;
            int i = Math.min(days - 1, (int) ((x - GUTTER) / ((getWidth() - GUTTER) / (double) days)));
            int min = y * 60 / HOUR / 30 * 30;
            host.create(first().plusDays(i), min / 60, min % 60);
        }

        @Override public Dimension getPreferredScrollableViewportSize() { return new Dimension(600, HOUR * 12); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return HOUR / 2; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return HOUR * 6; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    // ---------- agenda ----------

    static final int AGENDA_DAYS = 60;

    static final class Agenda extends View implements Scrollable {
        Agenda(Host h) { super(h); }

        private int height = 200;

        @Override public Dimension getPreferredSize() { return new Dimension(400, height); }

        @Override void paint(Graphics2D g) {
            int y = 16;
            LocalDate last = null;
            int w = Math.min(getWidth() - 48, 760);
            int x = 24;
            if (occs.isEmpty()) {
                g.setFont(Ui.font(13, false));
                g.setColor(Ui.OFF);
                g.drawString("Nessun evento nei prossimi " + AGENDA_DAYS + " giorni", x, 40);
            }
            for (int d = 0; d < AGENDA_DAYS; d++) {
                LocalDate day = anchor.plusDays(d);
                for (Model.Occ o : occs) {
                    if (!onDay(o, day)) continue;
                    if (!day.equals(last)) {
                        y += last == null ? 8 : 18;
                        g.setFont(Ui.font(11, true));
                        boolean today = day.equals(LocalDate.now());
                        g.setColor(today ? Ui.ACCENT_TEXT : Ui.OFF);
                        String label = (today ? "OGGI · " : "") + Ui.DAYS[day.getDayOfWeek().getValue() - 1].toUpperCase() + " "
                                + day.getDayOfMonth() + " " + Ui.MONTHS[day.getMonthValue() - 1].toUpperCase();
                        g.drawString(label, x, y + 12);
                        y += 22;
                        last = day;
                    }
                    Rectangle r = new Rectangle(x, y, w, 44);
                    g.setColor(Ui.PANEL);
                    g.fill(new RoundRectangle2D.Double(r.x, r.y, r.width, r.height, 12, 12));
                    g.setColor(Ui.BORDER);
                    g.draw(new RoundRectangle2D.Double(r.x + 0.5, r.y + 0.5, r.width - 1, r.height - 1, 12, 12));
                    g.setColor(colorOf(o));
                    g.fill(new RoundRectangle2D.Double(r.x + 12, r.y + 12, 4, 20, 4, 4));
                    g.setFont(Ui.font(12, false));
                    g.setColor(Ui.OFF);
                    String when = o.allDay ? "tutto il giorno" : Ui.time(o.start) + "–" + Ui.time(o.end);
                    g.drawString(when, r.x + 28, r.y + 27);
                    g.setFont(Ui.font(13, true));
                    g.setColor(Ui.TEXT);
                    String title = o.title();
                    String loc = o.location();
                    FontMetrics fm = g.getFontMetrics();
                    int tx = r.x + 28 + 150;
                    g.drawString(fit(fm, title, w - 190 - (loc != null ? 160 : 0)), tx, r.y + 27);
                    if (loc != null && !loc.isBlank()) {
                        g.setFont(Ui.font(12, false));
                        g.setColor(Ui.OFF);
                        String l = fit(g.getFontMetrics(), loc.trim(), 150);
                        g.drawString(l, r.x + w - 16 - g.getFontMetrics().stringWidth(l), r.y + 27);
                    }
                    hits.add(new Hit(r, o, null));
                    y += 50;
                }
            }
            if (Math.abs(height - (y + 24)) > 2) {
                height = y + 24;
                revalidate();
            }
        }

        @Override public Dimension getPreferredScrollableViewportSize() { return new Dimension(600, 500); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 24; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return r.height - 40; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return getParent() != null && getParent().getHeight() > height; }
    }

    // ---------- mini mese ----------

    /** Mese piccolo della barra laterale: clic su un giorno per andarci. */
    static final class Mini extends JComponent {
        LocalDate shown = LocalDate.now().withDayOfMonth(1), selected = LocalDate.now();
        java.util.function.Consumer<LocalDate> onPick = d -> { };

        Mini() {
            setPreferredSize(new Dimension(208, 196));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 196));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    double cw = getWidth() / 7.0;
                    if (e.getY() < 28) {
                        if (e.getX() > getWidth() - 28) shown = shown.plusMonths(1);
                        else if (e.getX() > getWidth() - 56) shown = shown.minusMonths(1);
                        repaint();
                        return;
                    }
                    if (e.getY() < 50) return;
                    int c = Math.min(6, (int) (e.getX() / cw)), r = (e.getY() - 50) / 24;
                    if (r > 5) return;
                    LocalDate d = monthStart(shown).plusDays(r * 7L + c);
                    selected = d;
                    onPick.accept(d);
                    repaint();
                }
            });
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Ui.aa(g);
            g.setFont(Ui.font(12, true));
            g.setColor(Ui.TEXT);
            g.drawString(Ui.MONTHS[shown.getMonthValue() - 1] + " " + shown.getYear(), 4, 18);
            new Ui.Glyph("left", 16, Ui.OFF).paintIcon(this, g, getWidth() - 50, 5);
            new Ui.Glyph("right", 16, Ui.OFF).paintIcon(this, g, getWidth() - 22, 5);
            double cw = getWidth() / 7.0;
            g.setFont(Ui.font(10, true));
            g.setColor(Ui.DIM);
            for (int i = 0; i < 7; i++) {
                String s = Ui.DAYS_SHORT[i].substring(0, 1);
                g.drawString(s, (int) (i * cw + cw / 2 - g.getFontMetrics().stringWidth(s) / 2.0), 44);
            }
            LocalDate first = monthStart(shown), today = LocalDate.now();
            g.setFont(Ui.font(11, false));
            for (int i = 0; i < 42; i++) {
                LocalDate d = first.plusDays(i);
                String s = String.valueOf(d.getDayOfMonth());
                double cx = i % 7 * cw + cw / 2;
                int y = 50 + i / 7 * 24;
                if (d.equals(today)) {
                    g.setColor(Ui.ACCENT);
                    g.fill(new Ellipse2D.Double(cx - 11, y + 1, 22, 22));
                } else if (d.equals(selected)) {
                    g.setColor(Ui.CONTROL);
                    g.fill(new Ellipse2D.Double(cx - 11, y + 1, 22, 22));
                }
                g.setColor(d.equals(today) ? Ui.BG : d.getMonthValue() == shown.getMonthValue() ? Ui.TEXT : Ui.DIM);
                g.drawString(s, (int) (cx - g.getFontMetrics().stringWidth(s) / 2.0), y + 16);
            }
            g.dispose();
        }
    }
}
