package it.michele.sveglia;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.media.browse.MediaBrowser;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Sceglie cosa riprodurre: sfoglia il catalogo dell'app (lo stesso usato da Android Auto) filtrandolo col testo
 * scritto, oppure salva quel testo come ricerca da far fare all'app quando suona la sveglia. Restituisce media, mediaTitle e query.
 */
public class BrowseActivity extends Activity {
    static final String EXTRA_PKG = "pkg";
    static final String EXTRA_MEDIA = "media";
    static final String EXTRA_TITLE = "title";
    static final String EXTRA_QUERY = "query";

    private MediaBrowser browser;
    private final List<String> stack = new ArrayList<>();
    private List<MediaBrowser.MediaItem> items = new ArrayList<>();
    private final List<Object> entries = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private EditText search;
    private TextView status;
    private TextView access;
    private String pkg = "";

    private static final Object LAST = new Object();
    private static final Object QUERY = new Object();
    private static final Object UP = new Object();

    private final MediaBrowser.SubscriptionCallback children = new MediaBrowser.SubscriptionCallback() {
        @Override
        public void onChildrenLoaded(String parentId, List<MediaBrowser.MediaItem> list) {
            if (stack.isEmpty() || !parentId.equals(stack.get(stack.size() - 1))) return;
            show(list, list.isEmpty() ? "Nessun elemento" : "");
        }

        @Override
        public void onError(String parentId) { show(new ArrayList<MediaBrowser.MediaItem>(), "Impossibile leggere questa cartella"); }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        String extra = getIntent().getStringExtra(EXTRA_PKG);
        pkg = extra == null ? "" : extra;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.setFitsSystemWindows(true);
        int pad = Ui.dp(this, 16);
        root.setPadding(pad, pad, pad, 0);
        root.addView(Ui.text(this, "Cosa riprodurre", 26, Ui.TEXT, false));
        root.addView(Ui.text(this, Music.label(this, pkg), 15, Ui.OFF, false));

        search = new EditText(this);
        search.setSingleLine();
        search.setHint("Cerca");
        search.setTextColor(Ui.TEXT);
        search.setHintTextColor(Ui.OFF);
        search.setImeOptions(EditorInfo.IME_ACTION_DONE);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable e) { refresh(); }
        });
        root.addView(search, matchWrap(Ui.dp(this, 12)));

        status = Ui.text(this, "Connessione all'app...", 14, Ui.OFF, false);
        root.addView(status, matchWrap(Ui.dp(this, 4)));

        ListView list = new ListView(this);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<String>());
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) { pick(entries.get(position)); }
        });
        root.addView(list, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);

        access = Ui.text(this, "", 14, Ui.TEXT, false);
        access.setBackground(Ui.rounded(0xFF5A1A10, Ui.dp(this, 16)));
        access.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 10));
        access.setVisibility(View.GONE);
        access.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            try {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                        Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                        new ComponentName(BrowseActivity.this, MediaListener.class).flattenToString()));
            } catch (RuntimeException e) {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            }
        }});
        root.addView(access, 4, matchWrap(Ui.dp(this, 8)));

        ComponentName service = Music.browserService(this, pkg);
        if (service == null) {
            status.setText("L'app non ha un catalogo da sfogliare: scrivi cosa cercare, verrà chiesto all'app quando suona.");
        } else {
            browser = new MediaBrowser(this, service, new MediaBrowser.ConnectionCallback() {
                @Override
                public void onConnected() { open(browser.getRoot()); }

                @Override
                public void onConnectionFailed() {
                    status.setText("L'app non permette di sfogliare il suo catalogo: scrivi cosa cercare, "
                            + "verrà chiesto all'app quando suona.");
                            refreshAccess();
                }
            }, null);
            browser.connect();
        }
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAccess();
    }

    /** Senza catalogo la ricerca passa dalla sessione multimediale dell'app, che richiede l'accesso alle notifiche. */
    private void refreshAccess() {
        // WebRadioDroid si comanda già con la sua azione di avvio; le altre app spesso ignorano il catalogo
        boolean needed = !Music.isWebRadio(pkg) && !Music.sessionAccess(this);
        access.setVisibility(needed ? View.VISIBLE : View.GONE);
        access.setText("Per comandare questa app consenti a Sveglia l'accesso alle notifiche "
                + "(serve solo a comandare la riproduzione, le notifiche non vengono lette). Tocca qui.");
    }

    private void open(String parentId) {
        if (!stack.isEmpty()) browser.unsubscribe(stack.get(stack.size() - 1));
        stack.add(parentId);
        status.setText("Caricamento...");
        browser.subscribe(parentId, children);
    }

    private void up() {
        browser.unsubscribe(stack.remove(stack.size() - 1));
        String parent = stack.remove(stack.size() - 1);
        open(parent);
    }

    private void show(List<MediaBrowser.MediaItem> list, String message) {
        items = list;
        status.setText(message);
        refresh();
    }

    private void refresh() {
        String q = search.getText().toString().trim();
        String filter = q.toLowerCase(Locale.ROOT);
        entries.clear();
        List<String> labels = new ArrayList<>();
        entries.add(LAST);
        labels.add("▶  Ultima riproduzione");
        if (!q.isEmpty()) {
            entries.add(QUERY);
            labels.add("🔍  Quando suona, cerca “" + q + "”");
        }
        if (stack.size() > 1) {
            entries.add(UP);
            labels.add("⬆  Indietro");
        }
        for (MediaBrowser.MediaItem item : items) {
            String title = title(item);
            if (!title.toLowerCase(Locale.ROOT).contains(filter)) continue;
            CharSequence sub = item.getDescription().getSubtitle();
            entries.add(item);
            labels.add((item.isPlayable() ? "♪  " : "📁  ") + title + (sub == null ? "" : "\n     " + sub));
        }
        adapter.clear();
        adapter.addAll(labels);
    }

    private void pick(Object entry) {
        if (entry == UP) {
            search.setText("");
            up();
            return;
        }
        Intent result = new Intent();
        if (entry == QUERY) {
            result.putExtra(EXTRA_QUERY, search.getText().toString().trim());
        } else if (entry instanceof MediaBrowser.MediaItem) {
            MediaBrowser.MediaItem item = (MediaBrowser.MediaItem) entry;
            if (!item.isPlayable()) {
                search.setText("");
                open(item.getMediaId());
                return;
            }
            result.putExtra(EXTRA_MEDIA, item.getMediaId()).putExtra(EXTRA_TITLE, title(item));
        }
        setResult(RESULT_OK, result);
        finish();
    }

    private static String title(MediaBrowser.MediaItem item) {
        CharSequence t = item.getDescription().getTitle();
        return t == null ? item.getMediaId() : t.toString();
    }

    @Override
    public void onBackPressed() {
        if (browser != null && browser.isConnected() && stack.size() > 1) {
            search.setText("");
            up();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (browser != null) browser.disconnect();
        super.onDestroy();
    }

    private LinearLayout.LayoutParams matchWrap(int topMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = topMargin;
        return lp;
    }
}
