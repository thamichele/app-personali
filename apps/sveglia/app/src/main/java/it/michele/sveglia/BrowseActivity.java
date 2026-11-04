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
import android.view.ViewGroup;
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
    private ArrayAdapter<String[]> adapter;
    private EditText search;
    private TextView status;
    private View access;
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
        Ui.apply(this);
        String extra = getIntent().getStringExtra(EXTRA_PKG);
        pkg = extra == null ? "" : extra;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.setFitsSystemWindows(true);
        int pad = Ui.dp(this, 16);
        root.setPadding(pad, Ui.dp(this, 12), pad, 0);
        root.addView(Ui.header(this, "Cosa riprodurre", true));
        root.addView(Ui.text(this, Music.label(this, pkg), 13, Ui.OFF, false));

        search = Ui.field(this, "Cerca, o incolla un link di playlist o album");
        Ui.leading(search, R.drawable.ic_search, Ui.OFF, 18);
        search.setGravity(android.view.Gravity.CENTER_VERTICAL);
        search.setImeOptions(EditorInfo.IME_ACTION_DONE);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable e) { refresh(); }
        });
        root.addView(search, Ui.matchWrap(this, 16));

        status = Ui.text(this, "Connessione all'app...", 13, Ui.OFF, false);
        root.addView(status, Ui.matchWrap(this, 8));

        ListView list = new ListView(this);
        list.setDivider(new android.graphics.drawable.ColorDrawable(0));
        list.setDividerHeight(Ui.dp(this, 8));
        list.setSelector(new android.graphics.drawable.ColorDrawable(0));
        list.setClipToPadding(false);
        list.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 16));
        adapter = new ArrayAdapter<String[]>(this, 0, new ArrayList<String[]>()) {
            @Override
            public View getView(int position, View convert, ViewGroup parent) { return row(getItem(position), convert); }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) { pick(entries.get(position)); }
        });
        root.addView(list, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);

        access = Ui.warning(this, "Serve l'accesso alle notifiche",
                "Per comandare questa app consenti a Sveglia l'accesso alle notifiche: serve solo a comandare "
                + "la riproduzione, le notifiche non vengono lette. Tocca qui.");
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
        root.addView(access, 4, Ui.matchWrap(this, 12));

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
        List<String[]> labels = new ArrayList<>();
        entries.add(LAST);
        labels.add(new String[]{"play", "Ultima riproduzione", null});
        if (!q.isEmpty()) {
            entries.add(QUERY);
            labels.add(q.startsWith("https://") ? new String[]{"link", "Quando suona, apri il link e premi Play", q}
                    : new String[]{"search", "Quando suona, cerca “" + q + "”", null});
        }
        if (stack.size() > 1) {
            entries.add(UP);
            labels.add(new String[]{"up", "Indietro", null});
        }
        for (MediaBrowser.MediaItem item : items) {
            String title = title(item);
            if (!title.toLowerCase(Locale.ROOT).contains(filter)) continue;
            CharSequence sub = item.getDescription().getSubtitle();
            entries.add(item);
            labels.add(new String[]{item.isPlayable() ? "music" : "folder", title, sub == null ? null : sub.toString()});
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

    /** Riga dell'elenco: icona, titolo, sottotitolo; le scelte rapide in alto sono nel colore principale. */
    private View row(String[] e, View convert) {
        LinearLayout row;
        TextView title, sub;
        android.widget.ImageView icon;
        if (convert instanceof LinearLayout) {
            row = (LinearLayout) convert;
            icon = (android.widget.ImageView) row.getChildAt(0);
            LinearLayout col = (LinearLayout) row.getChildAt(1);
            title = (TextView) col.getChildAt(0);
            sub = (TextView) col.getChildAt(1);
        } else {
            row = new LinearLayout(this);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(Ui.dp(this, 56));
            row.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
            icon = new android.widget.ImageView(this);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Ui.dp(this, 20), Ui.dp(this, 20));
            ilp.rightMargin = Ui.dp(this, 14);
            row.addView(icon, ilp);
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            title = Ui.text(this, "", 15, Ui.TEXT, false);
            sub = Ui.text(this, "", 13, Ui.OFF, false);
            title.setMaxLines(2);
            sub.setSingleLine();
            sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
            col.addView(title);
            col.addView(sub);
            row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        String kind = e[0];
        boolean quick = !kind.equals("music") && !kind.equals("folder");
        int res = kind.equals("play") ? R.drawable.ic_play : kind.equals("link") ? R.drawable.ic_link
                : kind.equals("search") ? R.drawable.ic_search : kind.equals("up") ? R.drawable.ic_up
                : kind.equals("folder") ? R.drawable.ic_folder : R.drawable.ic_music;
        icon.setImageResource(res);
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(quick ? Ui.ACCENT_TEXT : Ui.OFF));
        title.setText(e[1]);
        title.setTextColor(quick ? Ui.ACCENT_TEXT : Ui.TEXT);
        sub.setText(e[2]);
        sub.setVisibility(e[2] == null ? View.GONE : View.VISIBLE);
        row.setBackground(Ui.touch(this, Ui.box(this, Ui.PANEL, Ui.BORDER, 12), 12));
        return row;
    }
}
