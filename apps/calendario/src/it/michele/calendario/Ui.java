package it.michele.calendario;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.text.JTextComponent;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Kit "Arancio UI" comune alle mie app, per Swing: grigi caldi, colore principale a scelta (arancio di base),
 * JetBrains Mono, pulsanti con raggio 4, card con raggio 12 e bordo, icone a tratto 1.75.
 */
final class Ui {
    static final Color BG = new Color(0x1C1B1A), PANEL = new Color(0x252422), RAISED = new Color(0x2E2C2A),
            BORDER = new Color(0x3A3734), CONTROL = new Color(0x45413D), TEXT = new Color(0xEDEAE3),
            OFF = new Color(0xA39E95), DIM = new Color(0x6E6963), DANGER = new Color(0xF0948A),
            DANGER_BG = new Color(0x3A2523), DANGER_BORDER = new Color(0x6B3631);

    static final String[] THEME_NAMES = {"Arancio", "Viola", "Rosso", "Giallo", "Blu", "Verde"};
    /** principale, testo, tenue, bordo tenue, fondo tenue */
    private static final int[][] THEMES = {
            {0xD96D00, 0xF08A2A, 0xF5A04F, 0x6B4214, 0x36230F},
            {0x9D6BFF, 0xB48CFF, 0xC4A3FF, 0x4A3577, 0x261E3A},
            {0xEC5A5A, 0xF27C7C, 0xF59A9A, 0x6E2A2A, 0x3A1F1F},
            {0xE5B000, 0xF2C230, 0xF5D060, 0x6B5410, 0x332A0F},
            {0x4A90FF, 0x6BA6FF, 0x8AB8FF, 0x24426E, 0x17253A},
            {0x2FB36B, 0x4FCB87, 0x6FD69E, 0x1F5A3A, 0x15291F},
    };
    static Color ACCENT, ACCENT_TEXT, ACCENT_SOFT, ACCENT_BORDER, ACCENT_DEEP;

    static final String[] MONTHS = {"Gennaio", "Febbraio", "Marzo", "Aprile", "Maggio", "Giugno", "Luglio",
            "Agosto", "Settembre", "Ottobre", "Novembre", "Dicembre"};
    static final String[] DAYS_SHORT = {"Lun", "Mar", "Mer", "Gio", "Ven", "Sab", "Dom"};
    static final String[] DAYS = {"Lunedì", "Martedì", "Mercoledì", "Giovedì", "Venerdì", "Sabato", "Domenica"};

    private static Font base;

    private Ui() {}

    static void theme(int t) {
        int[] c = THEMES[t];
        ACCENT = new Color(c[0]); ACCENT_TEXT = new Color(c[1]); ACCENT_SOFT = new Color(c[2]);
        ACCENT_BORDER = new Color(c[3]); ACCENT_DEEP = new Color(c[4]);
    }

    static Color themeColor(int t) { return new Color(THEMES[t][0]); }

    static Font font(float size, boolean bold) {
        if (base == null) {
            try (InputStream in = Ui.class.getResourceAsStream("/jbmono.ttf")) {
                base = Font.createFont(Font.TRUETYPE_FONT, in);
            } catch (Exception e) {
                base = new Font(Font.MONOSPACED, Font.PLAIN, 12);
            }
        }
        return base.deriveFont(bold ? Font.BOLD : Font.PLAIN, size);
    }

