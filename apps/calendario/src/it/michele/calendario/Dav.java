package it.michele.calendario;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Client WebDAV minimo per CalDAV (RFC 4791) e CardDAV (RFC 6352), con autenticazione Basic. */
final class Dav {
    static final class DavException extends IOException {
        final int status;
        DavException(int status, String msg) { super(msg); this.status = status; }
    }

    static final class Resp {
        final int status;
        final String body, etag;
        final URI uri;
        Resp(int status, String body, String etag, URI uri) { this.status = status; this.body = body; this.etag = etag; this.uri = uri; }
    }

    /** Una voce di una risposta multistatus: href e proprietà trovate (per nome locale). */
    static final class Item {
        String href;
        final Map<String, Element> props = new HashMap<>();
        String text(String name) {
            Element e = props.get(name);
            return e == null ? null : e.getTextContent().trim();
        }
    }

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final String auth;
    final URI base;

    Dav(String server, String user, String password) {
        String s = server.trim();
        if (!s.matches("(?i)https?://.*")) s = "https://" + s;
        base = URI.create(s);
        auth = "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    Resp request(String method, URI uri, String body, String type, String... headers) throws IOException {
        for (int hop = 0; hop < 5; hop++) {
            HttpRequest.Builder b = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60))
                    .header("Authorization", auth).header("User-Agent", "Calendario/" + Main.VERSION)
                    .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            if (type != null) b.header("Content-Type", type);
            for (int i = 0; i + 1 < headers.length; i += 2) b.header(headers[i], headers[i + 1]);
            HttpResponse<String> r;
            try {
                r = http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrotto");
            }
            int st = r.statusCode();
            if (st >= 300 && st < 400 && st != 304) {
                String loc = r.headers().firstValue("Location").orElse(null);
                if (loc == null) break;
                URI next = uri.resolve(loc);
                // mai da https a http: la password viaggerebbe in chiaro
                if ("https".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(next.getScheme()))
                    throw new DavException(st, "Il server reindirizza verso una connessione non cifrata");
                uri = next;
                continue;
            }
            return new Resp(st, r.body(), r.headers().firstValue("ETag").orElse(null), uri);
        }
        throw new DavException(310, "Troppi reindirizzamenti");
    }

    private Resp check(Resp r) throws DavException {
        if (r.status == 401) throw new DavException(401, "Utente o password errati");
        if (r.status == 403) throw new DavException(403, "Accesso negato dal server");
        if (r.status >= 400) throw new DavException(r.status, "Errore del server (" + r.status + ")");
        return r;
    }

    // ---------- XML ----------

    private static final String NS = "xmlns:d=\"DAV:\" xmlns:c=\"urn:ietf:params:xml:ns:caldav\" "
            + "xmlns:card=\"urn:ietf:params:xml:ns:carddav\" xmlns:cs=\"http://calendarserver.org/ns/\" "
            + "xmlns:ic=\"http://apple.com/ns/ical/\"";

    private List<Item> propfind(URI uri, int depth, String props) throws IOException {
        Resp r = check(request("PROPFIND", uri,
                "<?xml version=\"1.0\" encoding=\"utf-8\"?><d:propfind " + NS + "><d:prop>" + props + "</d:prop></d:propfind>",
                "application/xml; charset=utf-8", "Depth", String.valueOf(depth)));
        return multistatus(r);
    }

    private List<Item> report(URI uri, String body) throws IOException {
        return multistatus(check(request("REPORT", uri, "<?xml version=\"1.0\" encoding=\"utf-8\"?>" + body,
                "application/xml; charset=utf-8", "Depth", "1")));
    }

    private static List<Item> multistatus(Resp r) throws IOException {
        List<Item> out = new ArrayList<>();
        Document doc = xml(r.body);
        NodeList responses = doc.getElementsByTagNameNS("DAV:", "response");
        for (int i = 0; i < responses.getLength(); i++) {
            Element resp = (Element) responses.item(i);
            Item it = new Item();
            Element href = child(resp, "href");
            if (href == null) continue;
            it.href = canon(r.uri.resolve(href.getTextContent().trim()));
            for (Node ps = resp.getFirstChild(); ps != null; ps = ps.getNextSibling()) {
                if (!(ps instanceof Element) || !"propstat".equals(ps.getLocalName())) continue;
                Element status = child((Element) ps, "status");
                if (status != null && !status.getTextContent().contains(" 200")) continue;
                Element prop = child((Element) ps, "prop");
                if (prop == null) continue;
                for (Node p = prop.getFirstChild(); p != null; p = p.getNextSibling())
                    if (p instanceof Element) it.props.put(p.getLocalName(), (Element) p);
            }
            out.add(it);
        }
        return out;
    }

    /** Parser XML sicuro: niente DOCTYPE né entità esterne (XXE). */
    private static Document xml(String s) throws IOException {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            DocumentBuilder b = f.newDocumentBuilder();
            b.setErrorHandler(null);
            return b.parse(new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IOException("Risposta del server non valida");
        }
    }

    /** Forma unica di un indirizzo, perché i server codificano gli stessi href in modi diversi (%40 o @). */
    static String canon(URI u) {
        try {
            return new URI(u.getScheme(), u.getRawAuthority() == null ? null : u.getAuthority(), u.getPath(), null, null)
                    .normalize().toASCIIString();
        } catch (Exception e) {
            return u.toString();
        }
    }

    private static Element child(Element e, String local) {
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling())
            if (n instanceof Element && local.equals(n.getLocalName())) return (Element) n;
        return null;
    }

    private static String hrefIn(Item it, String prop) {
        Element e = it.props.get(prop);
        if (e == null) return null;
        Element h = child(e, "href");
        return h == null ? null : h.getTextContent().trim();
    }

    private static boolean hasType(Item it, String type) {
        Element rt = it.props.get("resourcetype");
        return rt != null && child(rt, type) != null;
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ---------- scoperta ----------

    /** Trova calendari (solo quelli con eventi: le attività VTODO si gestiscono altrove) e rubriche dell'utente. */
    List<Model.Coll> discover() throws IOException {
        List<URI> starts = new ArrayList<>();
        starts.add(base);
        String path = base.getPath();
        if (path == null || path.isEmpty() || path.equals("/")) {
            starts.add(base.resolve("/.well-known/caldav"));
            starts.add(base.resolve("/.well-known/carddav"));
        }
        Set<URI> homes = new LinkedHashSet<>();
        IOException last = null;
        boolean any = false;
        for (URI s : starts) {
            try {
                List<Item> r = propfind(s, 0, "<d:current-user-principal/><c:calendar-home-set/><card:addressbook-home-set/><d:resourcetype/>");
                any = true;
                for (Item it : r) {
                    URI at = URI.create(it.href);
                    addHome(homes, at, hrefIn(it, "calendar-home-set"));
                    addHome(homes, at, hrefIn(it, "addressbook-home-set"));
                    String principal = hrefIn(it, "current-user-principal");
                    if (principal != null) {
                        URI pu = at.resolve(principal);
                        for (Item p : propfind(pu, 0, "<c:calendar-home-set/><card:addressbook-home-set/>")) {
                            addHome(homes, pu, hrefIn(p, "calendar-home-set"));
                            addHome(homes, pu, hrefIn(p, "addressbook-home-set"));
                        }
                    }
                    if (hasType(it, "calendar") || hasType(it, "addressbook")) homes.add(at);
                }
            } catch (DavException e) {
                if (e.status == 401) throw e;
                last = e;
            } catch (IOException e) {
                last = e;
            }
        }
        if (!any && last != null) throw last;
        if (homes.isEmpty()) homes.add(base);

        Map<String, Model.Coll> found = new LinkedHashMap<>();
        for (URI home : homes) {
            List<Item> items;
            try {
                items = propfind(home, 1, "<d:resourcetype/><d:displayname/><ic:calendar-color/><cs:getctag/>"
                        + "<c:supported-calendar-component-set/><d:current-user-privilege-set/>");
            } catch (DavException e) {
                if (e.status == 401) throw e;
                continue;
            }
            for (Item it : items) {
                boolean cal = hasType(it, "calendar"), card = hasType(it, "addressbook");
                if (!cal && !card) continue;
                if (cal && !supportsEvents(it)) continue;
                Model.Coll c = new Model.Coll();
                c.url = it.href.endsWith("/") ? it.href : it.href + "/";
                c.kind = cal ? Model.CAL : Model.CARD;
                String name = it.text("displayname");
                c.name = name == null || name.isEmpty() ? lastSegment(c.url) : name;
                c.color = it.text("calendar-color");
                c.readOnly = !canWrite(it) || c.url.contains("contact_birthdays");
                found.put(c.url, c);
            }
        }
        if (found.isEmpty()) throw new IOException("Nessun calendario trovato a questo indirizzo");
        return new ArrayList<>(found.values());
    }

    private static void addHome(Set<URI> homes, URI at, String href) {
        if (href != null && !href.isEmpty()) homes.add(at.resolve(href));
    }

    private static boolean supportsEvents(Item it) {
        Element set = it.props.get("supported-calendar-component-set");
        if (set == null) return true;
        NodeList comps = set.getElementsByTagNameNS("urn:ietf:params:xml:ns:caldav", "comp");
        if (comps.getLength() == 0) return true;
        for (int i = 0; i < comps.getLength(); i++)
            if ("VEVENT".equalsIgnoreCase(((Element) comps.item(i)).getAttribute("name"))) return true;
        return false;
    }

    private static boolean canWrite(Item it) {
        Element set = it.props.get("current-user-privilege-set");
        if (set == null) return true;
        for (String p : new String[]{"write", "write-content", "all", "bind"})
            if (set.getElementsByTagNameNS("DAV:", p).getLength() > 0) return true;
        return false;
    }

    static String lastSegment(String url) {
        String s = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        String seg = s.substring(s.lastIndexOf('/') + 1);
        try { return java.net.URLDecoder.decode(seg, StandardCharsets.UTF_8); } catch (Exception e) { return seg; }
    }

    // ---------- contenuti ----------

    String ctag(Model.Coll c) throws IOException {
        List<Item> r = propfind(URI.create(c.url), 0, "<cs:getctag/><d:sync-token/>");
        if (r.isEmpty()) return null;
        String t = r.get(0).text("getctag");
        return t != null ? t : r.get(0).text("sync-token");
    }

    /** href → etag di tutte le risorse della collezione. */
    Map<String, String> etags(Model.Coll c) throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        URI self = URI.create(c.url);
        for (Item it : propfind(self, 1, "<d:getetag/><d:resourcetype/>")) {
            if (sameCollection(it.href, c.url) || hasType(it, "collection")) continue;
            String etag = it.text("getetag");
            out.put(it.href, etag == null ? "" : etag);
        }
        return out;
    }

    private static boolean sameCollection(String a, String b) {
        String x = URI.create(a).getPath(), y = URI.create(b).getPath();
        if (x == null || y == null) return false;
        return x.replaceAll("/+$", "").equals(y.replaceAll("/+$", ""));
    }

    /** Scarica le risorse indicate (multiget a blocchi; se il server non lo supporta, una GET per volta). */
    Map<String, String[]> fetch(Model.Coll c, List<String> hrefs) throws IOException {
        Map<String, String[]> out = new HashMap<>();
        boolean cal = c.kind == Model.CAL;
        String dataProp = cal ? "calendar-data" : "address-data";
        for (int i = 0; i < hrefs.size(); i += 100) {
            List<String> part = hrefs.subList(i, Math.min(hrefs.size(), i + 100));
            StringBuilder b = new StringBuilder(cal ? "<c:calendar-multiget " : "<card:addressbook-multiget ").append(NS)
                    .append("><d:prop><d:getetag/>").append(cal ? "<c:calendar-data/>" : "<card:address-data/>").append("</d:prop>");
            for (String h : part) b.append("<d:href>").append(esc(URI.create(h).getRawPath())).append("</d:href>");
            b.append(cal ? "</c:calendar-multiget>" : "</card:addressbook-multiget>");
            boolean ok = false;
            try {
                for (Item it : report(URI.create(c.url), b.toString())) {
                    String data = it.text(dataProp);
                    if (data != null && !data.isEmpty()) { out.put(it.href, new String[]{it.text("getetag"), data}); ok = true; }
                }
            } catch (DavException e) {
                if (e.status == 401) throw e;
            }
            if (!ok) for (String h : part) {
                Resp r = request("GET", URI.create(h), null, null);
                if (r.status == 200) out.put(h, new String[]{r.etag, r.body});
            }
        }
        return out;
    }

    /** Crea (etag null) o aggiorna una risorsa; restituisce etag e contenuto come li ha salvati il server. */
    String[] put(String href, String data, String etag, boolean card) throws IOException {
        String type = card ? "text/vcard; charset=utf-8" : "text/calendar; charset=utf-8";
        Resp r = etag == null
                ? request("PUT", URI.create(href), data, type, "If-None-Match", "*")
                : request("PUT", URI.create(href), data, type, "If-Match", etag);
        if (r.status == 412) throw new DavException(412, "L'evento è stato modificato altrove: sincronizza e riprova");
        check(r);
        Resp g = check(request("GET", URI.create(href), null, null));
        return new String[]{g.etag != null ? g.etag : r.etag, g.body};
    }

    void delete(String href, String etag) throws IOException {
        Resp r = etag == null || etag.isEmpty() ? request("DELETE", URI.create(href), null, null)
                : request("DELETE", URI.create(href), null, null, "If-Match", etag);
        if (r.status == 404) return;
        if (r.status == 412) throw new DavException(412, "L'evento è stato modificato altrove: sincronizza e riprova");
        check(r);
    }
}
