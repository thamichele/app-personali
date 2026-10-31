package it.michele.sveglia;

import android.app.NotificationManager;
import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.media.browse.MediaBrowser;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Avvio delle app musicali usate come suoneria. */
final class Music {
    static final String WEBRADIO = "io.github.thamichele.webradiodroid";
    static final String QOBUZ = "com.qobuz.music";
    static final String BROWSER_SERVICE = "android.media.browse.MediaBrowserService";

    private Music() {}

    /** App avviabile con nome visibile. */
    static final class App {
        final String pkg;
        final String label;
        App(String pkg, String label) { this.pkg = pkg; this.label = label; }
    }

    static boolean isWebRadio(String pkg) {
        return pkg.equals(WEBRADIO) || pkg.equals(WEBRADIO + ".debug");
    }

    /**
     * Intent che apre l'app e, quando l'app lo permette, avvia già la riproduzione: WebRadioDroid ha un'azione
     * per l'ultima stazione o una stazione precisa; per una ricerca si usa l'azione standard "riproduci da ricerca".
     */
    static Intent launchIntent(Context c, Alarm a) {
        Intent i = playIntent(c, a);
        if (i == null) i = c.getPackageManager().getLaunchIntentForPackage(a.app);
        if (i != null) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }

    /** true se l'intent di avvio fa partire da solo quello che è stato scelto. */
    static boolean launchPlays(Context c, Alarm a) {
        return playIntent(c, a) != null;
    }

