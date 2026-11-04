package it.michele.sveglia;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.graphics.fonts.Font;
import android.graphics.fonts.FontFamily;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Kit "Arancio UI" comune alle mie app: grigi caldi, colore principale a scelta (arancio di base),
 * JetBrains Mono, pulsanti con raggio 4, card con raggio 12 e bordo, icone SVG a tratto 1.75.
 */
final class Ui {
    static final int BG = 0xFF1C1B1A;
    static final int PANEL = 0xFF252422;
    static final int RAISED = 0xFF2E2C2A;
    static final int BORDER = 0xFF3A3734;
    /** bordo di campi e pulsanti secondari */
    static final int CONTROL = 0xFF45413D;
    static final int TEXT = 0xFFEDEAE3;
    static final int OFF = 0xFFA39E95;
    static final int OK = 0xFF6CC4B6;
    static final int OK_BG = 0x295DB3A6;
    static final int DANGER = 0xFFF0948A;
    static final int DANGER_BG = 0xFF3A2523;
    static final int DANGER_BORDER = 0xFF6B3631;

    /** Colori principali del kit: nome, principale, testo, etichetta, bordo tenue, fondo tenue. */
    static final String[] THEME_NAMES = {"Arancio", "Viola", "Rosso", "Giallo", "Blu", "Verde"};
    private static final int[][] THEMES = {
            {0xFFD96D00, 0xFFF08A2A, 0xFFF5A04F, 0xFF6B4214, 0xFF36230F},
            {0xFF9D6BFF, 0xFFB48CFF, 0xFFC4A3FF, 0xFF4A3577, 0xFF261E3A},
            {0xFFEC5A5A, 0xFFF27C7C, 0xFFF59A9A, 0xFF6E2A2A, 0xFF3A1F1F},
            {0xFFE5B000, 0xFFF2C230, 0xFFF5D060, 0xFF6B5410, 0xFF332A0F},
            {0xFF4A90FF, 0xFF6BA6FF, 0xFF8AB8FF, 0xFF24426E, 0xFF17253A},
            {0xFF2FB36B, 0xFF4FCB87, 0xFF6FD69E, 0xFF1F5A3A, 0xFF15291F},
    };
    static int ACCENT, ACCENT_TEXT, ACCENT_SOFT, ACCENT_BORDER, ACCENT_DEEP;

    private static final SparseArray<Typeface> FONTS = new SparseArray<>();

    private Ui() {}

    static int themeColor(int theme) { return THEMES[theme][0]; }

    /** Carica il colore principale scelto e colora le barre di sistema. Da chiamare in ogni onCreate. */
    static void apply(Activity a) {
        int[] t = THEMES[new Store(a).theme()];
        ACCENT = t[0]; ACCENT_TEXT = t[1]; ACCENT_SOFT = t[2]; ACCENT_BORDER = t[3]; ACCENT_DEEP = t[4];
        a.getWindow().setStatusBarColor(BG);
        a.getWindow().setNavigationBarColor(BG);
    }

    static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** JetBrains Mono (variabile, ridotta ai caratteri latini) col peso richiesto. */
    static Typeface font(Context c, int weight) {
        Typeface t = FONTS.get(weight);
        if (t != null) return t;
        try {
            Font f = new Font.Builder(c.getResources(), R.font.jbmono)
                    .setWeight(weight).setFontVariationSettings("'wght' " + weight).build();
            t = new Typeface.CustomFallbackBuilder(new FontFamily.Builder(f).build())
                    .setSystemFallback("sans-serif").build();
        } catch (Exception e) {
            t = Typeface.create(Typeface.MONOSPACE, weight, false);
        }
        FONTS.put(weight, t);
        return t;
    }

    static TextView text(Context c, String s, int sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(font(c, bold ? 600 : 400));
        return t;
    }

    /** Titolo di sezione: 13, maiuscolo, spaziato, secondario. */
    static TextView section(Context c, String s) {
        TextView t = text(c, s.toUpperCase(Locale.ITALY), 13, OFF, true);
        t.setLetterSpacing(0.08f);
        return t;
    }

    static GradientDrawable rounded(int color, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        return g;
    }

    static GradientDrawable box(Context c, int fill, int stroke, int radiusDp) {
        GradientDrawable g = rounded(fill, dp(c, radiusDp));
        if (stroke != 0) g.setStroke(dp(c, 1), stroke);
        return g;
    }

    /** Sfondo con effetto al tocco. */
    static Drawable touch(Context c, Drawable bg, int radiusDp) {
        return new RippleDrawable(ColorStateList.valueOf(0x22EDEAE3), bg, rounded(0xFFFFFFFF, dp(c, radiusDp)));
    }

