# App personali
Collezione di app Android semplici per l'uso quotidiano. Ogni app è in `apps/<nome>/`: Java puro, interfaccia
costruita da codice, nessuna libreria esterna, build senza Gradle.

| App | Cosa fa |
|-----|---------|
| [Sveglia](apps/sveglia/README.md) | Sveglie multiple che suonano con un'app musicale (WebRadioDroid, Qobuz, ...) |

Nel repository F-Droid ci sono anche le app che hanno un repository GitHub proprio:

| App | Cosa fa |
|-----|---------|
| [WG Switch](https://github.com/thamichele/wgswitch) | Spegne e riaccende il tunnel WireGuard in base a WiFi, Bluetooth e Android Auto |
| [WebRadioDroid](https://github.com/thamichele/webradiodroid) | Webradio per telefono e Android Auto |
| [SMBCommander](https://github.com/thamichele/SMBCommander) | File manager per il NAS di casa, via SMB |
| [AntiDroidVirus](https://github.com/thamichele/antidroidvirus) | Registra e segnala l'uso dei permessi e le app sospette |

## Build
`KS_PASS=... ./build.sh <app>` compila con aapt2, javac, dx e apksigner (requisiti in testa allo script) e
produce `build/<app>/<app>-X.Y.Z.apk`. GitHub Actions compila ogni app a ogni push con una chiave di prova.
La chiave di firma vera e la sua password non sono nel repository: servono per pubblicare gli aggiornamenti,
che Android accetta solo se firmati con la stessa chiave. Ogni app ha la sua chiave (`keystore/<app>.jks`).

## Nuova app
1. Crea `apps/<nome>/` con `app.conf` (pacchetto, nome, versione), `app/src/main/` e `fdroid/metadata/`.
2. Aggiungi `<nome>` alla matrice in `.github/workflows/build.yml` e una riga alla tabella qui sopra.

Un'app che resta nel suo repository basta aggiungerla a `fdroid/esterne.txt` (indirizzo del suo repository F-Droid)
e alla seconda tabella: `fdroid.sh` ne copia l'ultima versione, con la sua firma, nel repository unico.

## Installare
Aggiungi a F-Droid (o Droid-ify, Neo Store) il repository unico con tutte le app:

https://raw.githubusercontent.com/thamichele/app-personali/fdroid/repo?fingerprint=DAE772F4AD70621AC22E5EBB60164CC70A9472A9F4638A69385108905A78FA02

## Pubblicare una nuova versione
1. Aggiorna `VERSION_NAME` in `apps/<app>/app.conf` (nuova funzione: MINORE+1; correzione: CORREZIONE+1).
2. Unisci in `main`: GitHub Actions compila con la chiave vera (secret `RELEASE_KEYSTORE_BASE64` e
   `RELEASE_KEYSTORE_PASSWORD`), crea la release `<app>-vX.Y.Z` e aggiorna il ramo `fdroid`.

Le nuove versioni delle app esterne arrivano nel repository unico entro un giorno: il workflow gira anche ogni
notte. A mano: `FDROID_KEYSTORE=... FDROID_PASS=... ./fdroid.sh [APK...]`, poi il push del ramo `fdroid`.
