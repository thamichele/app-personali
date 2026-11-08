# Calendario
App per PC (Windows e Linux) che fa solo il calendario: sincronizza i calendari CalDAV nei due sensi, mostra i
compleanni dei contatti CardDAV e avvisa degli eventi con i promemoria.

## Cosa fa
- **Calendari CalDAV**: Nextcloud, Radicale, Baïkal, iCloud, Fastmail, mailbox.org... Si inserisce l'indirizzo del
  server, utente e password (o password per le app): calendari e rubriche si trovano da soli.
- **Eventi**: crea, modifica, elimina, sposta da un calendario all'altro. Tutto il giorno o con orario, più giorni,
  luogo, note, ripetizioni (ogni giorno/settimana/mese/anno; le regole più complesse fatte con altre app si vedono
  e restano intatte) e modifica o eliminazione di una sola volta di un evento ripetuto.
- **Viste**: mese, settimana, giorno, agenda dei prossimi 60 giorni, mini mese nella barra laterale.
  Si adatta alla finestra: sotto i 980 punti la barra laterale si apre col pulsante menu, sotto gli 820 la vista si
  sceglie da un menu, le celle piccole mostrano pallini colorati; i dialoghi scorrono sugli schermi bassi. La scala
  segue il sistema (anche quella frazionaria di GNOME/KDE) o si imposta nelle Impostazioni.
  Doppio clic su uno spazio vuoto per creare un evento. Tasti: ← → per spostarsi, T oggi, M/S/G/A per la vista,
  Ctrl+N nuovo evento, F5 sincronizza.
- **Promemoria**: notifica di sistema e finestrella con "Posticipa 5 min". Funzionano finché l'app è aperta: chiudendo
  la finestra resta nell'area di notifica (Esci dal suo menu). Nelle impostazioni si può farla partire con il computer.
- **Contatti**: le rubriche CardDAV si sincronizzano in sola lettura; i compleanni compaiono nel calendario
  (si possono nascondere) e c'è un elenco contatti con ricerca.
- **Escluse**: le attività (VTODO) e i calendari dei compleanni generati dal server, gestiti altrove.

## Sincronizzazione
- All'avvio, ogni 15 minuti (impostabile) e con il pulsante ⟳. Si scarica solo quello che è cambiato (ctag/etag).
- Le modifiche vanno subito al server, con il controllo dell'etag: se l'evento è stato cambiato altrove nel frattempo,
  l'app lo dice invece di sovrascriverlo. Senza rete gli eventi si vedono ma non si modificano.
- La copia locale e le impostazioni sono in `~/.config/calendario` (Linux) o `%APPDATA%\Calendario` (Windows),
  leggibili solo dall'utente. La password è salvata lì: su un PC condiviso usa una password per le app.

## Installare
Dalla [release](https://github.com/thamichele/app-personali/releases) `calendario-vX.Y.Z` (Java incluso):
- **Windows**: estrai `calendario-X.Y.Z-windows.zip` e avvia `Calendario\Calendario.exe`.
- **Arch**: `sudo pacman -U calendario-X.Y.Z-1-any.pkg.tar.zst` (usa il Java del sistema, compare nel menu e si
  avvia con `calendario`). Oppure dai sorgenti: `cd apps/calendario/arch && makepkg -si`.
- **Debian, Ubuntu, Mint**: installa `calendario-X.Y.Z-linux.deb` (Debian, Ubuntu, Mint: compare nel menu) oppure estrai il
  `.tar.gz` e avvia `Calendario/bin/Calendario`. Le notifiche usano `notify-send` (pacchetto `libnotify-bin`).
- Avviata dal terminale si chiude con lui: meglio dal menu. Con "Avvia con il computer" parte all'accesso e resta
  nell'area di notifica (su GNOME serve l'estensione AppIndicator, altrimenti chiudendo la finestra si chiude).

## Build
`./build.sh calendario` crea il jar (`java -jar build/calendario/calendario-X.Y.Z.jar`, JDK 17+).
`PACKAGE=1 ./build.sh calendario` crea anche il pacchetto con Java incluso per il sistema su cui gira (jpackage;
per il .deb serve `fakeroot`). GitHub Actions lo fa per Windows e Linux a ogni push e pubblica la release quando
cambia `VERSION_NAME` in `app.conf`.

## Aspetto
Kit "Arancio UI" delle mie app: grigi caldi, JetBrains Mono (licenza OFL in `JetBrainsMono-OFL.txt`), pulsanti con
raggio 4, card con raggio 12 e bordo, icone a tratto 1.75, colore principale a scelta fra sei nelle impostazioni.
Icona secondo "Icone delle app": calendario a tratto arancio su fondo #1C1B1A, generata dal codice (`--icone`).
