package it.michele.sveglia;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Schermata della sveglia che suona, visibile anche sopra la schermata di blocco. */
public class RingActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setShowWhenLocked(true);
        setTurnScreenOn(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        Alarm a = RingService.current;
        if (a == null) {
            finish();
            return;
        }
        RingService.screen = this;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Ui.BG);
        root.setFitsSystemWindows(true);
        int pad = Ui.dp(this, 24);
        root.setPadding(pad, Ui.dp(this, 96), pad, pad * 2);

        String now = new SimpleDateFormat("HH:mm", Locale.ITALY).format(new Date());
        root.addView(Ui.text(this, now, 88, Ui.TEXT, true));
        root.addView(Ui.text(this, Ui.date(System.currentTimeMillis()), 20, Ui.OFF, false));
        if (!a.label.isEmpty()) root.addView(Ui.text(this, a.label, 24, Ui.TEXT, false), top(Ui.dp(this, 32)));
        if (!a.app.isEmpty()) root.addView(Ui.text(this, "♪ " + Music.label(this, a.app) + " · " + a.what(), 17, Ui.OFF, false), top(Ui.dp(this, 12)));

        View spacer = new View(this);
        root.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1));

        int snooze = new Store(this).snoozeMinutes();
        TextView snoozeBtn = Ui.pill(this, "Posticipa " + snooze + " min", Ui.PILL, Ui.TEXT);
        snoozeBtn.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { send(RingService.ACTION_SNOOZE); }});
        root.addView(snoozeBtn, wide());
        TextView stop = Ui.pill(this, "Ferma", Ui.ACCENT, Ui.TEXT);
        stop.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { send(RingService.ACTION_STOP); }});
        LinearLayout.LayoutParams lp = wide();
        lp.topMargin = Ui.dp(this, 16);
        root.addView(stop, lp);
        setContentView(root);

        if (!RingService.musicLaunched && Music.installed(this, a.app)) launchMusic(a);
    }

    /** Apre l'app musicale (consentito solo mentre questa schermata è in primo piano), poi torna qui. */
    private void launchMusic(Alarm a) {
        try {
            startActivity(Music.launchIntent(this, a));
        } catch (RuntimeException e) {
            return; // niente musica: dopo l'attesa il servizio suona la suoneria di riserva
        }
        new Store(this).log(Music.launchPlays(this, a) ? "app aperta con la richiesta" : "app aperta");
        RingService.musicLaunched = true;
        RingService.musicStarted(this);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (RingService.current != null) {
                    startActivity(new Intent(RingActivity.this, RingActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
                }
            }
        }, 3_000);
    }

    private void send(String action) {
        startService(new Intent(this, RingService.class).setAction(action));
        finish();
    }

    @Override
    public void onBackPressed() {
        // il tasto Indietro non ferma la sveglia: servono Posticipa o Ferma
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (RingService.screen == this) RingService.screen = null;
        super.onDestroy();
    }

    private LinearLayout.LayoutParams top(int margin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = margin;
        return lp;
    }

    private LinearLayout.LayoutParams wide() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 64));
    }
}