    /** Colori dei controlli Swing standard (menu, suggerimenti, finestre di sistema). */
    static void install() {
        UIManager.put("Panel.background", BG);
        UIManager.put("Viewport.background", BG);
        UIManager.put("ScrollPane.background", BG);
        UIManager.put("PopupMenu.background", RAISED);
        UIManager.put("PopupMenu.border", BorderFactory.createLineBorder(BORDER));
        UIManager.put("MenuItem.background", RAISED);
        UIManager.put("MenuItem.foreground", TEXT);
        UIManager.put("MenuItem.selectionBackground", CONTROL);
        UIManager.put("MenuItem.selectionForeground", TEXT);
        UIManager.put("MenuItem.acceleratorForeground", OFF);
        UIManager.put("MenuItem.font", font(13, false));
        UIManager.put("ToolTip.background", RAISED);
        UIManager.put("ToolTip.foreground", TEXT);
        UIManager.put("ToolTip.border", BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        UIManager.put("ToolTip.font", font(12, false));
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("Label.font", font(13, false));
        UIManager.put("OptionPane.background", PANEL);
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("TextField.selectionBackground", ACCENT_BORDER);
        UIManager.put("TextArea.selectionBackground", ACCENT_BORDER);
        UIManager.put("TextField.selectionForeground", TEXT);
        UIManager.put("TextArea.selectionForeground", TEXT);
    }

    static void aa(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }

    static Color mix(Color a, Color b, float t) {
        return new Color(Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    /** Colore di un calendario ("#RRGGBB" o "#RRGGBBAA" dal server), altrimenti il principale. */
    static Color color(String hex) {
        if (hex != null && hex.matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) return new Color(Integer.parseInt(hex.substring(1, 7), 16));
        return ACCENT;
    }

    // ---------- testo ----------

    static JLabel label(String s, float size, Color color, boolean bold) {
        JLabel l = new JLabel(s);
        l.setFont(font(size, bold));
        l.setForeground(color);
        return l;
    }

    /** Titolo di sezione: 11, maiuscolo, spaziato, secondario. */
    static JLabel section(String s) {
        JLabel l = label(s.toUpperCase(), 11, OFF, true);
        l.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        return l;
    }

    // ---------- icone ----------

    /** Icona a tratto disegnata su una griglia 24×24, come le icone SVG del kit. */
    static class Glyph implements Icon {
        final String name;
        final int size;
        Color color;

        Glyph(String name, int size, Color color) { this.name = name; this.size = size; this.color = color; }

        @Override public int getIconWidth() { return size; }
        @Override public int getIconHeight() { return size; }

        @Override public void paintIcon(Component c, Graphics g0, int x, int y) {
            Graphics2D g = (Graphics2D) g0.create();
            aa(g);
            g.translate(x, y);
            g.scale(size / 24.0, size / 24.0);
            Color col = color;
            if (c instanceof AbstractButton && !c.isEnabled()) col = DIM;
            g.setColor(col);
            g.setStroke(new BasicStroke(1.75f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            draw(g, name);
            g.dispose();
        }
    }

    static void draw(Graphics2D g, String name) {
        switch (name) {
            case "plus": line(g, 12, 5, 12, 19); line(g, 5, 12, 19, 12); break;
            case "left": poly(g, 15, 6, 9, 12, 15, 18); break;
            case "right": poly(g, 9, 6, 15, 12, 9, 18); break;
            case "down": poly(g, 6, 9, 12, 15, 18, 9); break;
            case "close": line(g, 6, 6, 18, 18); line(g, 18, 6, 6, 18); break;
            case "menu": line(g, 4, 7, 20, 7); line(g, 4, 12, 20, 12); line(g, 4, 17, 20, 17); break;
            case "check": poly(g, 5, 12.5, 10, 17, 19, 7); break;
            case "search":
                g.draw(new Ellipse2D.Double(5, 5, 12, 12)); line(g, 15.5, 15.5, 20, 20); break;
            case "refresh":
                g.draw(new Arc2D.Double(5, 5, 14, 14, 60, 290, Arc2D.OPEN));
                poly(g, 15.2, 3.6, 15.5, 6.9, 12.2, 7.3); break;
            case "settings":
                line(g, 4, 7, 20, 7); line(g, 4, 12, 20, 12); line(g, 4, 17, 20, 17);
                for (double[] k : new double[][]{{15, 7}, {9, 12}, {13, 17}}) {
                    Color c = g.getColor();
                    g.setColor(BG);
                    g.fill(new Ellipse2D.Double(k[0] - 2.2, k[1] - 2.2, 4.4, 4.4));
                    g.setColor(c);
                    g.draw(new Ellipse2D.Double(k[0] - 2.2, k[1] - 2.2, 4.4, 4.4));
                }
                break;
            case "user":
                g.draw(new Ellipse2D.Double(8, 4, 8, 8));
                Path2D p = new Path2D.Double();
                p.moveTo(4.5, 20); p.curveTo(5.5, 16, 8.5, 14.5, 12, 14.5); p.curveTo(15.5, 14.5, 18.5, 16, 19.5, 20);
                g.draw(p); break;
            case "trash":
                line(g, 4, 7, 20, 7); poly(g, 9, 7, 9, 4, 15, 4, 15, 7); poly(g, 6, 7, 7, 20, 17, 20, 18, 7);
                line(g, 10, 11, 10, 16); line(g, 14, 11, 14, 16); break;
            case "bell":
                Path2D b = new Path2D.Double();
                b.moveTo(4.5, 17); b.lineTo(6, 15); b.lineTo(6, 11); b.curveTo(6, 7, 8.7, 4.5, 12, 4.5);
                b.curveTo(15.3, 4.5, 18, 7, 18, 11); b.lineTo(18, 15); b.lineTo(19.5, 17); b.closePath();
                g.draw(b); g.draw(new Arc2D.Double(10, 18, 4, 3, 180, 180, Arc2D.OPEN)); break;
            case "calendar":
                g.draw(new RoundRectangle2D.Double(4, 5, 16, 15, 4, 4)); line(g, 4, 10, 20, 10);
                line(g, 8.5, 3, 8.5, 7); line(g, 15.5, 3, 15.5, 7); break;
            case "clock":
                g.draw(new Ellipse2D.Double(4, 4, 16, 16)); poly(g, 12, 8, 12, 12, 15, 14); break;
            case "pin":
                Path2D q = new Path2D.Double();
                q.moveTo(12, 21); q.curveTo(8, 16.5, 5.5, 13, 5.5, 10); q.curveTo(5.5, 6.4, 8.4, 3.5, 12, 3.5);
                q.curveTo(15.6, 3.5, 18.5, 6.4, 18.5, 10); q.curveTo(18.5, 13, 16, 16.5, 12, 21);
                g.draw(q); g.draw(new Ellipse2D.Double(9.5, 7.5, 5, 5)); break;
            case "repeat":
                poly(g, 17, 3.5, 20, 6.5, 17, 9.5); poly(g, 4, 11.5, 4, 9.5, 6, 6.5, 20, 6.5);
                poly(g, 7, 20.5, 4, 17.5, 7, 14.5); poly(g, 20, 12.5, 20, 14.5, 18, 17.5, 4, 17.5); break;
            case "alert":
                Path2D t = new Path2D.Double();
                t.moveTo(12, 4); t.lineTo(21, 19.5); t.lineTo(3, 19.5); t.closePath();
                g.draw(t); line(g, 12, 10, 12, 14); g.fill(new Ellipse2D.Double(11.1, 16, 1.8, 1.8)); break;
            default: break;
        }
    }

    private static void line(Graphics2D g, double x1, double y1, double x2, double y2) {
        g.draw(new Line2D.Double(x1, y1, x2, y2));
    }

    private static void poly(Graphics2D g, double... pts) {
        Path2D p = new Path2D.Double();
        p.moveTo(pts[0], pts[1]);
        for (int i = 2; i < pts.length; i += 2) p.lineTo(pts[i], pts[i + 1]);
        g.draw(p);
    }

    /** Icona dell'app (specifica "Icone delle app"): fondo #1C1B1A, raggio 12%, calendario a tratto nel 76% centrale. */
    static BufferedImage appIcon(int px, Color accent) {
        BufferedImage img = new BufferedImage(px, px, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        aa(g);
        g.setColor(BG);
        g.fill(new RoundRectangle2D.Double(0, 0, px, px, px * 0.24, px * 0.24));
        double inner = px * 0.76;
        g.translate((px - inner) / 2, (px - inner) / 2);
        g.scale(inner / 20.0, inner / 20.0);
        g.translate(-2, -2);
        g.setColor(accent);
        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new RoundRectangle2D.Double(4, 5, 16, 15, 4, 4));
        line(g, 4, 10, 20, 10);
        line(g, 8.5, 3, 8.5, 7);
        line(g, 15.5, 3, 15.5, 7);
        g.fill(new RoundRectangle2D.Double(13.2, 13.2, 3.6, 3.6, 1, 1));
        g.dispose();
        return img;
    }

    static List<Image> appIcons() {
        List<Image> l = new ArrayList<>();
        for (int s : new int[]{16, 24, 32, 48, 64, 128, 256}) l.add(appIcon(s, ACCENT));
        return l;
    }

    /** Scrive icon.png (256), icon-N.png e icon.ico (16-256, PNG dentro ICO) per i pacchetti; usato da build.sh. */
    static void exportIcons(Path dir) throws IOException {
        Files.createDirectories(dir);
        Color orange = themeColor(0);
        ImageIO.write(appIcon(256, orange), "png", dir.resolve("icon.png").toFile());
        int[] sizes = {16, 24, 32, 48, 64, 128, 256};
        List<byte[]> pngs = new ArrayList<>();
        for (int s : sizes) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            ImageIO.write(appIcon(s, orange), "png", b);
            pngs.add(b.toByteArray());
            Files.write(dir.resolve("icon-" + s + ".png"), b.toByteArray());
        }
        try (OutputStream out = Files.newOutputStream(dir.resolve("icon.ico"));
             DataOutputStream d = new DataOutputStream(out)) {
            d.writeShort(0); d.writeShort(Short.reverseBytes((short) 1)); d.writeShort(Short.reverseBytes((short) sizes.length));
            int offset = 6 + 16 * sizes.length;
            for (int i = 0; i < sizes.length; i++) {
                int s = sizes[i] >= 256 ? 0 : sizes[i];
                d.writeByte(s); d.writeByte(s); d.writeByte(0); d.writeByte(0);
                d.writeShort(Short.reverseBytes((short) 1)); d.writeShort(Short.reverseBytes((short) 32));
                d.writeInt(Integer.reverseBytes(pngs.get(i).length)); d.writeInt(Integer.reverseBytes(offset));
                offset += pngs.get(i).length;
            }
            for (byte[] p : pngs) d.write(p);
        }
    }

    // ---------- pulsanti ----------

    static final int PRIMARY = 0, SECONDARY = 1, GHOST = 2, DANGER_BTN = 3;

    /** Pulsante del kit, raggio 4, con eventuale icona. */
    static class Btn extends JButton {
        final int kind;
        boolean selected;

        Btn(String text, String icon, int kind) {
            super(text);
            this.kind = kind;
            setFont(font(13, kind == PRIMARY));
            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            boolean iconOnly = text == null || text.isEmpty();
            setBorder(BorderFactory.createEmptyBorder(0, iconOnly ? 0 : 14, 0, iconOnly ? 0 : 14));
            if (icon != null) {
                setIcon(new Glyph(icon, iconOnly ? 18 : 16, TEXT));
                setIconTextGap(8);
            }
            Dimension d = getPreferredSize();
            setPreferredSize(new Dimension(iconOnly ? 36 : d.width, 36));
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            aa(g);
            boolean hover = getModel().isRollover() && isEnabled(), down = getModel().isPressed();
            Color fill = null, stroke = null, fg = TEXT;
            switch (kind) {
                case PRIMARY: fill = !isEnabled() ? CONTROL : hover ? ACCENT_TEXT : ACCENT; fg = BG; break;
                case SECONDARY: fill = hover ? CONTROL : RAISED; stroke = CONTROL; break;
                case DANGER_BTN: fill = hover ? DANGER_BORDER : DANGER_BG; stroke = DANGER_BORDER; fg = DANGER; break;
                default: if (hover || selected) fill = selected ? RAISED : PANEL; if (selected) stroke = CONTROL; break;
            }
            if (down && fill != null) fill = fill.darker();
            if (!isEnabled()) fg = DIM;
            if (fill != null) {
                g.setColor(fill);
                g.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 8, 8));
            }
            if (stroke != null) {
                g.setColor(stroke);
                g.draw(new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1, 8, 8));
            }
            if (isFocusOwner()) {
                g.setColor(ACCENT);
                g.draw(new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1, 8, 8));
            }
            setForeground(fg);
            if (getIcon() instanceof Glyph) ((Glyph) getIcon()).color = fg;
            g.dispose();
            super.paintComponent(g0);
        }
    }

