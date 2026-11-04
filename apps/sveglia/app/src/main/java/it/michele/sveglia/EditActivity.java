package it.michele.sveglia;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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
    private boolean isNew;
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
        Ui.apply(this);
        int id = getIntent().getIntExtra(AlarmReceiver.EXTRA_ID, 0);
        alarm = id == 0 ? null : store.get(id);
        isNew = alarm == null;
        if (isNew) alarm = new Alarm();

        ScrollView scroll = new ScrollView(this);
        scroll.setFitsSystemWindows(true);
        scroll.setBackgroundColor(Ui.BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        root.setPadding(pad, Ui.dp(this, 12), pad, pad * 2);
        scroll.addView(root);
        setContentView(scroll);

        root.addView(Ui.header(this, isNew ? "Nuova sveglia" : "Modifica sveglia", true));

        LinearLayout timeCard = Ui.card(this);
        picker = new TimePicker(this, null, 0, R.style.WheelTimePicker);
        picker.setIs24HourView(true);
        picker.setHour(alarm.hour);
        picker.setMinute(alarm.minute);
        timeCard.addView(picker, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(timeCard, Ui.matchWrap(this, 12));

        LinearLayout card = Ui.card(this);
        root.addView(card, Ui.matchWrap(this, 12));

        card.addView(Ui.section(this, "Ripeti"));
        LinearLayout days = new LinearLayout(this);
        for (int i = 0; i < 7; i++) {
            final int day = i;
            TextView d = Ui.text(this, Alarm.DAY_LETTERS[i], 15, Ui.TEXT, true);
            d.setGravity(Gravity.CENTER);
            d.setContentDescription(Alarm.DAY_LETTERS[i]);
            d.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
                alarm.days ^= 1 << day;
                refreshDays();
            }});
            dayViews[i] = d;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1);
            lp.setMargins(i == 0 ? 0 : Ui.dp(this, 3), 0, i == 6 ? 0 : Ui.dp(this, 3), 0);
            days.addView(d, lp);
        }
        card.addView(days, Ui.matchWrap(this, 10));
        refreshDays();

        card.addView(fieldLabel("Nome sveglia"), Ui.matchWrap(this, 20));
        label = Ui.field(this, "Es. Lavoro");
        label.setText(alarm.label);
        card.addView(label, Ui.matchWrap(this, 6));

        card.addView(fieldLabel("Suono sveglia"), Ui.matchWrap(this, 16));
        sound = Ui.select(this, "");
        sound.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { chooseSound(); }});
        card.addView(sound, Ui.matchWrap(this, 6));
        whatLabel = fieldLabel("Cosa riprodurre");
        card.addView(whatLabel, Ui.matchWrap(this, 16));
        what = Ui.select(this, "");
        what.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            startActivityForResult(new Intent(EditActivity.this, BrowseActivity.class)
                    .putExtra(BrowseActivity.EXTRA_PKG, alarm.app), REQ_BROWSE);
        }});
        card.addView(what, Ui.matchWrap(this, 6));
        refreshSound();
        card.addView(Ui.text(this, "Con un'app musicale la sveglia apre l'app e avvia quello che hai scelto. "
                + "Se dopo 20 secondi non suona niente parte la suoneria di sistema.", 13, Ui.OFF, false), Ui.matchWrap(this, 12));

        LinearLayout buttons = new LinearLayout(this);
        TextView cancel = Ui.button(this, "Annulla", Ui.SECONDARY);
        cancel.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { finish(); }});
        TextView save = Ui.button(this, "Salva", Ui.PRIMARY);
        save.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            save();
            toast(Ui.until(alarm.next(System.currentTimeMillis())));
            finish();
        }});
        buttons.addView(cancel, Ui.weight(this, 0));
        LinearLayout.LayoutParams slp = Ui.weight(this, 0);
        slp.leftMargin = Ui.dp(this, 8);
        buttons.addView(save, slp);
        root.addView(buttons, Ui.matchWrap(this, 20));

        TextView test = Ui.button(this, "Salva e prova tra 10 secondi", Ui.GHOST);
        test.setTextColor(Ui.ACCENT_TEXT);
        test.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            save();
            Scheduler.test(EditActivity.this, alarm.id, System.currentTimeMillis() + 10_000);
            toast("Suona tra 10 secondi: puoi bloccare il telefono");
            finish();
        }});
        root.addView(test, Ui.matchWrap(this, 8));

        if (!isNew) {
            TextView delete = Ui.button(this, "Elimina sveglia", Ui.DANGER_BTN);
            Ui.leading(delete, R.drawable.ic_trash, Ui.DANGER, 18);
            delete.setGravity(Gravity.CENTER);
            delete.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
                Ui.confirm(EditActivity.this, "Eliminare la sveglia?", "La sveglia delle " + alarm.time() + " verrà eliminata.",
                        "Elimina", new Runnable() { @Override public void run() {
                            Scheduler.cancel(EditActivity.this, alarm.id);
                            store.delete(alarm.id);
                            finish();
                        }});
            }});
            root.addView(delete, Ui.matchWrap(this, 24));
        }
    }

    private TextView fieldLabel(String s) {
        return Ui.text(this, s, 13, Ui.TEXT, true);
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

    /** Giorni scelti: fondo tenue, bordo e testo del colore principale. */
    private void refreshDays() {
        for (int i = 0; i < 7; i++) {
            boolean on = alarm.hasDay(i);
            dayViews[i].setTextColor(on ? Ui.ACCENT_TEXT : Ui.OFF);
            dayViews[i].setBackground(Ui.touch(this, on ? Ui.box(this, Ui.ACCENT_DEEP, Ui.ACCENT_BORDER, 4)
                    : Ui.box(this, Ui.RAISED, Ui.CONTROL, 4), 4));
            dayViews[i].setSelected(on);
        }
    }

    private void chooseSound() {
        final List<Music.App> apps = Music.musicApps(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        final Dialog d = Ui.dialog(this, "Suono sveglia", scroll);
        for (int i = 0; i <= apps.size(); i++) {
            final String app = i == 0 ? "" : apps.get(i - 1).pkg;
            boolean on = app.equals(alarm.app);
            TextView t = Ui.text(this, i == 0 ? "Suoneria di sistema" : apps.get(i - 1).label, 15, on ? Ui.ACCENT_TEXT : Ui.TEXT, on);
            t.setMinHeight(Ui.dp(this, 48));
            t.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
            Ui.leading(t, i == 0 ? R.drawable.ic_bell : R.drawable.ic_music, on ? Ui.ACCENT_TEXT : Ui.OFF, 18);
            t.setBackground(Ui.touch(this, on ? Ui.box(this, Ui.ACCENT_DEEP, 0, 4) : new android.graphics.drawable.ColorDrawable(0), 4));
            t.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
                if (!app.equals(alarm.app)) {
                    alarm.app = app;
                    alarm.media = alarm.mediaTitle = alarm.query = "";
                }
                refreshSound();
                d.dismiss();
            }});
            list.addView(t, Ui.matchWrap(this, i == 0 ? 0 : 2));
        }
        d.show();
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

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
