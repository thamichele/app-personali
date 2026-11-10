# App personali
Collezione di app semplici per l'uso quotidiano, per Android e per PC. Ogni app è in `apps/<nome>/`: Java puro,
interfaccia costruita da codice, nessuna libreria esterna, build senza Gradle.

| App | Cosa fa |
|-----|---------|
| [Sveglia](apps/sveglia/README.md) | Sveglie multiple che suonano con un'app musicale (WebRadioDroid, Qobuz, ...) |
| [Calendario](apps/calendario/README.md) | Per PC (Windows e Linux): calendari CalDAV e compleanni CardDAV, con promemoria |
| [B1g-Pentester](apps/b1g-pentester/README.md) | Per Linux, da terminale: pentesting SSH della propria rete (solo su dispositivi propri) |

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

Un'app per PC ha invece `TYPE=desktop` e `MAIN` in `app.conf`, i sorgenti in `src/`, le risorse in `res/` e va nella
matrice del job `desktop`: `build.sh` crea il jar e, con `PACKAGE=1`, il pacchetto con Java incluso (jpackage).
Le release per Windows e Linux le pubblica il job `desktop-release`; non va nel repository F-Droid.

Un'app da terminale per Linux ha `TYPE=cli` in `app.conf` e i sorgenti Python (libreria standard, niente `pip`) in
`src/`: `build.sh` controlla la sintassi e crea un tar; `install.sh` la installa in `~/.local`. Va nella matrice del
job `cli`; non ha release né repository F-Droid.

Un'app che resta nel suo repository basta aggiungerla a `fdroid/esterne.txt` (indirizzo del suo repository F-Droid)
e alla seconda tabella: `fdroid.sh` ne copia l'ultima versione, con la sua firma, nel repository unico.

## Installare
Aggiungi a F-Droid (o Droid-ify, Neo Store) il repository unico con tutte le app:

https://raw.githubusercontent.com/thamichele/app-personali/fdroid/repo?fingerprint=DAE772F4AD70621AC22E5EBB60164CC70A9472A9F4638A69385108905A78FA02

## Pubblicare una nuova versione
1. Aggiorna `VERSION_NAME` in `apps/<app>/app.conf` (nuova funzione: MINORE+1; correzione: CORREZIONE+1).
2. Unisci in `main`: GitHub Actions compila con la chiave vera (secret `RELEASE_KEYSTORE_BASE64` e
   `RELEASE_KEYSTORE_PASSWORD`), crea la release `<app>-vX.Y.Z` e aggiorna il ramo `fdroid`.

Le nuove versioni delle app esterne arrivano nel repository unico entro un'ora: il workflow gira anche ogni
ora. A mano: `FDROID_KEYSTORE=... FDROID_PASS=... ./fdroid.sh [APK...]`, poi il push del ramo `fdroid`.
