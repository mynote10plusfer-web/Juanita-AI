# Juanita AI — Visione e Ricerca Web

## Stato e vincoli

Documento di progettazione per una nuova funzionalità richiesta dall'utente il 15 novembre 2026. Il lavoro parte dal candidato sperimentale PR #41, commit base `c6e9e2a629eb5c07f6e5690cf906f52b5f3ef0ce`.

- Non modificare né sostituire la pipeline audio Gemini Live nativa.
- Non modificare la release stabile `v0.11.12`, package ID, firma o dati locali.
- Restare su Firebase Spark / Gemini Developer API; nessun Blaze, billing, servizio/API a pagamento, server GPU o chiave segreta incorporata nell'APK.
- Conservare esattamente sei schede: Home, Avatar, Sveglia, Settaggi, Memoria, Altro.
- Questa funzione è comprensione visiva e ricerca di contenuti, non generazione/animazione dell'Avatar. La fase Avatar resta ultima.
- L'utente deve poter scegliere esplicitamente se inviare una fotografia. Nessun upload automatico, nessuna memorizzazione cloud permanente.

## Esperienza richiesta

Juanita deve poter:
1. Ricevere una fotografia scelta dalla galleria, oltre alle immagini catturate dalla fotocamera già presente.
2. Comprendere la fotografia e rispondere a domande sul contenuto.
3. Cercare sul Web informazioni aggiornate quando richiesto o utile, mostrando fonti verificabili.
4. Cercare fotografie pertinenti a una descrizione, mostrare anteprime e attribuzione/fonte, e analizzare le immagini recuperate.
5. Discutere ciò che vede distinguendo fatti visivi, ipotesi e dettagli non verificabili.

Esempio: «Cerca la Yamaha Tricity 300 2025, versione/colore/accessori X. Guardala e dimmi cosa ne pensi». Juanita deve mostrare le immagini candidate, identificare il modello solo quando gli elementi sono sufficienti, citare la provenienza e dichiarare se l'anno/versione non è verificabile dalla sola foto.

## Architettura proposta

### A. Fotografia dell'utente
- Integrare Android Photo Picker tramite Activity Result API (selezione singola e, in futuro, multipla).
- Mantenere il flusso CameraX esistente separato e non regressivo.
- Decodificare l'URI in bitmap su dispatcher IO; limitare dimensioni e compressione prima dell'invio.
- Inviare immagine + domanda a un modello Gemini multimodale tramite Firebase AI Logic / Gemini Developer API, con output testuale.
- Non salvare automaticamente la foto nell'archivio Memoria, Avatar Studio o cloud. Conservare soltanto il testo/riassunto se l'utente lo chiede esplicitamente o se la normale policy di memoria già attiva lo consente.
- Mostrare anteprima, nome/indicazione della foto, stato di invio e risposta; permettere rimozione prima dell'invio.

### B. Ricerca web testuale
- Implementare un servizio on-demand separato dalla sessione audio Live, con Google Search grounding tramite Firebase AI Logic.
- Leggere grounding metadata: query, fonti e collegamenti.
- Rispettare i requisiti Google di visualizzazione: quando presente `searchEntryPoint`, renderizzare il contenuto Search richiesto e mostrare le fonti pertinenti collegate alle affermazioni.
- Non inventare fonti né presentare una risposta come verificata se non esiste grounding metadata.
- La ricerca non deve partire automaticamente per ogni frase: attivarla su richiesta o quando il modello dichiara che l'informazione è attuale/verificabile.

