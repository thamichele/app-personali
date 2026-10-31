package it.michele.sveglia;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.KeyEvent;

/**
 * Sveglia che suona. La notifica a schermo intero apre RingActivity, che avvia l'app musicale
 * (un'app in background non può aprire altre app). Se dopo qualche secondo non suona niente,
 * parte la suoneria di sistema: la sveglia suona comunque.
 */
public class RingService extends Service {
    static final String ACTION_RING = "it.michele.sveglia.RING";
    static final String ACTION_MUSIC = "it.michele.sveglia.MUSIC";
    static final String ACTION_SNOOZE = "it.michele.sveglia.SNOOZE";
    static final String ACTION_STOP = "it.michele.sveglia.STOP";
    private static final String CHANNEL = "ring";
    private static final int NOTIF_ID = 1;
    /** Attesa massima dell'app musicale prima della suoneria di riserva. */
    private static final long MUSIC_WAIT_MS = 20_000;
    private static final long PLAY_DELAY_MS = 4_000;
    /** Dopo questo tempo la sveglia smette da sola. */
    private static final long TIMEOUT_MS = 10 * 60_000;

    /** Stato letto da RingActivity (stesso processo, thread principale). */
    static Alarm current;
    static boolean musicLaunched;
    static RingActivity screen;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private PowerManager.WakeLock wakeLock;
    private int savedVolume = -1;

    private final Runnable fallback = new Runnable() {
        @Override
        public void run() {
            if (!getSystemService(AudioManager.class).isMusicActive()) playTone();
        }
    };
    /** Dopo l'apertura dell'app: avvia quello che è stato scelto, oppure l'ultima riproduzione. */
    private final Runnable play = new Runnable() {
        @Override
        public void run() {
            Alarm a = current;
            if (a == null) return;
            boolean chosen = !a.media.isEmpty() || !a.query.isEmpty();
            boolean silent = !getSystemService(AudioManager.class).isMusicActive();
            if (Music.launchPlays(RingService.this, a)) {
                if (!chosen && silent) Music.press(RingService.this, KeyEvent.KEYCODE_MEDIA_PLAY);
            } else if (chosen) {
                Music.playChoice(RingService.this, a);
            } else if (silent) {
                Music.press(RingService.this, KeyEvent.KEYCODE_MEDIA_PLAY);
            }
        }
    };
    private final Runnable timeout = new Runnable() {
        @Override
        public void run() { finish(false); }
    };

    static void ring(Context c, int id) {
        c.startForegroundService(new Intent(c, RingService.class).setAction(ACTION_RING).putExtra(AlarmReceiver.EXTRA_ID, id));
    }

    /** Chiamato da RingActivity dopo aver aperto l'app musicale. */
    static void musicStarted(Context c) {
        c.startService(new Intent(c, RingService.class).setAction(ACTION_MUSIC));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_RING.equals(action)) {
            Alarm a = new Store(this).get(intent.getIntExtra(AlarmReceiver.EXTRA_ID, 0));
            if (a == null && current == null) {
                stopSelf();
                return START_NOT_STICKY;
            }
            if (a != null) start(a);
        } else if (current == null) {
            stopSelf();
        } else if (ACTION_MUSIC.equals(action)) {
            musicLaunched = true;
            handler.removeCallbacks(fallback);
            handler.postDelayed(play, PLAY_DELAY_MS);
            handler.postDelayed(fallback, MUSIC_WAIT_MS);
        } else if (ACTION_SNOOZE.equals(action)) {
            finish(true);
        } else if (ACTION_STOP.equals(action)) {
            finish(false);
        }
        return START_NOT_STICKY;
    }

    private void start(Alarm a) {
        if (current != null) stopSound();
        current = a;
        musicLaunched = false;
        startForeground(NOTIF_ID, notification(a), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sveglia:ring");
            wakeLock.acquire(TIMEOUT_MS + 60_000);
        }
        if (Music.installed(this, a.app)) {
            setMusicVolume();
            // se RingActivity non si apre (telefono in uso: la notifica resta in alto) suona la riserva
            handler.postDelayed(fallback, MUSIC_WAIT_MS);
        } else {
            playTone();
        }
        handler.postDelayed(timeout, TIMEOUT_MS);
    }

    private void finish(boolean snooze) {
        Alarm a = current;
        stopSound();
        if (snooze && a != null) {
            Store store = new Store(this);
            Alarm saved = store.get(a.id);
            if (saved != null) {
                saved.snoozeAt = System.currentTimeMillis() + store.snoozeMinutes() * 60_000L;
                saved.enabled = true;
                store.save(saved);
                Scheduler.scheduleAll(this);
            }
        }
        current = null;
        if (screen != null) screen.finish();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void stopSound() {
        handler.removeCallbacksAndMessages(null);
        if (player != null) {
            player.release();
            player = null;
        }
        if (musicLaunched) Music.press(this, KeyEvent.KEYCODE_MEDIA_PAUSE);
        musicLaunched = false;
        if (savedVolume >= 0) {
            getSystemService(AudioManager.class).setStreamVolume(AudioManager.STREAM_MUSIC, savedVolume, 0);
            savedVolume = -1;
        }
    }

    private void setMusicVolume() {
        int percent = new Store(this).musicVolume();
        if (percent == 0) return;
        AudioManager am = getSystemService(AudioManager.class);
        if (savedVolume < 0) savedVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.max(1, Math.round(max * percent / 100f)), 0);
    }

    /** Suoneria di sistema in loop sul canale sveglia (rispetta il volume sveglia del telefono). */
    private void playTone() {
        if (player != null) return;
        Uri[] uris = {RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM),
                Settings.System.DEFAULT_ALARM_ALERT_URI, Settings.System.DEFAULT_RINGTONE_URI};
        for (Uri uri : uris) {
            if (uri == null) continue;
            MediaPlayer p = new MediaPlayer();
            try {
                p.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
                p.setDataSource(this, uri);
                p.setLooping(true);
                p.prepare();
                p.start();
                player = p;
                return;
            } catch (Exception e) {
                p.release();
            }
        }
    }

    private Notification notification(Alarm a) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Sveglia che suona", NotificationManager.IMPORTANCE_HIGH);
        ch.setSound(null, null); // il suono lo gestisce il servizio
        nm.createNotificationChannel(ch);
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, RingActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String title = a.label.isEmpty() ? "Sveglia " + a.time() : a.label + " " + a.time();
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(Music.label(this, a.app))
                .setCategory(Notification.CATEGORY_ALARM)
                .setOngoing(true)
                .setContentIntent(open)
                .setFullScreenIntent(open, true)
                .addAction(new Notification.Action.Builder(null, "Posticipa", service(ACTION_SNOOZE)).build())
                .addAction(new Notification.Action.Builder(null, "Ferma", service(ACTION_STOP)).build())
                .build();
    }

    private PendingIntent service(String action) {
        return PendingIntent.getService(this, action.hashCode(), new Intent(this, RingService.class).setAction(action),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    public void onDestroy() {
        stopSound();
        current = null;
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