    private static Intent playIntent(Context c, Alarm a) {
        Intent i = null;
        if (isWebRadio(a.app)) {
            i = new Intent("org.y20k.transistor.action.START").setClassName(a.app, "org.y20k.transistor.MainActivity");
            if (a.media.isEmpty()) i.putExtra("START_LAST_PLAYED_STATION", true);
            else i.putExtra("STATION_UUID", a.media);
        } else if (a.isLink()) {
            // link di una playlist o di un album (es. open.qobuz.com/playlist/...), aperto solo nell'app scelta
            i = new Intent(Intent.ACTION_VIEW, Uri.parse(a.query)).setPackage(a.app);
        } else if (a.media.isEmpty() && !a.query.isEmpty()) {
            i = new Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).setPackage(a.app)
                    .putExtra(SearchManager.QUERY, a.query);
        }
        return i != null && c.getPackageManager().resolveActivity(i, 0) != null ? i : null;
    }

    static boolean installed(Context c, String pkg) {
        return !pkg.isEmpty() && c.getPackageManager().getLaunchIntentForPackage(pkg) != null;
    }

    static String label(Context c, String pkg) {
        if (pkg.isEmpty()) return "Suoneria di sistema";
        try {
            PackageManager pm = c.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg + " (non installata)";
        }
    }

    /** App musicali note che non sempre si dichiarano tali. */
    private static final Set<String> KNOWN = new HashSet<>(java.util.Arrays.asList(
            QOBUZ, "com.spotify.music", "com.aspiro.tidal", "com.google.android.apps.youtube.music",
            "deezer.android.app", "com.amazon.mp3", "com.apple.android.music", "com.soundcloud.android",
            "com.sec.android.app.music", "com.maxmpz.audioplayer", "tv.plex.labs.plexamp", "com.bandcamp.android"));

    /**
     * Solo app musicali: WebRadioDroid, quelle note, quelle con categoria "audio" o che si dichiarano lettori
     * musicali. Prima WebRadioDroid e Qobuz, poi in ordine alfabetico.
     */
    static List<App> musicApps(Context c) {
        PackageManager pm = c.getPackageManager();
        Set<String> music = new HashSet<>();
        Intent player = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC);
        for (ResolveInfo r : pm.queryIntentActivities(player, 0)) music.add(r.activityInfo.packageName);

        List<App> apps = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo r : pm.queryIntentActivities(launcher, 0)) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(c.getPackageName()) || !seen.add(pkg)) continue;
            boolean audio = r.activityInfo.applicationInfo.category == ApplicationInfo.CATEGORY_AUDIO;
            if (isWebRadio(pkg) || KNOWN.contains(pkg) || music.contains(pkg) || audio) {
                apps.add(new App(pkg, r.loadLabel(pm).toString()));
            }
        }
        Collections.sort(apps, new Comparator<App>() {
            @Override
            public int compare(App a, App b) {
                int pa = priority(a.pkg), pb = priority(b.pkg);
                return pa != pb ? pa - pb : a.label.compareToIgnoreCase(b.label);
            }
        });
        return apps;
    }

    private static int priority(String pkg) {
        if (isWebRadio(pkg)) return 0;
        if (pkg.equals(QOBUZ)) return 1;
        return 2;
    }

    /** Servizio catalogo dell'app, null se non ne ha. */
    static ComponentName browserService(Context c, String pkg) {
        List<ResolveInfo> list = c.getPackageManager().queryIntentServices(new Intent(BROWSER_SERVICE).setPackage(pkg), 0);
        return list.isEmpty() ? null : new ComponentName(pkg, list.get(0).serviceInfo.name);
    }

    /**
     * Comanda l'app scelta, mai la sessione di un'altra app: choice = true chiede l'elemento scelto o la ricerca,
     * false avvia l'ultima riproduzione. Strade in ordine: sessione multimediale (con l'accesso alle notifiche),
     * catalogo dell'app, tasto Play inviato solo a quell'app.
     */
    static void command(final Context c, final Alarm a, final boolean choice) {
        final Store store = new Store(c);
        final String what = choice ? (a.media.isEmpty() ? "ricerca" : "elemento scelto") : "Play";
        MediaController session = session(c, a.app);
        if (session != null) {
            PlaybackState st = session.getPlaybackState();
            long needed = !choice ? PlaybackState.ACTION_PLAY
                    : a.media.isEmpty() ? PlaybackState.ACTION_PLAY_FROM_SEARCH : PlaybackState.ACTION_PLAY_FROM_MEDIA_ID;
            boolean declared = st != null && (st.getActions() & needed) != 0;
            store.log("sessione: invio " + what + (declared ? "" : " (non dichiarato dall'app)"));
            send(session.getTransportControls(), a, choice);
            return;
        }
        ComponentName service = browserService(c, a.app);
        if (service == null) {
            store.log("nessuna sessione né catalogo: tasto Play all'app");
            pressApp(c, a.app, KeyEvent.KEYCODE_MEDIA_PLAY);
            return;
        }
        final MediaBrowser[] holder = new MediaBrowser[1];
        holder[0] = new MediaBrowser(c, service, new MediaBrowser.ConnectionCallback() {
            @Override
            public void onConnected() {
                store.log("catalogo: invio " + what);
                send(new MediaController(c, holder[0].getSessionToken()).getTransportControls(), a, choice);
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() { holder[0].disconnect(); }
                }, 5_000);
            }

            @Override
            public void onConnectionFailed() {
                store.log("catalogo rifiutato: tasto Play all'app");
                pressApp(c, a.app, KeyEvent.KEYCODE_MEDIA_PLAY);
            }
        }, null);
        holder[0].connect();
    }

    private static void send(MediaController.TransportControls tc, Alarm a, boolean choice) {
        if (!choice) tc.play();
        else if (!a.media.isEmpty()) tc.playFromMediaId(a.media, null);
        else tc.playFromSearch(a.query, null);
    }

    /** Mette in pausa l'app che suonava per la sveglia. */
    static void pause(Context c, String pkg) {
        MediaController session = session(c, pkg);
        if (session != null) session.getTransportControls().pause();
        else pressApp(c, pkg, KeyEvent.KEYCODE_MEDIA_PAUSE);
    }

    /** true se l'utente ha concesso l'accesso alle notifiche, che permette di vedere le sessioni multimediali. */
    static boolean sessionAccess(Context c) {
        return c.getSystemService(NotificationManager.class)
                .isNotificationListenerAccessGranted(new ComponentName(c, MediaListener.class));
    }

    /** Sessione multimediale attiva dell'app, null se non c'è o se manca l'accesso. */
    private static MediaController session(Context c, String pkg) {
        if (!sessionAccess(c)) return null;
        MediaSessionManager msm = c.getSystemService(MediaSessionManager.class);
        for (MediaController mc : msm.getActiveSessions(new ComponentName(c, MediaListener.class))) {
            if (mc.getPackageName().equals(pkg)) return mc;
        }
        return null;
    }

    /**
     * Tasto multimediale solo per l'app indicata. Se la sua sessione è la prima per il sistema (serve l'accesso
     * alle notifiche per saperlo) passa dal sistema, come un tasto delle cuffie; altrimenti va direttamente al
     * ricevitore dei tasti multimediali dell'app.
     */
    static void pressApp(Context c, String pkg, int keyCode) {
        long t = SystemClock.uptimeMillis();
        if (sessionAccess(c)) {
            List<MediaController> sessions = c.getSystemService(MediaSessionManager.class)
                    .getActiveSessions(new ComponentName(c, MediaListener.class));
            if (!sessions.isEmpty() && sessions.get(0).getPackageName().equals(pkg)) {
                AudioManager am = c.getSystemService(AudioManager.class);
                am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, keyCode, 0));
                am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, keyCode, 0));
                return;
            }
        }
        for (int action : new int[]{KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP}) {
            c.sendBroadcast(new Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(pkg)
                    .putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(t, t, action, keyCode, 0)));
        }
    }
}
