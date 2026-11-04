package it.michele.sveglia;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

/** Impostazioni in una pagina nello stile del resto dell'app. Ogni modifica è salvata subito. */
public class SettingsActivity extends Activity {
    private Store store;
    private LinearLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new Store(this);
        Ui.apply(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setFitsSystemWindows(true);
        scroll.setBackgroundColor(Ui.BG);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        root.setPadding(pad, Ui.dp(this, 12), pad, pad * 2);
        scroll.addView(root);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        build(); // l'accesso alle notifiche può essere cambiato nelle impostazioni di sistema
    }

    private void build() {
        root.removeAllViews();
        root.addView(Ui.header(this, "Impostazioni", true));

        root.addView(Ui.section(this, "Sveglia"), Ui.matchWrap(this, 16));
        LinearLayout ring = Ui.card(this);
        ring.addView(snoozeRow());
        ring.addView(divider(), dividerParams());
        ring.addView(slider("Volume app musicale", 100, 1, store.musicVolume(), new Format() {
            @Override public String value(int v) { return v == 0 ? "attuale" : v + "%"; }
            @Override public void save(int v) { store.setMusicVolume(v); }
        }));
        ring.addView(Ui.text(this, "0 = lascia il volume che c'è.", 13, Ui.OFF, false), Ui.matchWrap(this, 2));
        ring.addView(divider(), dividerParams());
        ring.addView(slider("Dissolvenza in ingresso", Store.MAX_FADE / 5, 5, store.fadeSeconds(), new Format() {
            @Override public String value(int v) { return v == 0 ? "no" : v + " s"; }
            @Override public void save(int v) { store.setFadeSeconds(v); }
        }));
        ring.addView(Ui.text(this, "Musica e suoneria partono piano e salgono fino al volume scelto.", 13, Ui.OFF, false),
                Ui.matchWrap(this, 2));
        root.addView(ring, Ui.matchWrap(this, 8));

        root.addView(Ui.section(this, "Colore principale"), Ui.matchWrap(this, 24));
        root.addView(themes(), Ui.matchWrap(this, 8));

        root.addView(Ui.section(this, "App musicali"), Ui.matchWrap(this, 24));
        root.addView(accessCard(), Ui.matchWrap(this, 8));

        root.addView(Ui.section(this, "Registro"), Ui.matchWrap(this, 24));
        root.addView(logCard(), Ui.matchWrap(this, 8));

        String version = "";
        try {
            version = " " + getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }
        TextView v = Ui.text(this, "Sveglia" + version, 12, Ui.OFF, false);
        v.setGravity(Gravity.CENTER);
        root.addView(v, Ui.matchWrap(this, 24));
    }

