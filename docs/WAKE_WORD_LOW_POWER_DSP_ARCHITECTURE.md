# Juanita AI — Architettura wake word a basso consumo e accesso DSP

**Stato: decisione architetturale e piano di implementazione.** Questo documento non dichiara ancora attivo il DSP: l'API pubblica Android disponibile oggi non garantisce alle normali app di terze parti l'accesso al Sound Trigger HAL.

## Obiettivo non negoziabile

L'ascolto in attesa della wake word deve avere il minor consumo possibile su Android, indipendentemente dal produttore. Il consumo va misurato su dispositivi reali; non si promette un valore universale né si confonde il consumo in standby con quello di Gemini Live durante una conversazione.

## Evidenze e vincoli Android

- Il Sound Trigger HAL esegue il rilevamento della hotword in un percorso hardware/vendor a basso consumo e invia un evento discreto all'app. È il percorso preferibile rispetto a una cattura PCM continua con AudioRecord.
- La documentazione AOSP descrive modelli, formati e semantica del HAL come contratto vendor-specific e indica che le implementazioni Sound Trigger sono normalmente fornite dal produttore.
- Le API framework SoundTriggerManager e i permessi correlati sono marcati @SystemApi/@hide o signature/privileged; non sono una base legittima per un'app Play/side-load indipendente. Non usare reflection, API nascoste, permessi privilegiati dichiarati nel manifest o dipendenze da root.
- La decisione UE sull'interoperabilità Android prevede accesso per hotword DSP di terze parti tramite modelli di terze parti entro 1 gennaio 2027; la pagina della Commissione indica l'implementazione generale delle misure entro 1 agosto 2027 (Android 18). L'accesso va integrato soltanto quando Google pubblica API/documentazione e il dispositivo espone capacità compatibili. Non anticipare l'accesso né presumere che ogni telefono abbia il DSP richiesto.

## Architettura target

1. **WakeWordBackend**: contratto interno indipendente dal produttore, con start, stop, close, stato, origine del trigger e diagnostica.
2. **Backend DSP ufficiale Android**: futuro adattatore basato esclusivamente sulle API pubbliche/interoperabili documentate e sul modello supportato dal dispositivo. Deve interrogare capacità e consenso; se non disponibile, fallisce in modo esplicito e reversibile.
3. **Backend OEM opzionali**: solo integrazioni pubbliche/documentate e legalmente distribuibili. Nessuna dipendenza obbligatoria da Samsung, Qualcomm o altro produttore.
4. **Backend Sherpa-ONNX**: fallback attuale. Mantiene opt-in esplicito, AudioRecord unico, modello locale verificato, nessun invio o salvataggio dell'audio e handoff esclusivo con Gemini Live.
5. **WakeWordBackendSelector**: seleziona DSP solo dopo verifica runtime reale di API, modello, lingua, autorizzazione e capacità; altrimenti Sherpa. Mai dichiarare DSP attivo sulla sola presenza di un chipset o di una classe di dispositivo.
6. **Policy energetica**: dopo la sveglia/conversazione, chiudere Gemini Live con timeout e tornare al backend leggero soltanto se l'utente ha attivato l'ascolto; altrimenti terminare il servizio e rilasciare tutte le risorse. Nessun riavvio del microfono dopo process death senza consenso.
7. **Diagnostica**: mostrare backend realmente attivo, motivo del fallback, durata di ascolto, CPU del worker, wake lock, temperatura, batteria iniziale/finale e sessioni Live. Nessun polling frequente, registrazione audio o telemetria remota.

## Ottimizzazioni immediate del fallback software

- Mantenere un solo thread di cattura e un solo proprietario del microfono.
- Riutilizzare buffer e stream; evitare allocazioni, log frequenti e I/O nel loop audio.
- Mantenere un solo thread modello e priorità audio; verificare che il decoder non lavori quando non ci sono frame.
- Profilare CPU e consumo per sessione su più dispositivi prima di modificare frame, frequenza o soglie.
- Non introdurre pause arbitrarie del microfono o duty cycling non validato: potrebbero perdere la wake word e peggiorare l'esperienza. Non ridurre la frequenza di campionamento richiesta dal modello.
- Valutare VAD/gating soltanto con test di accuratezza, falsi positivi/negativi e consumo; il gating non deve richiedere un secondo riconoscitore sempre attivo.

## Test e criteri di accettazione

Matrice minima: Samsung Android 12/15, Google Pixel, almeno un dispositivo Qualcomm non Samsung e almeno un dispositivo MediaTek; aggiungere versioni Android e produttori reali disponibili. Per ogni dispositivo registrare modello, build Android, backend, supporto API, durata, batteria iniziale/finale, temperatura, CPU media, falsi trigger e wake word mancate.

Scenari: schermo acceso/spento, risparmio energetico, standby per 1/4/8 ore, rete assente, chiamata/audio Bluetooth, avvio/stop, allarme, avvio Gemini Live, perdita microfono e riavvio processo.

Accettazione:
- DSP: deve essere dimostrato dal backend effettivamente attivo, non dedotto dall'hardware.
- Fallback: nessuna regressione di riconoscimento; CPU e consumo misurati e confrontati con baseline.
- Handoff: mai due acquisizioni microfono contemporanee; stop confermato prima del cambio backend.
- Privacy: nessun audio persistito o inviato dal detector offline.
- Costi: nessun SDK/API a pagamento, billing, Firebase Blaze o segreti nell'APK.
- Pubblicazione: nessun cambiamento a main, firma, package ID o dati utente; PR #58 resta draft e non viene fusa senza approvazione.

## Sequenza operativa

A. Audit della cattura Sherpa e della diagnostica energetica già presenti; fissare una baseline ripetibile.
B. Introdurre il contratto backend e il selettore senza cambiare il comportamento runtime attuale.
C. Integrare il DSP solo quando esiste un'API pubblica supportata; aggiungere test unitari e strumentali per selezione/fallback.
D. Ottimizzare Sherpa in base a misure reali, non a ipotesi.
E. Eseguire la matrice multi-produttore e pubblicare risultati comparativi.

## Fonti ufficiali

- AOSP Sound Trigger: https://source.android.com/docs/core/audio/sound-trigger
- Android framework SoundTriggerManager (SystemApi/signature restricted): https://android.googlesource.com/platform/frameworks/base/+/6e96251d92f29e164e5cf99f1690b6e1353f1ae4/media/java/android/media/soundtrigger/SoundTriggerManager.java
- Android manifest permission CAPTURE_AUDIO_HOTWORD (signature/privileged): https://android.googlesource.com/platform/frameworks/base/+/c60281bd2144/core/res/AndroidManifest.xml
- Commissione europea, misure di interoperabilità Android: https://digital-markets-act.ec.europa.eu/businesses-portal/interoperability/alphabet-specification-proceedings-interoperability-ai-services_en
