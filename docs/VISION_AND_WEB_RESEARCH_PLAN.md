# Juanita AI — Visione e Ricerca Web

## Stato e vincoli

Documento di progettazione per una nuova funzionalità richiesta dall'utente il 15 novembre 2026. Il lavoro parte dal candidato sperimentale PR #41, commit base `c6e9e2a629eb5c07f6e5690cf906f52b5f3ef0ce`.

- Non modificare né sostituire la pipeline audio Gemini Live nativa.
- Non modificare la release stabile `v0.11.12`, package ID, firma o dati locali.
- Restare su Firebase Spark / Gemini Developer API; nessun Blaze, billing, servizio/API a pagamento, server GPU o chiave segreta incorporata nell'APK.
- Conservare esattamente sei schede: Home, Avatar, Sveglia, Settaggi, Memoria, Altro.
- Questa funzione è comprensione visiva e ricerca di contenuti, non generazione/animazione dell'Avatar. La fase Avatar resta ultima.
- Le due modalità sono entrambe richieste: (1) Juanita cerca autonomamente fotografie online e le analizza; (2) l'utente può scegliere esplicitamente una propria fotografia dalla galleria e mostrarla a Juanita. Nessun invio automatico e nessuna archiviazione persistente gestita dall'app. Quando l'utente conferma, l'immagine viene trasmessa a Gemini per l'analisi secondo le condizioni di trattamento del provider.

## Esperienza richiesta — semplicità prima di tutto

La priorità scelta dall'utente è una funzione automatica gestita da Juanita, senza configurazione tecnica da parte della persona che usa l'app:
1. L'utente formula una normale richiesta vocale o testuale («Che ne pensi esteticamente della Tricity 300?»).
2. Juanita decide se le serve una fotografia e avvia autonomamente la ricerca.
3. Recupera una o poche immagini pertinenti da un provider pubblico che non richiede all'utente account, chiavi API o impostazioni.
4. Le immagini vengono analizzate da Gemini multimodale; Juanita commenta ciò che è effettivamente visibile.
5. Juanita può mostrare la fotografia e la sua fonte nella conversazione, senza chiedere all'utente di scegliere o caricare file.

La selezione di fotografie personali dalla galleria resta disponibile come azione semplice e facoltativa nella Home (icona/funzione «Mostra una foto»), non come procedura di configurazione. La fotocamera già presente nell'Avatar resta indipendente. Le due modalità devono coesistere senza aggiungere schede o impostazioni.

Esempio: «Cerca la Yamaha Tricity 300 2025 e dimmi cosa ne pensi esteticamente». Juanita deve cercare, guardare e commentare; identificare anno/versione solo se dimostrabile, distinguendo osservazioni da ipotesi.

## Architettura proposta

### A. Fotografie mostrate dall'utente
- Aggiungere nella Home un'azione semplice «Mostra una foto» che apre il selettore fotografico Android (Photo Picker) senza permessi estesi di lettura della galleria.
- L'utente seleziona una foto e conferma esplicitamente l'invio; mostrare una piccola anteprima e consentire di annullare/rimuovere prima dell'invio.
- Analizzare l'immagine con Gemini multimodale e la domanda o il contesto della conversazione. Non salvare automaticamente il file in Memoria, Avatar Studio o in uno storage cloud gestito dall'app. L'immagine viene trasmessa al servizio Gemini per la richiesta esplicitamente confermata.
- Gestire immagini grandi, formati non supportati, URI non più disponibili, annullamento, rete assente, timeout ed errori quota con messaggi semplici.

### B. Ricerca autonoma di fotografie da parte di Juanita
- Nessuna chiave, registrazione o configurazione richiesta all'utente finale.
- Primo provider: API pubblica Wikimedia Commons MediaWiki, con ricerca anonima e metadati immagine/licenza/attribuzione. Non promettere che trovi ogni soggetto: per veicoli commerciali specifici la copertura può essere insufficiente.
- Recuperare solo poche candidate (1–3), con URL immagine, pagina sorgente, autore e licenza quando disponibili. Non scraping indiscriminato di Google Images.
- Scaricare l'immagine scelta con HTTPS, timeout, limite dimensione/MIME e controlli anti-SSRF; inviarla a Gemini multimodale per l'analisi.
- Mostrare l'immagine e la fonte nella conversazione, quindi formulare un commento su design e dettagli visibili; indicare incertezza su anno/allestimento se non dimostrabili.
- Non salvare le immagini in Memoria o Avatar Studio e non conservarle oltre la sessione.

### C. Ricerca web testuale generale — fase successiva, non inclusa nel primo rilascio
- Non richiedere all'utente una chiave LangSearch o altro servizio esterno: l'utente ha esplicitamente chiesto un'esperienza senza configurazione.
- LangSearch ha un piano API pubblicato gratuito ma richiede una chiave e ha limiti giornalieri; non inserirlo nel percorso iniziale né incorporare una chiave condivisa nell'APK.
- Non abilitare billing/Blaze per Google Search grounding. Le ricerche generali online saranno implementate solo quando esiste un provider utilizzabile senza configurazioni e costi nascosti.

