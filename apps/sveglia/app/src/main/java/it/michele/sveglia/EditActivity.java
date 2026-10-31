package it.michele.sveglia;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import java.util.List;

/** Nuova sveglia o modifica: orario a rotella, giorni, nome e app musicale. */
public class EditActivity extends Activity {
    private Store store;
    private Alarm alarm;
    private TimePicker picker;
    private EditText label;
    private static final int REQ_BROWSE = 1;
    private TextView sound;
    private TextView whatLabel;
    private TextView what;
    private final TextView[] dayViews = new TextView[7];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new Store(this);
        int id = getIntent().getIntExtra(AlarmReceiver.EXTRA_ID, 0);
        alarm = id == 0 ? null : store.get(id);
        if (alarm == null) alarm = new Alarm();
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);

        ScrollView scroll = new ScrollView(this);
        scroll.setFitsSystemWindows(true);
        scroll.setBackgroundColor(Ui.BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        root.setPadding(pad, pad, pad, pad * 2);
        scroll.addView(root);
        setContentView(scroll);

        picker = new TimePicker(this, null, 0, R.style.WheelTimePicker);
        picker.setIs24HourView(true);
        picker.setHour(alarm.hour);
        picker.setMinute(alarm.minute);
        root.addView(picker, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.rounded(Ui.CARD, Ui.dp(this, 32)));
        card.setPadding(Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20));
        root.addView(card, matchWrap(Ui.dp(this, 16)));

        card.addView(Ui.text(this, "Ripeti", 15, Ui.OFF, false));
        LinearLayout days = new LinearLayout(this);
        days.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        for (int i = 0; i < 7; i++) {
            final int day = i;
            TextView d = Ui.text(this, Alarm.DAY_LETTERS[i], 17, Ui.TEXT, true);
            d.setGravity(Gravity.CENTER);
            d.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
                alarm.days ^= 1 << day;
                refreshDays();
            }});
            dayViews[i] = d;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40), 0);
            lp.rightMargin = Ui.dp(this, 4);
            days.addView(d, lp);
        }
        card.addView(days);
        refreshDays();

        label = new EditText(this);
        label.setSingleLine();
        label.setHint("Nome sveglia");
        label.setText(alarm.label);
        label.setTextColor(Ui.TEXT);
        label.setHintTextColor(Ui.OFF);
        card.addView(label, matchWrap(Ui.dp(this, 8)));

        card.addView(Ui.text(this, "Suono sveglia", 15, Ui.OFF, false), matchWrap(Ui.dp(this, 16)));
        sound = Ui.text(this, Music.label(this, alarm.app), 19, Ui.TEXT, false);
        sound.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        sound.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { chooseSound(); }});
        card.addView(sound);
        whatLabel = Ui.text(this, "Cosa riprodurre", 15, Ui.OFF, false);
        card.addView(whatLabel, matchWrap(Ui.dp(this, 8)));
        what = Ui.text(this, "", 19, Ui.TEXT, false);
        what.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        what.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            startActivityForResult(new Intent(EditActivity.this, BrowseActivity.class)
                    .putExtra(BrowseActivity.EXTRA_PKG, alarm.app), REQ_BROWSE);
        }});
        card.addView(what);
        refreshSound();
        card.addView(Ui.text(this, "Con un'app musicale la sveglia apre l'app e avvia quello che hai scelto. "
                + "Se dopo 20 secondi non suona niente parte la suoneria di sistema.", 13, Ui.OFF, false), matchWrap(Ui.dp(this, 8)));

        LinearLayout buttons = new LinearLayout(this);
        TextView cancel = Ui.pill(this, "Annulla", Ui.PILL, Ui.TEXT);
        cancel.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { finish(); }});
        TextView save = Ui.pill(this, "Salva", Ui.ACCENT, Ui.TEXT);
        save.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            save();
            toast(Ui.until(alarm.next(System.currentTimeMillis())));
            finish();
        }});
        buttons.addView(cancel, Ui.weight(this, 4));
        buttons.addView(save, Ui.weight(this, 4));
        root.addView(buttons, matchWrap(Ui.dp(this, 24)));

        TextView test = Ui.pill(this, "Salva e prova tra 10 secondi", Ui.PILL, Ui.TEXT);
        test.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            save();
            Scheduler.test(EditActivity.this, alarm.id, System.currentTimeMillis() + 10_000);
            toast("Suona tra 10 secondi: puoi bloccare il telefono");
            finish();
        }});
        root.addView(test, matchWrap(Ui.dp(this, 12)));
    }

    private void save() {
        alarm.hour = picker.getHour();
        alarm.minute = picker.getMinute();
        alarm.label = label.getText().toString().trim();
        alarm.enabled = true;
        alarm.snoozeAt = 0;
        store.save(alarm);
        Scheduler.scheduleAll(this);
    }

    private void refreshDays() {
        for (int i = 0; i < 7; i++) {
            boolean on = alarm.hasDay(i);
            dayViews[i].setTextColor(on ? Ui.DAY_ON : Ui.TEXT);
            dayViews[i].setBackground(on ? Ui.rounded(0xFF3A1410, Ui.dp(this, 20)) : null);
        }
    }

    private void chooseSound() {
        final List<Music.App> apps = Music.musicApps(this);
        String[] names = new String[apps.size() + 1];
        names[0] = "Suoneria di sistema";
        for (int i = 0; i < apps.size(); i++) names[i + 1] = apps.get(i).label;
        new AlertDialog.Builder(this)
                .setTitle("Suono sveglia")
                .setItems(names, new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface d, int which) {
                    String app = which == 0 ? "" : apps.get(which - 1).pkg;
                    if (!app.equals(alarm.app)) {
                        alarm.app = app;
                        alarm.media = alarm.mediaTitle = alarm.query = "";
                    }
                    refreshSound();
                }})
                .show();
    }

    private void refreshSound() {
        sound.setText(Music.label(this, alarm.app));
        int v = alarm.app.isEmpty() ? View.GONE : View.VISIBLE;
        whatLabel.setVisibility(v);
        what.setVisibility(v);
        what.setText(alarm.what());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_BROWSE || resultCode != RESULT_OK || data == null) return;
        alarm.media = nonNull(data.getStringExtra(BrowseActivity.EXTRA_MEDIA));
        alarm.mediaTitle = nonNull(data.getStringExtra(BrowseActivity.EXTRA_TITLE));
        alarm.query = nonNull(data.getStringExtra(BrowseActivity.EXTRA_QUERY));
        refreshSound();
    }

    private static String nonNull(String s) { return s == null ? "" : s; }

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
