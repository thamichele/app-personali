package it.michele.sveglia;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Colori e costruttori di viste condivisi, in stile orologio Samsung (tema scuro). */
final class Ui {
    static final int BG = 0xFF000000;
    static final int CARD = 0xFF161616;
    static final int PILL = 0xFF2A2A2A;
    static final int TEXT = 0xFFFFFFFF;
    static final int OFF = 0xFF6E6E6E;
    static final int ACCENT = 0xFFFF4A1C;
    static final int DAY_ON = 0xFFF0261B;

    private Ui() {}

    static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static TextView text(Context c, String s, int sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        return t;
    }

    static GradientDrawable rounded(int color, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        return g;
    }

    /** Pulsante a pillola. */
    static TextView pill(Context c, String label, int bg, int fg) {
        TextView b = text(c, label, 17, fg, true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(rounded(bg, dp(c, 28)));
        b.setPadding(dp(c, 20), dp(c, 14), dp(c, 20), dp(c, 14));
        return b;
    }

    static void tint(Switch s) {
        int[][] states = {{android.R.attr.state_checked}, {}};
        s.setThumbTintList(new ColorStateList(states, new int[]{TEXT, 0xFF1A1A1A}));
        s.setTrackTintList(new ColorStateList(states, new int[]{ACCENT, 0xFF9A9A9A}));
    }

    static LinearLayout.LayoutParams weight(Context c, int marginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        lp.setMargins(dp(c, marginDp), 0, dp(c, marginDp), 0);
        return lp;
    }

    /** "sab, 31 ott" */
    static String date(long millis) {
        return new SimpleDateFormat("EEE, d MMM", Locale.ITALY).format(new Date(millis));
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