    static Btn button(String text, int kind, Runnable action) {
        Btn b = new Btn(text, null, kind);
        if (action != null) b.addActionListener(e -> action.run());
        return b;
    }

    static Btn iconButton(String icon, String tip, Runnable action) {
        Btn b = new Btn(null, icon, GHOST);
        b.setToolTipText(tip);
        b.getAccessibleContext().setAccessibleName(tip);
        if (action != null) b.addActionListener(e -> action.run());
        return b;
    }

    // ---------- campi ----------

    /** Bordo arrotondato dei campi: colore principale quando hanno il fuoco. */
    static final class FieldBorder extends AbstractBorder {
        @Override public void paintBorder(Component c, Graphics g0, int x, int y, int w, int h) {
            Graphics2D g = (Graphics2D) g0.create();
            aa(g);
            g.setColor(c.isFocusOwner() || (c.getParent() != null && c.getParent().getParent() instanceof JScrollPane
                    && c.isFocusOwner()) ? ACCENT : CONTROL);
            g.draw(new RoundRectangle2D.Double(x + 0.5, y + 0.5, w - 1, h - 1, 8, 8));
            g.dispose();
        }

        @Override public Insets getBorderInsets(Component c) { return new Insets(8, 10, 8, 10); }
    }

    static void styleText(JTextComponent t) {
        t.setFont(font(13, false));
        t.setBackground(BG);
        t.setForeground(TEXT);
        t.setCaretColor(ACCENT_TEXT);
        t.setSelectionColor(ACCENT_BORDER);
        t.setSelectedTextColor(TEXT);
        t.setDisabledTextColor(OFF);
        t.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { repaintBorder(t); }
            @Override public void focusLost(FocusEvent e) { repaintBorder(t); }
        });
    }

    private static void repaintBorder(JComponent t) {
        Component p = t.getParent() != null && t.getParent().getParent() instanceof JScrollPane ? t.getParent().getParent() : t;
        p.repaint();
    }

    /** Campo di testo con suggerimento grigio quando è vuoto. */
    static JTextField field(String hint) {
        JTextField f = new JTextField() {
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                aa(g);
                g.setColor(getBackground());
                g.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 8, 8));
                g.dispose();
                super.paintComponent(g0);
                if (getText().isEmpty() && hint != null && !isFocusOwner()) {
                    Graphics2D h = (Graphics2D) g0.create();
                    aa(h);
                    h.setColor(DIM);
                    h.setFont(getFont());
                    Insets in = getInsets();
                    h.drawString(hint, in.left, in.top + h.getFontMetrics().getAscent());
                    h.dispose();
                }
            }
        };
        f.setOpaque(false);
        styleText(f);
        f.setBorder(new FieldBorder());
        return f;
    }

    static JTextArea area() {
        JTextArea a = new JTextArea(3, 20);
        styleText(a);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        // Tab sposta il fuoco invece di scrivere un carattere
        a.setFocusTraversalKeys(java.awt.KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, null);
        a.setFocusTraversalKeys(java.awt.KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, null);
        return a;
    }

    /** Scelta da un elenco: riquadro con valore e freccia, menu a comparsa nello stile del kit. */
    static final class Select<T> extends Btn {
        private final List<T> items = new ArrayList<>();
        private final Function<T, String> label;
        private T value;
        Consumer<T> onChange = v -> { };

        Select(List<T> items, Function<T, String> label) {
            super("", null, SECONDARY);
            this.label = label;
            this.items.addAll(items);
            setFont(font(13, false));
            setHorizontalAlignment(SwingConstants.LEFT);
            setHorizontalTextPosition(SwingConstants.LEFT);
            setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
            setIcon(new Glyph("down", 14, OFF));
            addActionListener(e -> open());
            if (!items.isEmpty()) set(items.get(0));
        }

        void items(List<T> l) { items.clear(); items.addAll(l); }

        void set(T v) {
            value = v;
            setText(label.apply(v));
        }

        T get() { return value; }

        @Override public Dimension getPreferredSize() { return new Dimension(super.getPreferredSize().width, 36); }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            aa(g);
            g.setColor(BG);
            g.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 8, 8));
            g.setColor(isFocusOwner() ? ACCENT : getModel().isRollover() && isEnabled() ? OFF : CONTROL);
            g.draw(new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1, 8, 8));
            g.setFont(getFont());
            g.setColor(isEnabled() ? TEXT : OFF);
            java.awt.FontMetrics fm = g.getFontMetrics();
            String s = getText();
            int max = getWidth() - 40;
            while (s.length() > 1 && fm.stringWidth(s) > max) s = s.substring(0, s.length() - 2) + "…";
            g.drawString(s, 10, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            if (isEnabled()) new Glyph("down", 14, OFF).paintIcon(this, g, getWidth() - 24, (getHeight() - 14) / 2);
            g.dispose();
        }

        private void open() {
            JPopupMenu m = popup();
            for (T it : items) {
                JMenuItem mi = menuItem(label.apply(it), () -> { set(it); onChange.accept(it); });
                if (java.util.Objects.equals(it, value)) mi.setForeground(ACCENT_TEXT);
                m.add(mi);
            }
            m.setPreferredSize(new Dimension(Math.max(getWidth(), m.getPreferredSize().width), m.getPreferredSize().height));
            m.show(this, 0, getHeight() + 2);
        }
    }

    static JPopupMenu popup() {
        JPopupMenu m = new JPopupMenu();
        m.setBackground(RAISED);
        m.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(4, 0, 4, 0)));
        return m;
    }

    static JMenuItem menuItem(String text, Runnable action) {
        JMenuItem mi = new JMenuItem(text);
        mi.setFont(font(13, false));
        mi.setBackground(RAISED);
        mi.setForeground(TEXT);
        mi.setBorder(BorderFactory.createEmptyBorder(7, 12, 7, 16));
        mi.addActionListener(e -> action.run());
        return mi;
    }

    /** Interruttore del kit. */
    static final class Toggle extends JComponent {
        boolean on;
        Consumer<Boolean> onChange = v -> { };

        Toggle(boolean on) {
            this.on = on;
            setPreferredSize(new Dimension(40, 22));
            setMinimumSize(getPreferredSize());
            setMaximumSize(getPreferredSize());
            setFocusable(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseClicked(java.awt.event.MouseEvent e) { if (isEnabled()) flip(); }
            });
            addKeyListener(new java.awt.event.KeyAdapter() {
                @Override public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_SPACE && isEnabled()) flip();
                }
            });
            addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent e) { repaint(); }
                @Override public void focusLost(FocusEvent e) { repaint(); }
            });
        }

        void flip() { set(!on); onChange.accept(on); }

        void set(boolean v) { on = v; repaint(); }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            aa(g);
            int w = 40, h = 22, y = (getHeight() - h) / 2;
            g.setColor(on ? (isEnabled() ? ACCENT : ACCENT_BORDER) : CONTROL);
            g.fill(new RoundRectangle2D.Double(0, y, w, h, h, h));
            if (isFocusOwner()) {
                g.setColor(ACCENT_TEXT);
                g.draw(new RoundRectangle2D.Double(0.5, y + 0.5, w - 1, h - 1, h, h));
            }
            g.setColor(on ? BG : OFF);
            g.fill(new Ellipse2D.Double(on ? w - h + 3 : 3, y + 3, h - 6, h - 6));
            g.dispose();
        }
    }

    // ---------- contenitori ----------

    /** Pannello con fondo, raggio e bordo del kit (card raggio 12). */
    static JPanel card(Color fill, Color stroke, int radius) {
        JPanel p = new JPanel() {
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                aa(g);
                g.setColor(fill);
                g.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), radius * 2, radius * 2));
                if (stroke != null) {
                    g.setColor(stroke);
                    g.draw(new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1, radius * 2, radius * 2));
                }
                g.dispose();
            }
        };
        p.setOpaque(false);
        return p;
    }

    static JPanel column() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        return p;
    }

    static JPanel row(int gap, Component... cs) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, gap, 0));
        p.setOpaque(false);
        for (Component c : cs) p.add(c);
        return p;
    }

    /** Barra di scorrimento sottile e scura. */
    static JScrollPane scroll(Component c) {
        JScrollPane s = new JScrollPane(c);
        s.setBorder(BorderFactory.createEmptyBorder());
        s.getViewport().setBackground(BG);
        s.setBackground(BG);
        styleBar(s.getVerticalScrollBar());
        styleBar(s.getHorizontalScrollBar());
        s.getVerticalScrollBar().setUnitIncrement(24);
        return s;
    }

    static void styleBar(JScrollBar b) {
        b.setPreferredSize(new Dimension(10, 10));
        b.setBackground(BG);
        b.setUI(new BasicScrollBarUI() {
            @Override protected void configureScrollBarColors() { thumbColor = CONTROL; trackColor = BG; }
            @Override protected JButton createDecreaseButton(int o) { return zero(); }
            @Override protected JButton createIncreaseButton(int o) { return zero(); }
            private JButton zero() {
                JButton z = new JButton();
                z.setPreferredSize(new Dimension(0, 0));
                return z;
            }
            @Override protected void paintThumb(Graphics g0, JComponent c, java.awt.Rectangle r) {
                if (r.isEmpty()) return;
                Graphics2D g = (Graphics2D) g0.create();
                aa(g);
                g.setColor(isThumbRollover() ? OFF : CONTROL);
                g.fill(new RoundRectangle2D.Double(r.x + 2, r.y + 2, r.width - 4, r.height - 4, 6, 6));
                g.dispose();
            }
            @Override protected void paintTrack(Graphics g, JComponent c, java.awt.Rectangle r) {
                g.setColor(BG);
                g.fillRect(r.x, r.y, r.width, r.height);
            }
        });
    }

    /** Campo con etichetta sopra (etichette 12, secondarie). */
    static JPanel labeled(String label, Component field) {
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setOpaque(false);
        p.add(label(label, 12, OFF, false), BorderLayout.NORTH);
        p.add(field, BorderLayout.CENTER);
        p.setAlignmentX(0);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height));
        return p;
    }

    static Component gap(int h) { return Box.createVerticalStrut(h); }

    static Border pad(int t, int l, int b, int r) { return BorderFactory.createEmptyBorder(t, l, b, r); }

    /** Finestra di dialogo modale scura con titolo e contenuto; Esc chiude. */
    static JDialog dialog(Window owner, String title) {
        JDialog d = new JDialog(owner, title, java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        d.getContentPane().setBackground(PANEL);
        d.setIconImages(appIcons());
        d.getRootPane().registerKeyboardAction(e -> d.dispose(), javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        return d;
    }

    /** Area utile dello schermo su cui si trova la finestra (senza barre di sistema). */
    static java.awt.Rectangle screen(Window w) {
        java.awt.GraphicsConfiguration gc = w != null ? w.getGraphicsConfiguration()
                : java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
        java.awt.Rectangle r = gc.getBounds();
        Insets in = java.awt.Toolkit.getDefaultToolkit().getScreenInsets(gc);
        return new java.awt.Rectangle(r.x + in.left, r.y + in.top, r.width - in.left - in.right, r.height - in.top - in.bottom);
    }

    /** Contenuto che segue la larghezza della finestra e scorre in verticale quando lo schermo è basso. */
    private static final class Fit extends JPanel implements javax.swing.Scrollable {
        Fit(Component c) { super(new BorderLayout()); setOpaque(false); add(c); }
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(java.awt.Rectangle r, int o, int d) { return 24; }
        @Override public int getScrollableBlockIncrement(java.awt.Rectangle r, int o, int d) { return r.height - 40; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return getParent() != null && getParent().getHeight() > getPreferredSize().height; }
    }

    /**
     * Mette nel dialogo il contenuto (che scorre se serve) e i pulsanti sempre visibili in fondo,
     * con una dimensione che sta nello schermo anche piccolo o con la scala alta.
     */
    static void place(JDialog d, JComponent body, JComponent footer, int width) {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(PANEL);
        body.setBorder(pad(20, 20, 4, 20));
        JScrollPane sp = scroll(new Fit(body));
        sp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sp.getViewport().setBackground(PANEL);
        sp.setBackground(PANEL);
        root.add(sp, BorderLayout.CENTER);
        footer.setBorder(pad(10, 20, 16, 20));
        root.add(footer, BorderLayout.SOUTH);
        d.setContentPane(root);
        d.pack();
        java.awt.Rectangle scr = screen(d.getOwner());
        int w = Math.min(Math.max(width, d.getWidth()), scr.width - 32), h = Math.min(d.getHeight(), scr.height - 32);
        d.setSize(w, h);
        d.setMinimumSize(new Dimension(Math.min(360, w), Math.min(300, h)));
        d.setLocationRelativeTo(d.getOwner());
    }

    /** Domanda con più risposte (l'ultima è quella principale); restituisce l'indice scelto o -1. */
    static int ask(Window owner, String title, String message, int dangerIndex, String... options) {
        JDialog d = dialog(owner, title);
        JPanel body = column();
        body.setBorder(pad(20, 20, 16, 20));
        JLabel t = label(title, 17, TEXT, true);
        t.setAlignmentX(0);
        body.add(t);
        body.add(gap(10));
        JLabel m = label("<html><div style='width:320px'>" + html(message) + "</div></html>", 13, OFF, false);
        m.setAlignmentX(0);
        body.add(m);
        body.add(gap(20));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.setAlignmentX(0);
        final int[] choice = {-1};
        Btn last = null;
        for (int i = 0; i < options.length; i++) {
            final int idx = i;
            int kind = i == dangerIndex ? DANGER_BTN : i == options.length - 1 && options.length > 1 ? PRIMARY : SECONDARY;
            if (options.length == 1) kind = PRIMARY;
            if (i == 0 && options.length > 1 && dangerIndex != 0) kind = GHOST;
            Btn b = button(options[i], kind, () -> { choice[0] = idx; d.dispose(); });
            buttons.add(b);
            last = b;
        }
        body.add(buttons);
        d.setContentPane(body);
        body.setBackground(PANEL);
        body.setOpaque(true);
        d.pack();
        d.setResizable(false);
        d.setLocationRelativeTo(owner);
        if (last != null) d.getRootPane().setDefaultButton(last);
        d.setVisible(true);
        return choice[0];
    }

    static void message(Window owner, String title, String message) {
        ask(owner, title, message, -1, "OK");
    }

    static String html(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
    }

    /** "sab 8 novembre" */
    static String day(java.time.LocalDate d) {
        return DAYS_SHORT[d.getDayOfWeek().getValue() - 1].toLowerCase() + " " + d.getDayOfMonth() + " " + MONTHS[d.getMonthValue() - 1].toLowerCase();
    }

    static String time(java.time.ZonedDateTime t) {
        return String.format("%02d:%02d", t.getHour(), t.getMinute());
    }
}