    /** Posticipa: − 10 min + */
    private View snoozeRow() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(Ui.text(this, "Posticipa di", 15, Ui.TEXT, false), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final TextView value = Ui.text(this, store.snoozeMinutes() + " min", 15, Ui.ACCENT_TEXT, true);
        value.setGravity(Gravity.CENTER);
        View minus = stepper(R.drawable.ic_minus, "Meno minuti", -1, value);
        View plus = stepper(R.drawable.ic_add, "Più minuti", 1, value);
        row.addView(minus, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        row.addView(value, new LinearLayout.LayoutParams(Ui.dp(this, 76), ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(plus, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        return row;
    }

    private View stepper(int icon, String description, final int delta, final TextView value) {
        ImageView b = Ui.iconButton(this, icon, description, false);
        b.setBackground(Ui.touch(this, Ui.box(this, Ui.RAISED, Ui.CONTROL, 4), 4));
        b.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            int m = Math.max(1, Math.min(store.snoozeMinutes() + delta, 30));
            store.setSnoozeMinutes(m);
            value.setText(m + " min");
        }});
        return b;
    }

    private interface Format {
        String value(int v);
        void save(int v);
    }

    /** Etichetta con valore a destra e cursore sotto; salva quando si stacca il dito. */
    private View slider(String label, int steps, final int unit, int current, final Format f) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = new LinearLayout(this);
        row.addView(Ui.text(this, label, 15, Ui.TEXT, false), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final TextView value = Ui.text(this, f.value(current), 15, Ui.ACCENT_TEXT, true);
        row.addView(value);
        box.addView(row);
        SeekBar s = new SeekBar(this);
        s.setMax(steps);
        s.setProgress(current / unit);
        s.setContentDescription(label);
        Ui.tint(s);
        s.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 4));
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b, int p, boolean user) { value.setText(f.value(p * unit)); }
            @Override public void onStartTrackingTouch(SeekBar b) {}
            @Override public void onStopTrackingTouch(SeekBar b) { f.save(b.getProgress() * unit); }
        });
        box.addView(s, Ui.matchWrap(this, 4));
        return box;
    }

    /** I sei colori del kit; quello scelto ha il bordo e il fondo tenue del suo colore. */
    private View themes() {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        int current = store.theme();
        for (int i = 0; i < Ui.THEME_NAMES.length; i++) {
            final int theme = i;
            int main = Ui.themeColor(i);
            boolean on = i == current;
            TextView t = Ui.text(this, Ui.THEME_NAMES[i], 14, Ui.TEXT, on);
            t.setMinHeight(Ui.dp(this, 48));
            t.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 8), 0);
            android.graphics.drawable.GradientDrawable dot = Ui.rounded(main, Ui.dp(this, 4));
            dot.setSize(Ui.dp(this, 20), Ui.dp(this, 20));
            t.setCompoundDrawablesRelativeWithIntrinsicBounds(dot, null, null, null);
            t.setCompoundDrawablePadding(Ui.dp(this, 10));
            t.setGravity(Gravity.CENTER_VERTICAL);
            int bg = on ? (main & 0x00FFFFFF) | 0x1F000000 : Ui.PANEL;
            t.setBackground(Ui.touch(this, Ui.box(this, bg, on ? main : Ui.BORDER, 4), 4));
            t.setContentDescription("Colore " + Ui.THEME_NAMES[i] + (on ? ", scelto" : ""));
            t.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
                if (theme == store.theme()) return;
                store.setTheme(theme);
                recreate();
            }});
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(GridLayout.spec(i / 3), GridLayout.spec(i % 3, 1f));
            lp.width = 0;
            lp.setMargins(i % 3 == 0 ? 0 : Ui.dp(this, 4), i < 3 ? 0 : Ui.dp(this, 8), i % 3 == 2 ? 0 : Ui.dp(this, 4), 0);
            grid.addView(t, lp);
        }
        return grid;
    }

    private View accessCard() {
        LinearLayout card = Ui.card(this);
        boolean access = Music.sessionAccess(this);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(Ui.text(this, "Accesso alle notifiche", 15, Ui.TEXT, false), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(access ? Ui.chip(this, "Concesso", Ui.OK_BG, Ui.OK) : Ui.chip(this, "Non concesso", Ui.ACCENT_DEEP, Ui.ACCENT_SOFT));
        card.addView(row);
        card.addView(Ui.text(this, "Serve a comandare Qobuz e le app che non aprono il catalogo. Le notifiche non vengono lette.",
                13, Ui.OFF, false), Ui.matchWrap(this, 6));
        TextView open = Ui.button(this, access ? "Apri impostazioni" : "Concedi l'accesso", access ? Ui.SECONDARY : Ui.PRIMARY);
        open.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        }});
        card.addView(open, Ui.matchWrap(this, 12));
        return card;
    }

    private View logCard() {
        LinearLayout card = Ui.card(this);
        String text = store.log();
        TextView log = Ui.text(this, text.isEmpty() ? "Nessun evento" : text, 11, Ui.OFF, false);
        log.setTypeface(Ui.font(this, 400), Typeface.NORMAL);
        log.setTextIsSelectable(true);
        log.setBackground(Ui.box(this, Ui.BG, Ui.BORDER, 8));
        log.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        card.addView(log);
        TextView clear = Ui.button(this, "Svuota registro", Ui.SECONDARY);
        clear.setEnabled(!text.isEmpty());
        if (text.isEmpty()) clear.setTextColor(0xFF77726A);
        clear.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            store.clearLog();
            build();
        }});
        card.addView(clear, Ui.matchWrap(this, 12));
        return card;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(Ui.BORDER);
        return v;
    }

    private LinearLayout.LayoutParams dividerParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 1));
        lp.setMargins(0, Ui.dp(this, 14), 0, Ui.dp(this, 14));
        return lp;
    }
}
