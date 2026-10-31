# Sveglia
Sveglie multiple con aspetto ispirato all'orologio Samsung. Ogni sveglia può suonare con la suoneria di
sistema oppure avviare un'app musicale.

## Come suona
1. All'orario la notifica a schermo intero accende lo schermo e apre la sveglia, anche col telefono bloccato.
2. Con un'app musicale la sveglia imposta il volume musica (Impostazioni ⋮, 0 = lascia quello attuale) e apre l'app:
   - **WebRadioDroid**: usa la sua azione "avvia ultima stazione", la radio parte da sola;
   - **Qobuz e le altre app**: l'app viene aperta e dopo 4 secondi riceve il tasto Play multimediale,
     che riprende l'ultimo brano o playlist.
3. Se dopo 20 secondi non suona niente (app che non riparte, nessuna rete, telefono non ancora sbloccato dopo un
   riavvio) parte la suoneria di sistema: la sveglia suona comunque.
4. **Posticipa** e **Ferma** sono sulla schermata e nella notifica; mettono in pausa la musica e ripristinano il
   volume. Dopo 10 minuti la sveglia si ferma da sola.

## Installazione (Samsung S23/S26 Ultra)
1. Installa l'APK consentendo l'installazione da origini sconosciute.
2. Apri Sveglia e concedi le notifiche. Se compare un avviso rosso, toccalo e attiva l'opzione indicata
   (notifiche a schermo intero, "Sveglie e promemoria").
3. Crea una sveglia, scegli l'app in "Suono sveglia" e usa **Salva e prova tra 10 secondi** bloccando il telefono.

## Note
- Le sveglie sono salvate solo sul telefono (niente backup cloud), in uno spazio leggibile anche prima del
  primo sblocco dopo un riavvio, così suonano comunque.
- L'app non usa internet e chiede solo i permessi per sveglie, notifiche e riproduzione.
