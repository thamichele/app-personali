package it.michele.calendario;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Impostazioni e copia locale dei calendari, nella cartella dell'utente:
 * Linux ~/.config/calendario, Windows %APPDATA%\Calendario. File leggibili solo dall'utente.
 */
final class Store {
    static final Path DIR = dir();
    private static final Path SETTINGS = DIR.resolve("impostazioni.properties");
    private static final Path CACHE = DIR.resolve("calendari.dat");
    private static final int CACHE_VERSION = 1;

    final Properties p = new Properties();

    private static Path dir() {
        String os = System.getProperty("os.name", "").toLowerCase();
        Path d;
        if (os.contains("win")) {
            String appdata = System.getenv("APPDATA");
            d = Paths.get(appdata != null ? appdata : System.getProperty("user.home"), "Calendario");
        } else {
            String xdg = System.getenv("XDG_CONFIG_HOME");
            d = (xdg != null && !xdg.isEmpty() ? Paths.get(xdg) : Paths.get(System.getProperty("user.home"), ".config")).resolve("calendario");
        }
        try {
            Files.createDirectories(d);
            privateFile(d, "rwx------");
        } catch (IOException ignored) { }
        return d;
    }

    /** Su Linux solo il proprietario può leggere (contiene la password); su Windows vale già il profilo utente. */
    static void privateFile(Path f, String perms) {
        try { Files.setPosixFilePermissions(f, PosixFilePermissions.fromString(perms)); } catch (Exception ignored) { }
    }

    Store() {
        if (Files.exists(SETTINGS)) {
            try (InputStream in = Files.newInputStream(SETTINGS)) {
                p.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
            } catch (IOException ignored) { }
        }
    }

    String get(String k, String def) { return p.getProperty(k, def); }

    int getInt(String k, int def) {
        try { return Integer.parseInt(p.getProperty(k)); } catch (Exception e) { return def; }
    }

    boolean getBool(String k, boolean def) {
        String v = p.getProperty(k);
        return v == null ? def : Boolean.parseBoolean(v);
    }

    void put(String k, Object v) {
        if (v == null) p.remove(k); else p.setProperty(k, String.valueOf(v));
    }

    String server() { return get("server", ""); }
    String user() { return get("utente", ""); }
    String password() { return get("password", ""); }
    int syncMinutes() { return getInt("sincronizza", 15); }
    int theme() { return Math.max(0, Math.min(5, getInt("tema", 0))); }
    int defaultReminder() { return getInt("promemoria", 15); }
    /** 0 = quella del sistema */
    int scale() { return getInt("scala", 0); }

    Set<String> hidden() {
        String h = get("nascosti", "");
        return h.isEmpty() ? new HashSet<>() : new HashSet<>(Arrays.asList(h.split("\n")));
    }

    void hidden(Set<String> s) { put("nascosti", String.join("\n", s)); }

    synchronized void save() {
        try {
            Path tmp = DIR.resolve("impostazioni.tmp");
            try (OutputStream out = Files.newOutputStream(tmp)) {
                privateFile(tmp, "rw-------");
                p.store(new java.io.OutputStreamWriter(out, StandardCharsets.UTF_8), "Calendario");
            }
            Files.move(tmp, SETTINGS, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            System.err.println("Impostazioni non salvate: " + e);
        }
    }

    // ---------- copia locale ----------

    static List<Model.Coll> loadCache() {
        List<Model.Coll> out = new ArrayList<>();
        if (!Files.exists(CACHE)) return out;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(CACHE)))) {
            if (in.readInt() != CACHE_VERSION) return out;
            int n = in.readInt();
            for (int i = 0; i < n; i++) {
                Model.Coll c = new Model.Coll();
                c.url = str(in); c.name = str(in); c.color = str(in); c.ctag = str(in);
                c.kind = in.readInt();
                c.readOnly = in.readBoolean();
                int m = in.readInt();
                for (int j = 0; j < m; j++) {
                    String href = str(in), etag = str(in), data = str(in);
                    c.items.put(href, new Model.Res(href, etag, data));
                }
                out.add(c);
            }
        } catch (IOException e) {
            System.err.println("Copia locale illeggibile, verrà riscaricata: " + e);
            out.clear();
        }
        return out;
    }

    static synchronized void saveCache(List<Model.Coll> colls) {
        try {
            Path tmp = DIR.resolve("calendari.tmp");
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp)))) {
                privateFile(tmp, "rw-------");
                out.writeInt(CACHE_VERSION);
                out.writeInt(colls.size());
                for (Model.Coll c : colls) {
                    str(out, c.url); str(out, c.name); str(out, c.color); str(out, c.ctag);
                    out.writeInt(c.kind);
                    out.writeBoolean(c.readOnly);
                    out.writeInt(c.items.size());
                    for (Model.Res r : c.items.values()) { str(out, r.href); str(out, r.etag); str(out, r.data); }
                }
            }
            Files.move(tmp, CACHE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            System.err.println("Copia locale non salvata: " + e);
        }
    }

    static void deleteCache() {
        try { Files.deleteIfExists(CACHE); } catch (IOException ignored) { }
    }

    private static String str(DataInputStream in) throws IOException {
        int len = in.readInt();
        if (len < 0) return null;
        byte[] b = new byte[len];
        in.readFully(b);
        return new String(b, StandardCharsets.UTF_8);
    }

    private static void str(DataOutputStream out, String s) throws IOException {
        if (s == null) { out.writeInt(-1); return; }
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        out.writeInt(b.length);
        out.write(b);
    }

    // ---------- promemoria già mostrati ----------

    private static final Path FIRED = DIR.resolve("promemoria.txt");

    static Set<String> loadFired() {
        try { return new HashSet<>(Files.readAllLines(FIRED, StandardCharsets.UTF_8)); } catch (IOException e) { return null; }
    }

    static void saveFired(Set<String> s) {
        try { Files.write(FIRED, s, StandardCharsets.UTF_8); } catch (IOException ignored) { }
    }
}
