package it.michele.sveglia;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Elenco delle sveglie. Interfaccia costruita da codice. */
public class MainActivity extends Activity {
    private Store store;
    private LinearLayout root;
    private int theme;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            build();
            handler.postDelayed(this, 30_000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new Store(this);
        Ui.apply(this);
        theme = store.theme();
        FrameLayout frame = new FrameLayout(this);
        frame.setFitsSystemWindows(true);
        frame.setBackgroundColor(Ui.BG);
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        root.setPadding(pad, Ui.dp(this, 12), pad, Ui.dp(this, 104));
        scroll.addView(root);
        frame.addView(scroll);

        ImageView add = Ui.iconButton(this, R.drawable.ic_add, "Aggiungi sveglia", true);
        add.setElevation(Ui.dp(this, 6));
        add.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            startActivity(new Intent(MainActivity.this, EditActivity.class));
        }});
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 56), Gravity.BOTTOM | Gravity.END);
        lp.setMargins(pad, pad, pad, Ui.dp(this, 24));
        frame.addView(add, lp);
        setContentView(frame);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (store.theme() != theme) {
            recreate(); // colore principale cambiato nelle impostazioni
            return;
        }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        Scheduler.scheduleAll(this);
        tick.run();
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(tick);
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        build();
    }

    private void build() {
        root.removeAllViews();
        LinearLayout header = Ui.header(this, "Sveglie", false);
        ImageView settings = Ui.iconButton(this, R.drawable.ic_settings, "Impostazioni", false);
        settings.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            startActivity(new Intent(MainActivity.this, SettingsActivity.class));
        }});
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44));
        slp.rightMargin = -Ui.dp(this, 10);
        header.addView(settings, slp);
        root.addView(header);

        long next = Scheduler.nextAny(this);
        root.addView(Ui.text(this, next == 0 ? "Tutte le sveglie sono disattivate" : Ui.until(next), 17, Ui.TEXT, false),
                Ui.matchWrap(this, 8));
        if (next != 0) root.addView(Ui.text(this, Ui.date(next) + " · " + Ui.time(next), 13, Ui.OFF, false), Ui.matchWrap(this, 2));

        buildWarnings();

        List<Alarm> alarms = store.alarms();
        if (alarms.isEmpty()) {
            LinearLayout empty = Ui.card(this);
            empty.setGravity(Gravity.CENTER_HORIZONTAL);
            empty.setPadding(Ui.dp(this, 16), Ui.dp(this, 32), Ui.dp(this, 16), Ui.dp(this, 32));
            empty.addView(Ui.text(this, "Nessuna sveglia", 15, Ui.TEXT, true));
            empty.addView(Ui.text(this, "Tocca + per aggiungerne una.", 13, Ui.OFF, false), Ui.matchWrap(this, 4));
            for (int i = 0; i < empty.getChildCount(); i++) ((TextView) empty.getChildAt(i)).setGravity(Gravity.CENTER);
            root.addView(empty, Ui.matchWrap(this, 24));
            return;
        }
        root.addView(Ui.section(this, alarms.size() == 1 ? "1 sveglia" : alarms.size() + " sveglie"), Ui.matchWrap(this, 24));
        for (Alarm a : alarms) root.addView(row(a), Ui.matchWrap(this, 8));
    }

    private View row(final Alarm a) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Ui.touch(this, Ui.box(this, Ui.PANEL, Ui.BORDER, 12), 12));
        row.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14));
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        int color = a.enabled ? Ui.TEXT : Ui.OFF;
        if (!a.label.isEmpty()) left.addView(Ui.text(this, a.label, 13, Ui.OFF, false));
        TextView time = Ui.text(this, a.time(), 44, color, true);
        time.setLetterSpacing(-0.02f);
        time.setIncludeFontPadding(false);
        left.addView(time, Ui.matchWrap(this, 2));
        left.addView(days(a), Ui.matchWrap(this, 6));
        if (!a.app.isEmpty()) {
            TextView music = Ui.text(this, Music.label(this, a.app) + " · " + a.what(), 13, Ui.OFF, false);
            music.setSingleLine();
            music.setEllipsize(android.text.TextUtils.TruncateAt.END);
            Ui.leading(music, R.drawable.ic_music, Ui.OFF, 16);
            left.addView(music, Ui.matchWrap(this, 6));
        }
        row.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Switch on = new Switch(this);
        on.setChecked(a.enabled);
        on.setScaleX(1.2f);
        on.setScaleY(1.2f);
        on.setContentDescription("Attiva sveglia delle " + a.time());
        Ui.tint(on);
        on.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { @Override public void onCheckedChanged(CompoundButton b, boolean checked) {
            a.enabled = checked;
            a.snoozeAt = 0;
            store.save(a);
            Scheduler.scheduleAll(MainActivity.this);
            if (checked) toast(Ui.until(a.next(System.currentTimeMillis())));
            build();
        }});
        row.addView(on);

        row.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            startActivity(new Intent(MainActivity.this, EditActivity.class).putExtra(AlarmReceiver.EXTRA_ID, a.id));
        }});
        row.setOnLongClickListener(new View.OnLongClickListener() { @Override public boolean onLongClick(View v) {
            confirmDelete(a);
            return true;
        }});
        return row;
    }

    private void confirmDelete(final Alarm a) {
        Ui.confirm(this, "Eliminare la sveglia?", "La sveglia delle " + a.time() + " verrà eliminata.", "Elimina",
                new Runnable() { @Override public void run() {
                    Scheduler.cancel(MainActivity.this, a.id);
                    store.delete(a.id);
                    build();
                }});
    }

    /** "L M M G V S D" con i giorni attivi nel colore principale, oppure la data per le sveglie singole. */
    private View days(Alarm a) {
        if (!a.repeats()) {
            return Ui.text(this, Ui.date(a.next(System.currentTimeMillis())), 13, Ui.OFF, false);
        }
        LinearLayout row = new LinearLayout(this);
        for (int i = 0; i < 7; i++) {
            boolean on = a.hasDay(i);
            int color = on ? (a.enabled ? Ui.ACCENT_TEXT : Ui.TEXT) : 0xFF77726A;
            TextView d = Ui.text(this, Alarm.DAY_LETTERS[i], 13, color, on);
            d.setPadding(0, 0, Ui.dp(this, 10), 0);
            row.addView(d);
        }
        return row;
    }

    /** Avvisi solo se manca qualcosa che impedirebbe alla sveglia di suonare. */
    private void buildWarnings() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (!nm.areNotificationsEnabled()) {
            warning("Notifiche disattivate", "La sveglia non può mostrarsi.", Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        } else if (Build.VERSION.SDK_INT >= 34 && !nm.canUseFullScreenIntent()) {
            warning("Notifiche a schermo intero", "Consentile, altrimenti la sveglia non accende lo schermo.",
                    Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT);
        }
        if (!getSystemService(AlarmManager.class).canScheduleExactAlarms()) {
            warning("Sveglie e promemoria", "Consenti il permesso, altrimenti le sveglie non suonano all'ora esatta.",
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
        }
    }

    private void warning(String title, String msg, final String settingsAction) {
        LinearLayout w = Ui.warning(this, title, msg + " Tocca per aprire le impostazioni.");
        w.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            Intent i = new Intent(settingsAction, Uri.parse("package:" + getPackageName()))
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            try {
                startActivity(i);
            } catch (RuntimeException e) {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
            }
        }});
        root.addView(w, Ui.matchWrap(this, 16));
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