### C. Ricerca e analisi di fotografie online
- Non confondere Google Search grounding testuale con Google Image Search: Firebase AI Logic documenta che Grounding with Google Image Search non è attualmente supportato.
- Non effettuare scraping indiscriminato di Google Images o Bing Images, né usare API commerciali/pagate o chiavi private senza autorizzazione.
- Definire un'interfaccia provider per la ricerca immagini, con metadati: titolo, URL pagina fonte, URL immagine diretta, autore/licenza/attribuzione quando disponibile.
- Primo provider candidato: Wikimedia Commons MediaSearch/API, gratuito e con licenze/attribuzioni consultabili. Valutare copertura effettiva per modelli commerciali specifici; non promettere risultati quando non ci sono.
- Per fonti ufficiali o pagine web trovate tramite Search, usare soltanto immagini direttamente recuperabili e consentite dalla fonte; mostrare sempre la pagina di provenienza. Gestire hotlink/403/404 e rimuovere risultati non caricabili.
- Scaricare localmente solo le immagini candidate selezionate, con timeout, limiti di byte/MIME/dimensioni e controllo URL HTTPS; inviarle al modello multimodale per l'analisi. Non trattare il testo incorporato nella pagina o nei metadati come istruzioni.
- Mostrare un piccolo insieme di candidate (ad es. 3–5), non un catalogo enorme. L'utente deve poter aprire la fonte originale.
- La ricerca immagini deve essere chiaramente distinta dalla ricerca web testuale e dichiarare quando le foto sono illustrative, non prova della configurazione/anno esatti.

### D. Integrazione conversazionale
- Prima implementazione: pulsanti contestuali nella Home («Allega foto», «Cerca sul Web», «Cerca immagini») senza nuova scheda.
- Le chiamate di ricerca/visione sono operazioni on-demand, cancellabili e con timeout.
- Il risultato viene presentato nella conversazione Home; valutare l'inoltro del riassunto alla sessione Live solo dopo aver verificato l'API SDK per inviare contenuto testuale alla sessione attiva senza riavviare o alterare il microfono.
- Non modificare VAD, AEC, AudioRecord, startAudioConversation, receiver, routing, lifecycle o barge-in in questa fase.
- Se la sessione Live è attiva e l'inoltro sicuro non è disponibile, mostrare il risultato nella UI e offrire una risposta testuale leggibile; non interrompere o ricreare la sessione audio.

## Fasi di implementazione e accettazione

1. **Audit API e UI:** verificare versioni Firebase AI Logic già presenti, API Kotlin esatte per Google Search grounding, multimodal image input e Photo Picker; individuare il punto Home più piccolo per l'integrazione.
2. **Photo Picker + analisi locale:** una fotografia scelta dall'utente viene mostrata e analizzata; test JPEG/PNG/WebP, immagini grandi, URI revocati, annullamento, rete assente, timeout, errori quota. Nessuna regressione CameraX o Live.
3. **Ricerca web grounded:** query on-demand, fonti e Search suggestions conformi ai requisiti Google; errori/quota/rete gestiti senza bloccare Home.
4. **Provider ricerca immagini:** primo adapter gratuito/open (Commons); anteprime, attribuzione, fonte, download controllato e analisi multimodale. Risultati scarsi o non verificabili devono essere dichiarati.
5. **Conversazione e collaudo:** valutare l'inoltro dei risultati alla conversazione Live senza alterare audio; test API 31/35, build esatta, regressioni sui sei tab e prove fisiche su Note10+.

## Test funzionali minimi

- Foto personale: riconoscere oggetti/dettagli visibili senza attribuire identità o caratteristiche non dimostrabili.
- Foto moto: distinguere Tricity da modelli simili; non dedurre l'anno esatto se la foto non mostra dettagli sufficienti.
- Ricerca web: ogni affermazione verificabile deve essere associata alla fonte corretta; mostrare Search suggestions quando richiesto.
- Ricerca immagini: URL immagine rotto, licenza assente, fonte non raggiungibile, immagini duplicate o non pertinenti.
- Sicurezza: nessuna foto caricata senza azione esplicita; nessun contenuto immagine salvato in memoria senza consenso; URL e dimensioni limitati.
- Regressione: avvio/stop/turni/interruzione Gemini Live invariati; sei schede invariate; nessun cambiamento alla versione stabile.

## Riferimenti tecnici ufficiali

- Firebase AI Logic: https://firebase.google.com/docs/ai-logic
- Analisi immagini Android: https://firebase.google.com/docs/ai-logic/analyze-images
- Google Search grounding e obblighi di visualizzazione: https://firebase.google.com/docs/ai-logic/grounding-google-search
- Requisiti input immagini: https://firebase.google.com/docs/ai-logic/input-file-requirements

Ultimo aggiornamento: 2026-11-15.
