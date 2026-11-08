package it.michele.calendario;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Sincronizzazione nei due sensi. La rete lavora su un solo thread; i dati in memoria cambiano solo sul thread
 * dell'interfaccia (invokeAndWait), così l'interfaccia li legge senza blocchi.
 * Le modifiche vanno subito al server: se non è raggiungibile l'evento non viene salvato e lo si dice.
 */
final class Sync {
    private final Store store;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "sincronizzazione");
        t.setDaemon(true);
        return t;
    });
    /** Solo sul thread dell'interfaccia. */
    List<Model.Coll> colls;
    Runnable onChange = () -> { };
    volatile boolean busy;
    String status = "";

    Sync(Store store) {
        this.store = store;
        colls = Store.loadCache();
    }

    boolean configured() { return !store.server().isEmpty(); }

    private Dav dav() { return new Dav(store.server(), store.user(), store.password()); }

    /** Scarica le novità di tutti i calendari e rubriche; done riceve null o il messaggio d'errore. */
    void syncNow(Consumer<String> done) {
        if (!configured()) { if (done != null) done.accept("Configura il server nelle impostazioni"); return; }
        busy = true;
        onChange.run();
        final List<Model.Coll> current = colls;
        worker.execute(() -> {
            String err = null;
            List<Model.Coll> next = null;
            Map<Model.Coll, Map<String, String[]>> fetched = new HashMap<>();
            Map<Model.Coll, Map<String, String>> listed = new HashMap<>();
            Map<Model.Coll, String> ctags = new HashMap<>();
            try {
                Dav d = dav();
                next = d.discover();
                Map<String, Model.Coll> old = new HashMap<>();
                for (Model.Coll c : current) old.put(c.url, c);
                for (Model.Coll c : next) {
                    Model.Coll o = old.get(c.url);
                    if (o != null) { c.items.putAll(o.items); c.ctag = o.ctag; }
                    String ctag = null;
                    try { ctag = d.ctag(c); } catch (IOException ignored) { }
                    if (ctag != null && ctag.equals(c.ctag) && !c.items.isEmpty()) continue;
                    Map<String, String> etags = d.etags(c);
                    List<String> changed = new ArrayList<>();
                    for (Map.Entry<String, String> e : etags.entrySet()) {
                        Model.Res r = c.items.get(e.getKey());
                        if (r == null || e.getValue().isEmpty() || !e.getValue().equals(r.etag)) changed.add(e.getKey());
                    }
                    listed.put(c, etags);
                    fetched.put(c, changed.isEmpty() ? new HashMap<>() : d.fetch(c, changed));
                    ctags.put(c, ctag);
                }
            } catch (Exception e) {
                err = message(e);
            }
            final String error = err;
            final List<Model.Coll> result = next;
            apply(() -> {
                if (result != null) {
                    for (Model.Coll c : result) {
                        Map<String, String> etags = listed.get(c);
                        if (etags == null) continue;
                        c.items.keySet().retainAll(etags.keySet());
                        for (Map.Entry<String, String[]> f : fetched.get(c).entrySet()) {
                            String[] v = f.getValue();
                            String etag = v[0] != null ? v[0] : etags.get(f.getKey());
                            c.items.put(f.getKey(), new Model.Res(f.getKey(), etag, v[1]));
                        }
                        c.ctag = ctags.get(c);
                    }
                    colls = result;
                    Store.saveCache(colls);
                }
                busy = false;
                status = error != null ? error : "Sincronizzato alle " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
                if (done != null) done.accept(error);
            });
        });
    }

    /** Salva una risorsa nuova (etag null) o modificata sul server e poi in locale. */
    void put(Model.Coll c, String href, String etag, Ical.Comp root, Consumer<String> done) {
        final String data = Ical.write(root);
        worker.execute(() -> {
            String err = null;
            String[] saved = null;
            try { saved = dav().put(href, data, etag, c.kind == Model.CARD); } catch (Exception e) { err = message(e); }
            final String error = err;
            final String[] s = saved;
            apply(() -> {
                if (s != null) {
                    c.items.put(href, new Model.Res(href, s[0], s[1] != null && !s[1].isEmpty() ? s[1] : data));
                    c.ctag = null;
                    Store.saveCache(colls);
                }
                done.accept(error);
            });
        });
    }

    void delete(Model.Coll c, Model.Res r, Consumer<String> done) {
        worker.execute(() -> {
            String err = null;
            try { dav().delete(r.href, r.etag); } catch (Exception e) { err = message(e); }
            final String error = err;
            apply(() -> {
                if (error == null) {
                    c.items.remove(r.href);
                    c.ctag = null;
                    Store.saveCache(colls);
                }
                done.accept(error);
            });
        });
    }

    /** Prova l'accesso con dati nuovi senza toccare quelli salvati. */
    void test(String server, String user, String pass, Consumer<String> done) {
        worker.execute(() -> {
            String err = null;
            int cals = 0, books = 0;
            try {
                for (Model.Coll c : new Dav(server, user, pass).discover()) if (c.kind == Model.CAL) cals++; else books++;
            } catch (Exception e) { err = message(e); }
            final String msg = err != null ? err : "OK: " + cals + (cals == 1 ? " calendario" : " calendari") + ", "
                    + books + (books == 1 ? " rubrica" : " rubriche");
            final boolean ok = err == null;
            SwingUtilities.invokeLater(() -> done.accept((ok ? "" : "!") + msg));
        });
    }

    private void apply(Runnable r) {
        try {
            SwingUtilities.invokeAndWait(() -> { r.run(); onChange.run(); });
        } catch (Exception e) {
            Thread.currentThread().interrupt();
        }
    }

    static String message(Exception e) {
        if (e instanceof UnknownHostException) return "Server non trovato: controlla l'indirizzo";
        if (e instanceof ConnectException) return "Server non raggiungibile";
        if (e instanceof HttpTimeoutException) return "Il server non risponde";
        if (e instanceof javax.net.ssl.SSLException) return "Connessione sicura non riuscita (certificato non valido?)";
        if (e instanceof IllegalArgumentException) return "Indirizzo del server non valido";
        String m = e.getMessage();
        return m == null || m.isEmpty() ? "Errore: " + e.getClass().getSimpleName() : m;
    }
}