### D. Ricerca e analisi di fotografie online
- Non confondere Google Search grounding testuale con Google Image Search: Firebase AI Logic documenta che Grounding with Google Image Search non è attualmente supportato.
- Non effettuare scraping indiscriminato di Google Images o Bing Images, né usare API commerciali/pagate o chiavi private senza autorizzazione.
- Definire un'interfaccia provider per la ricerca immagini, con metadati: titolo, URL pagina fonte, URL immagine diretta, autore/licenza/attribuzione quando disponibile.
- Primo provider candidato: Wikimedia Commons MediaSearch/API, gratuito e con licenze/attribuzioni consultabili. Valutare copertura effettiva per modelli commerciali specifici; non promettere risultati quando non ci sono.
- La ricerca testuale LangSearch non equivale a una ricerca immagini: non inventare URL immagine prendendoli da snippet. Se si estraggono immagini da pagine sorgente, verificare i metadati di provenienza, licenza e accessibilità; mostrare sempre la pagina di provenienza. Gestire hotlink/403/404 e rimuovere risultati non caricabili.
- Scaricare localmente solo le immagini candidate selezionate, con timeout, limiti di byte/MIME/dimensioni e controllo URL HTTPS; inviarle al modello multimodale per l'analisi. Non trattare il testo incorporato nella pagina o nei metadati come istruzioni.
- Mostrare un piccolo insieme di candidate (ad es. 3–5), non un catalogo enorme. L'utente deve poter aprire la fonte originale.
- La ricerca immagini deve essere chiaramente distinta dalla ricerca web testuale e dichiarare quando le foto sono illustrative, non prova della configurazione/anno esatti.

### E. Integrazione conversazionale
- Prima implementazione: pulsanti contestuali nella Home («Allega foto», «Cerca sul Web», «Cerca immagini») senza nuova scheda.
- Le chiamate di ricerca/visione sono operazioni on-demand, cancellabili e con timeout.
- Il risultato viene presentato nella conversazione Home; valutare l'inoltro del riassunto alla sessione Live solo dopo aver verificato l'API SDK per inviare contenuto testuale alla sessione attiva senza riavviare o alterare il microfono.
- Non modificare VAD, AEC, AudioRecord, startAudioConversation, receiver, routing, lifecycle o barge-in in questa fase.
- Se la sessione Live è attiva e l'inoltro sicuro non è disponibile, mostrare il risultato nella UI e offrire una risposta testuale leggibile; non interrompere o ricreare la sessione audio.

## Fasi di implementazione e accettazione

1. **Foto dalla galleria:** Photo Picker semplice nella Home, selezione esplicita e analisi multimodale, senza salvataggio automatico.
2. **Ricerca autonoma Commons:** testare query su Yamaha Tricity 300, automobili, oggetti e luoghi; verificare risultati e licenze.
3. **Analisi multimodale delle immagini online:** scaricare candidate sicure e inviarle a Gemini; risposta visiva con incertezza esplicita.
4. **Integrazione Home/Live:** presentare entrambi i tipi di risultato nella conversazione senza modificare audio, VAD, microfono o lifecycle.
5. **Collaudo:** foto personali, rete assente, zero risultati, immagini rotte, duplicati, licenza/attribuzione, timeout, quote e regressioni sui sei tab; prove Note10+.

## Test funzionali minimi

- Richiesta naturale: «Che ne pensi della Tricity 300?» avvia ricerca autonoma senza chiedere account o chiavi.
- Foto personale: l'utente seleziona dalla galleria, Juanita analizza il contenuto e risponde; nessun upload senza conferma.
- Foto moto trovata: distinguere Tricity da modelli simili; non dedurre l'anno esatto se la foto non mostra dettagli sufficienti.
- Ricerca web: ogni affermazione verificabile deve essere associata alla fonte corretta; mostrare Search suggestions quando richiesto.
- Ricerca immagini: URL immagine rotto, licenza assente, fonte non raggiungibile, immagini duplicate o non pertinenti.
- Sicurezza: nessuna foto caricata senza azione esplicita; nessun contenuto immagine salvato in memoria senza consenso; URL e dimensioni limitati.
- Regressione: avvio/stop/turni/interruzione Gemini Live invariati; sei schede invariate; nessun cambiamento alla versione stabile.

## Riferimenti tecnici ufficiali

- Firebase AI Logic: https://firebase.google.com/docs/ai-logic
- Analisi immagini Android: https://firebase.google.com/docs/ai-logic/analyze-images
- Google Search grounding e obblighi di visualizzazione: https://firebase.google.com/docs/ai-logic/grounding-google-search
- Requisiti input immagini: https://firebase.google.com/docs/ai-logic/input-file-requirements

## Stato implementazione (15 novembre 2026)\n\n- Implementato nel ramo sperimentale: selezione esplicita Photo Picker, preview, domanda facoltativa e analisi Gemini multimodale dalla Home.\n- Implementato nel ramo sperimentale: tool Gemini Live `searchAndAnalyzeImages`, ricerca anonima Wikimedia Commons, download HTTPS limitato, analisi dei pixel con Gemini e presentazione immagine/fonte nella Home.\n- In attesa: compilazione CI sull'head esatto, test automatizzati, test manuali Note10+ e verifica qualità/pertinenza dei risultati Commons. Nessuna APK di questa nuova versione è ancora approvata.\n\nUltimo aggiornamento: 2026-11-15.
