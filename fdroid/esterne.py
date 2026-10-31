#!/usr/bin/env python3
"""Copia nel repository F-Droid unico l'ultima versione delle app elencate in esterne.txt.

Uso: esterne.py esterne.txt CARTELLA_FDROID
Per ogni repository F-Droid elencato legge index-v1.json, scarica l'APK più recente di ogni pacchetto in
repo/ (controllandone lo SHA-256 dichiarato nell'indice) e scrive metadata/<pacchetto>.yml con i dati
dell'indice. Gli APK restano firmati dalla loro chiave: Android accetta gli aggiornamenti come prima.
Un repository irraggiungibile viene saltato con un avviso, così non blocca le altre app.
"""
import hashlib
import json
import os
import sys
import urllib.request

# campi di index-v1.json -> campi dei metadati F-Droid
FIELDS = {"name": "Name", "summary": "Summary", "description": "Description", "authorName": "AuthorName",
          "license": "License", "categories": "Categories", "webSite": "WebSite",
          "sourceCode": "SourceCode", "issueTracker": "IssueTracker"}
TEXTS = ("name", "summary", "description")


def get(url):
    with urllib.request.urlopen(url, timeout=60) as r:
        return r.read()


def metadata(app):
    # nome, riassunto e descrizione possono stare solo nelle traduzioni: si usa la prima disponibile
    loc = next(iter(app.get("localized", {}).values()), {})
    meta = {}
    for key, field in FIELDS.items():
        value = app.get(key) or (loc.get(key) if key in TEXTS else None)
        if value:
            meta[field] = value
    return meta


def copy(base, work):
    index = json.loads(get(base + "/index-v1.json"))
    apps = {a["packageName"]: a for a in index["apps"]}
    for pkg, versions in index["packages"].items():
        apk = max(versions, key=lambda v: int(v["versionCode"]))
        if apk.get("hashType") != "sha256":
            raise ValueError(pkg + ": hash non sha256")
        dest = os.path.join(work, "repo", "%s_%s.apk" % (pkg, apk["versionCode"]))
        if not os.path.exists(dest):
            data = get(base + "/" + urllib.request.quote(apk["apkName"]))
            if hashlib.sha256(data).hexdigest() != apk["hash"]:
                raise ValueError(pkg + ": SHA-256 diverso da quello dell'indice")
            with open(dest, "wb") as f:
                f.write(data)
        # JSON è YAML valido: nessuna libreria in più
        with open(os.path.join(work, "metadata", pkg + ".yml"), "w") as f:
            json.dump(metadata(apps.get(pkg, {})), f, ensure_ascii=False, indent=2)
        print("%s %s da %s" % (pkg, apk["versionName"], base))


def main(listfile, work):
    for line in open(listfile):
        base = line.split("#")[0].strip().rstrip("/")
        if not base:
            continue
        if not base.startswith("https://"):
            sys.exit("solo indirizzi https: " + base)
        try:
            copy(base, work)
        except (OSError, ValueError, KeyError) as e:
            print("ATTENZIONE: salto %s (%s)" % (base, e), file=sys.stderr)


if __name__ == "__main__":
    main(*sys.argv[1:])
