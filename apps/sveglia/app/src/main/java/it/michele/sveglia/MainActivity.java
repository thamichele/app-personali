package it.michele.sveglia;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.DialogInterface;
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
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Elenco delle sveglie. Interfaccia costruita da codice. */
public class MainActivity extends Activity {
    private Store store;
    private LinearLayout root;
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
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        ScrollView scroll = new ScrollView(this);
        scroll.setFitsSystemWindows(true);
        scroll.setBackgroundColor(Ui.BG);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        root.setPadding(pad, pad, pad, pad * 2);
        scroll.addView(root);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
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
        buildTopBar();

        long next = Scheduler.nextAny(this);
        TextView head = Ui.text(this, next == 0 ? "Tutte le sveglie\nsono disattivate" : Ui.until(next), 26, Ui.TEXT, false);
        head.setGravity(Gravity.CENTER);
        root.addView(head, matchWrap(Ui.dp(this, 48)));
        TextView sub = Ui.text(this, next == 0 ? "" : Ui.date(next) + ", " + time(next), 15, Ui.OFF, false);
        sub.setGravity(Gravity.CENTER);
        root.addView(sub, matchWrap(Ui.dp(this, 6)));

        buildWarnings();

        List<Alarm> alarms = store.alarms();
        if (alarms.isEmpty()) {
            TextView empty = Ui.text(this, "Nessuna sveglia. Tocca + per aggiungerne una.", 15, Ui.OFF, false);
            empty.setGravity(Gravity.CENTER);
            root.addView(empty, matchWrap(Ui.dp(this, 64)));
            return;
        }
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.rounded(Ui.CARD, Ui.dp(this, 32)));
        card.setPadding(Ui.dp(this, 24), Ui.dp(this, 12), Ui.dp(this, 20), Ui.dp(this, 12));
        for (Alarm a : alarms) card.addView(row(a));
        root.addView(card, matchWrap(Ui.dp(this, 40)));
    }

    /** Pillola in alto a destra con "+" e menu, come nell'orologio Samsung. */
    private void buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.END);
        LinearLayout pill = new LinearLayout(this);
        pill.setBackground(Ui.rounded(Ui.PILL, Ui.dp(this, 28)));
        pill.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
        TextView add = Ui.text(this, "+", 30, Ui.TEXT, false);
        add.setPadding(Ui.dp(this, 14), Ui.dp(this, 2), Ui.dp(this, 14), Ui.dp(this, 4));
        add.setContentDescription("Aggiungi sveglia");
        add.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            startActivity(new Intent(MainActivity.this, EditActivity.class));
        }});
        TextView menu = Ui.text(this, "⋮", 26, Ui.TEXT, true);
        menu.setPadding(Ui.dp(this, 14), Ui.dp(this, 4), Ui.dp(this, 14), Ui.dp(this, 4));
        menu.setContentDescription("Impostazioni");
        menu.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { settings(); }});
        pill.addView(add);
        pill.addView(menu);
        bar.addView(pill);
        root.addView(bar);
    }

    private View row(final Alarm a) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 14));
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        int color = a.enabled ? Ui.TEXT : Ui.OFF;
        if (!a.label.isEmpty()) left.addView(Ui.text(this, a.label, 16, color, false));
        left.addView(Ui.text(this, a.time(), 52, color, true));
        left.addView(days(a));
        if (!a.app.isEmpty()) left.addView(Ui.text(this, "♪ " + Music.label(this, a.app), 14, Ui.OFF, false));
        row.addView(left, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Switch on = new Switch(this);
        on.setChecked(a.enabled);
        on.setScaleX(1.3f);
        on.setScaleY(1.3f);
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
            new AlertDialog.Builder(MainActivity.this)
                    .setMessage("Eliminare la sveglia delle " + a.time() + "?")
                    .setPositiveButton("Elimina", new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface d, int w) {
                        Scheduler.cancel(MainActivity.this, a.id);
                        store.delete(a.id);
                        build();
                    }})
                    .setNegativeButton("Annulla", null)
                    .show();
            return true;
        }});
        return row;
    }

    /** "L M M G V S D" con i giorni attivi in rosso, oppure la data per le sveglie singole. */
    private View days(Alarm a) {
        if (!a.repeats()) {
            return Ui.text(this, Ui.date(a.next(System.currentTimeMillis())), 16, Ui.OFF, true);
        }
        LinearLayout row = new LinearLayout(this);
        for (int i = 0; i < 7; i++) {
            int color = a.hasDay(i) ? (a.enabled ? Ui.DAY_ON : Ui.OFF) : (a.enabled ? Ui.TEXT : Ui.OFF);
            TextView d = Ui.text(this, Alarm.DAY_LETTERS[i], 16, color, a.hasDay(i));
            d.setPadding(0, 0, Ui.dp(this, 8), 0);
            row.addView(d);
        }
        return row;
    }

    /** Avvisi solo se manca qualcosa che impedirebbe alla sveglia di suonare. */
    private void buildWarnings() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (!nm.areNotificationsEnabled()) {
            warning("Notifiche disattivate: la sveglia non può mostrarsi.", Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        } else if (Build.VERSION.SDK_INT >= 34 && !nm.canUseFullScreenIntent()) {
            warning("Consenti le notifiche a schermo intero, altrimenti la sveglia non accende lo schermo.",
                    Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT);
        }
        if (!getSystemService(AlarmManager.class).canScheduleExactAlarms()) {
            warning("Consenti \"Sveglie e promemoria\".", Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
        }
    }

    private void warning(String msg, final String settingsAction) {
        TextView t = Ui.text(this, msg + " Tocca per aprire le impostazioni.", 15, Ui.TEXT, false);
        t.setBackground(Ui.rounded(0xFF5A1A10, Ui.dp(this, 20)));
        t.setPadding(Ui.dp(this, 20), Ui.dp(this, 14), Ui.dp(this, 20), Ui.dp(this, 14));
        t.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            Intent i = new Intent(settingsAction, Uri.parse("package:" + getPackageName()))
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            try {
                startActivity(i);
            } catch (RuntimeException e) {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
            }
        }});
        root.addView(t, matchWrap(Ui.dp(this, 24)));
    }

    private void settings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 24);
        box.setPadding(pad, pad / 2, pad, 0);

        box.addView(Ui.text(this, "Posticipa di (minuti)", 15, Ui.TEXT, false));
        final NumberPicker snooze = new NumberPicker(this);
        snooze.setMinValue(1);
        snooze.setMaxValue(30);
        snooze.setValue(store.snoozeMinutes());
        box.addView(snooze);

        final TextView volLabel = Ui.text(this, "", 15, Ui.TEXT, false);
        box.addView(volLabel, matchWrap(Ui.dp(this, 16)));
        final SeekBar volume = new SeekBar(this);
        volume.setMax(100);
        volume.setProgress(store.musicVolume());
        volLabel.setText(volumeText(volume.getProgress()));
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) { volLabel.setText(volumeText(p)); }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        box.addView(volume, matchWrap(Ui.dp(this, 8)));

        new AlertDialog.Builder(this)
                .setTitle("Impostazioni")
                .setView(box)
                .setPositiveButton("Salva", new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface d, int w) {
                    store.setSnoozeMinutes(snooze.getValue());
                    store.setMusicVolume(volume.getProgress());
                }})
                .setNegativeButton("Annulla", null)
                .show();
    }

    private static String volumeText(int p) {
        return p == 0 ? "Volume app musicale: lascia quello attuale" : "Volume app musicale: " + p + "%";
    }

    private static String time(long millis) {
        return new java.text.SimpleDateFormat("HH:mm", java.util.Locale.ITALY).format(new java.util.Date(millis));
    }

    private LinearLayout.LayoutParams matchWrap(int topMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = topMargin;
        return lp;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