    /** Card del kit: pannello, bordo, raggio 12. */
    static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(box(c, PANEL, BORDER, 12));
        int p = dp(c, 16);
        l.setPadding(p, p, p, p);
        return l;
    }

    static final int PRIMARY = 0, SECONDARY = 1, GHOST = 2, DANGER_BTN = 3;

    /** Pulsante del kit, alto 44 (tocco comodo), raggio 4. */
    static TextView button(Context c, String label, int kind) {
        int fg = kind == PRIMARY ? BG : kind == DANGER_BTN ? DANGER : TEXT;
        TextView b = text(c, label, 15, fg, kind == PRIMARY);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(c, 48));
        b.setPadding(dp(c, 16), 0, dp(c, 16), 0);
        Drawable bg = kind == PRIMARY ? box(c, ACCENT, 0, 4)
                : kind == SECONDARY ? box(c, RAISED, CONTROL, 4)
                : kind == DANGER_BTN ? box(c, DANGER_BG, DANGER_BORDER, 4)
                : new ColorDrawable(0);
        b.setBackground(touch(c, bg, 4));
        return b;
    }

    /** Pulsante quadrato con sola icona. */
    static ImageView iconButton(Context c, int icon, String description, boolean filled) {
        ImageView i = new ImageView(c);
        i.setImageResource(icon);
        i.setImageTintList(ColorStateList.valueOf(filled ? BG : TEXT));
        i.setScaleType(ImageView.ScaleType.CENTER);
        i.setContentDescription(description);
        Drawable bg = filled ? box(c, ACCENT, 0, 6) : new ColorDrawable(0);
        i.setBackground(touch(c, bg, filled ? 6 : 4));
        return i;
    }

    static ImageView icon(Context c, int icon, int color) {
        ImageView i = new ImageView(c);
        i.setImageResource(icon);
        i.setImageTintList(ColorStateList.valueOf(color));
        return i;
    }

    /** Icona piccola prima del testo. */
    static void leading(TextView t, int icon, int color, int sizeDp) {
        Drawable d = t.getContext().getDrawable(icon).mutate();
        d.setTint(color);
        d.setBounds(0, 0, dp(t.getContext(), sizeDp), dp(t.getContext(), sizeDp));
        t.setCompoundDrawablesRelative(d, null, null, null);
        t.setCompoundDrawablePadding(dp(t.getContext(), 8));
        t.setGravity(Gravity.CENTER_VERTICAL);
    }

    /** Campo di testo del kit: fondo scuro, bordo, bordo colorato quando ha il fuoco. */
    static EditText field(Context c, String hint) {
        EditText e = new EditText(c);
        e.setSingleLine();
        e.setHint(hint);
        e.setTextSize(15);
        e.setTypeface(font(c, 400));
        e.setTextColor(TEXT);
        e.setHintTextColor(OFF);
        e.setMinHeight(dp(c, 48));
        e.setPadding(dp(c, 12), 0, dp(c, 12), 0);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, box(c, BG, ACCENT, 4));
        s.addState(new int[]{}, box(c, BG, CONTROL, 4));
        e.setBackground(s);
        GradientDrawable cursor = rounded(ACCENT, 0);
        cursor.setSize(dp(c, 2), 0);
        e.setTextCursorDrawable(cursor);
        return e;
    }

    /** Riga che apre una scelta: etichetta sopra, valore in un riquadro con freccia. */
    static TextView select(Context c, String value) {
        TextView t = text(c, value, 15, TEXT, false);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setMinHeight(dp(c, 48));
        t.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setMaxLines(2);
        t.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_chevron, 0);
        t.setCompoundDrawableTintList(ColorStateList.valueOf(OFF));
        t.setCompoundDrawablePadding(dp(c, 8));
        t.setBackground(touch(c, box(c, BG, CONTROL, 4), 4));
        return t;
    }

    /** Avviso del kit: fondo tenue del colore principale, icona e testo. */
    static LinearLayout warning(Context c, String title, String body) {
        LinearLayout w = new LinearLayout(c);
        w.setBackground(touch(c, box(c, ACCENT_DEEP, ACCENT_BORDER, 10), 10));
        w.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));
        ImageView i = icon(c, R.drawable.ic_alert, ACCENT_TEXT);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(c, 20), dp(c, 20));
        ilp.rightMargin = dp(c, 12);
        ilp.topMargin = dp(c, 1);
        w.addView(i, ilp);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(text(c, title, 14, TEXT, true));
        if (body != null) col.addView(text(c, body, 13, OFF, false));
        w.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return w;
    }

    /** Etichetta a pillola (es. stato "Concesso"). */
    static TextView chip(Context c, String s, int bg, int fg) {
        TextView t = text(c, s, 12, fg, true);
        t.setBackground(rounded(bg, dp(c, 999)));
        t.setPadding(dp(c, 10), dp(c, 4), dp(c, 10), dp(c, 4));
        return t;
    }

    /** Intestazione della pagina: indietro (facoltativo), titolo grande, pulsanti a destra. */
    static LinearLayout header(final Activity a, String title, boolean back) {
        LinearLayout bar = new LinearLayout(a);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setMinimumHeight(dp(a, 56));
        if (back) {
            ImageView b = iconButton(a, R.drawable.ic_back, "Indietro", false);
            b.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { a.onBackPressed(); }});
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(a, 44), dp(a, 44));
            lp.rightMargin = dp(a, 4);
            lp.leftMargin = -dp(a, 10);
            bar.addView(b, lp);
        }
        TextView t = text(a, title, 26, TEXT, true);
        t.setLetterSpacing(-0.02f);
        bar.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return bar;
    }

    static void tint(Switch s) {
        int[][] states = {{android.R.attr.state_checked}, {}};
        s.setThumbTintList(new ColorStateList(states, new int[]{BG, OFF}));
        s.setTrackTintList(new ColorStateList(states, new int[]{ACCENT, CONTROL}));
        s.setTrackTintMode(android.graphics.PorterDuff.Mode.SRC);
    }

    static void tint(SeekBar s) {
        s.setProgressTintList(ColorStateList.valueOf(ACCENT));
        s.setProgressBackgroundTintList(ColorStateList.valueOf(BORDER));
        s.setThumbTintList(ColorStateList.valueOf(ACCENT));
    }

    /** Finestra di dialogo nello stile del kit: card scura con titolo e contenuto. */
    static Dialog dialog(Context c, String title, View body) {
        Dialog d = new Dialog(c);
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(box(c, PANEL, BORDER, 12));
        int p = dp(c, 20);
        box.setPadding(p, p, p, dp(c, 12));
        box.addView(text(c, title, 20, TEXT, true));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, 12);
        box.addView(body, lp);
        d.setContentView(box);
        d.getWindow().setBackgroundDrawable(new ColorDrawable(0));
        d.getWindow().setLayout(Math.min(dp(c, 400), c.getResources().getDisplayMetrics().widthPixels - dp(c, 32)),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        return d;
    }

    /** Conferma per azioni distruttive: Annulla e un pulsante rosso. */
    static void confirm(Context c, String title, String message, String action, final Runnable onConfirm) {
        LinearLayout body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        body.addView(text(c, message, 15, OFF, false));
        LinearLayout buttons = new LinearLayout(c);
        buttons.setGravity(Gravity.END);
        TextView cancel = button(c, "Annulla", GHOST);
        TextView ok = button(c, action, DANGER_BTN);
        buttons.addView(cancel);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(c, 8);
        buttons.addView(ok, lp);
        body.addView(buttons, matchWrap(c, 20));
        final Dialog d = dialog(c, title, body);
        cancel.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { d.dismiss(); }});
        ok.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            d.dismiss();
            onConfirm.run();
        }});
        d.show();
    }

    static LinearLayout.LayoutParams weight(Context c, int marginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.setMargins(dp(c, marginDp), 0, dp(c, marginDp), 0);
        return lp;
    }

    static LinearLayout.LayoutParams matchWrap(Context c, int topMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, topMarginDp);
        return lp;
    }

    /** "sab, 31 ott" */
    static String date(long millis) {
        return new SimpleDateFormat("EEE, d MMM", Locale.ITALY).format(new Date(millis));
    }

    static String time(long millis) {
        return new SimpleDateFormat("HH:mm", Locale.ITALY).format(new Date(millis));
    }

    /** "Sveglia tra 7 ore e 15 minuti" */
    static String until(long millis) {
        long min = Math.max(1, (millis - System.currentTimeMillis() + 59_999) / 60_000);
        long d = min / 1440, h = min % 1440 / 60, m = min % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append(d == 1 ? " giorno" : " giorni");
        if (h > 0) sb.append(sb.length() > 0 ? (m > 0 ? ", " : " e ") : "").append(h).append(h == 1 ? " ora" : " ore");
        if (m > 0) sb.append(sb.length() > 0 ? " e " : "").append(m).append(m == 1 ? " minuto" : " minuti");
        return "Sveglia tra " + sb;
    }
}
