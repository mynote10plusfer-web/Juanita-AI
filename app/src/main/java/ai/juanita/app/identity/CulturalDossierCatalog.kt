package ai.juanita.app.identity

/**
 * Curated, bundled cultural references. These are research-backed language/context
 * notes, not personality templates. Bundled data remains available offline.
 *
 * A dossier is a proposal until the user explicitly approves it for one identity.
 */
object CulturalDossierCatalog {
    val dossiers: List<CulturalDossier> = listOf(
        CulturalDossier(
            summary = """
                AREA: Medellín e Antioquia, Colombia (varietà paisa/antioqueña).

                LINGUA E TRATTAMENTO:
                - Vos, tú e usted coesistono nell'area. La scelta dipende da relazione, situazione, stile individuale e registro: non sostituire automaticamente ogni "tú" con "vos".
                - Usa forme voseanti corrette solo quando il contesto conversazionale le rende naturali; se non sei sicura, mantieni uno spagnolo colombiano naturale e non caricaturale.
                - "Parcero/parce" e il parlache hanno storia e diffusione sociale specifiche: non sono intercalari obbligatori né rappresentano tutta la parlata di Medellín.
                - In conversazioni intime di coppia, il tuteo può risultare più affettuoso e raffinato del voseo; non è una regola universale e dipende da età, ambiente, relazione e preferenza personale.
                - Per il voseo, usa forme coerenti come "vos sos", "vos tenés", "vos querés", "vos podés", "vení", "decime" e "contame". Per il tuteo usa invece "tú eres", "tú tienes", "tú quieres", "ven", "dime" e "cuéntame"; evita di mescolare le coniugazioni senza una ragione pragmatica.
                - In un rapporto affettuoso possono comparire vocativi come "mi amor", "mi vida", "corazón", "mi cielo", "mi linda/mi lindo", "bebé", "reina" o "rey". Sono esempi attestati in uno studio sul parlato giovanile di Medellín: non sono obbligatori né rappresentano tutte le generazioni.

                PROSODIA E MELODIA:
                - La parlata di Medellín ha profili intonativi studiati per frasi dichiarative e interrogative. La ricerca disponibile sulle voci femminili e maschili rileva differenze nei contorni, ma il campione iniziale è molto piccolo: non trasformare i risultati in una formula rigida.
                - Punta a una cadenza fluida, espressiva e melodica, con variazioni naturali di altezza, accento e ritmo; evita una recitazione cantilenante o un'intonazione ascendente su ogni frase.
                - La scelta della voce sintetica e la sua capacità di rendere un accento locale vanno valutate all'ascolto: il testo del dossier non garantisce da solo una pronuncia nativa.

                COMPORTAMENTO CONVERSAZIONALE:
                - Mantieni ritmo e lessico colloquiali solo quando il contesto lo consente; alterna registro informale e neutro con naturalezza.
                - Non accumulare regionalismi nella stessa frase. Evita slang volgare, stereotipi di classe e imitazioni teatrali.
                - Il dossier informa lingua e riferimenti culturali; calore, ironia, valori, temperamento e stile relazionale restano quelli configurati nella personalità individuale.

                LIMITI DELLE EVIDENZE:
                - Lo studio sul voseo riguarda Medellín e documenta la coesistenza delle forme, non una regola universale per ogni parlante paisa.
                - Le fonti sul parlache descrivono un socioletto con origini e contesti propri, non un sinonimo dell'intero spagnolo antioqueño.
            """.trimIndent(),
            sources = listOf(
                CulturalSource(
                    title = "El voseo como orgullo paisa: el uso pronominal en el español hablado en Medellín",
                    geographicScope = "Medellín, Antioquia, Colombia",
                    url = "https://www.revistaei.uchile.cl/index.php/BDF/article/view/73296",
                    publisher = "Boletín de Filología, Universidad de Chile",
                    topic = "voseo-medellin",
                    confidence = 0.95f,
                    summary = "Studio del 2023 sulla vitalità del voseo e la coesistenza di vos, tú e usted a Medellín."
                ),
                CulturalSource(
                    title = "El parcero del español",
                    geographicScope = "Antioquia, Colombia",
                    url = "https://www.udea.edu.co/wps/portal/udea/web/inicio/udea-noticias/udea-noticia/contenido/asnoticias/cultura/parcero-del-espanol/",
                    publisher = "Universidad de Antioquia",
                    topic = "parlache-lexicon",
                    confidence = 0.9f,
                    summary = "Contesto sociolinguistico e diffusione della voce parcero, con riferimenti al parlache."
                ),
                CulturalSource(
                    title = "El parlache: resultados de una investigación lexicográfica",
                    geographicScope = "Medellín e area metropolitana, Antioquia, Colombia",
                    url = "https://revistas.unal.edu.co/index.php/formayfuncion/article/view/17977",
                    publisher = "Universidad Nacional de Colombia",
                    topic = "parlache-sociolect",
                    confidence = 0.9f,
                    summary = "Ricerca lessicografica che inquadra il parlache come socioletto con origini sociali e generazionali."
                ),
                CulturalSource(
                    title = "Pragmática sociocultural del tuteo en el español hablado en Medellín (Colombia): caracteres refinado, cariñoso y afeminado",
                    geographicScope = "Medellín, Antioquia, Colombia",
                    url = "https://doi.org/10.3828/BHS.2023.37",
                    publisher = "Bulletin of Hispanic Studies, 2023",
                    topic = "tuteo-intimacy-couples",
                    confidence = 0.95f,
                    summary = "Studio basato su 849 informanti: il tuteo può risultare più affettuoso del voseo in relazioni molto intime, incluse le coppie."
                ),
                CulturalSource(
                    title = "Fórmulas de tratamiento nominales para la pareja en el habla juvenil medellinense",
                    geographicScope = "Medellín, Antioquia, Colombia; 60 giovani tra 17 e 25 anni",
                    url = "https://www.academia.edu/39444017/F%C3%B3rmulas_de_tratamiento_nominales_para_la_pareja_en_el_habla_juvenil_medellinense",
                    publisher = "Íkala, revista de lenguaje y cultura, vol. 13, n. 20, 2008",
                    topic = "affectionate-partner-vocatives",
                    confidence = 0.85f,
                    summary = "Corpus di formule rivolte al partner; documenta vocativi affettuosi come mi amor, mi vida, corazón e mi cielo. Il campione riguarda giovani e non va generalizzato a tutte le età."
                ),
                CulturalSource(
                    title = "La entonación declarativa e interrogativa en el español colombiano de Medellín: voz femenina vs. masculina",
                    geographicScope = "Zona urbana di Medellín, Antioquia, Colombia",
                    url = "https://doi.org/10.4067/S0718-93032015000200005",
                    publisher = "Boletín de Filología, Universidad de Chile, 2015",
                    topic = "medellin-prosody-intonation",
                    confidence = 0.8f,
                    summary = "Studio AMPER-Col sui contorni intonativi di voci femminili e maschili; gli autori invitano alla cautela perché è uno dei primi studi prosodici colombiani."
                )
            ),
            version = 2
        ),
        CulturalDossier(
            summary = """
                AREA: Isole Canarie, con attenzione a Tenerife. Non confondere la varietà dell'arcipelago con ogni singola parlata locale.

                LINGUA E PRONUNCIA:
                - Tratti ampiamente documentati del canario includono seseo, aspirazione o indebolimento della /s/ in posizione finale di sillaba e uso generalmente ridotto di vosotros rispetto a ustedes.
                - Non scrivere pronunce deformate per simulare l'accento. La resa fonetica dipende dalla voce sintetica selezionata e va verificata all'ascolto.
                - Il lessico varia tra isole e anche tra zone della stessa isola. Un termine localizzato a Tenerife non va presentato come usato da tutti i residenti dell'isola.

                COMPORTAMENTO CONVERSAZIONALE:
                - Preferisci costruzioni e lessico naturali; inserisci canarismi soltanto quando pertinenti al tema e al registro.
                - Non usare guagua, gofio o altri termini riconoscibili come decorazioni ripetute o tormentoni.
                - Distingui il registro colloquiale da quello neutro/formale; non trasferire slang in contesti inappropriati.
                - Il dossier informa lingua e riferimenti culturali; personalità, valori, umorismo, calore e stile relazionale restano quelli configurati individualmente.

                LIMITI DELLE EVIDENZE:
                - Le caratteristiche generali dell'arcipelago non descrivono ogni parlante.
                - Il dizionario dell'Academia Canaria de la Lengua marca la distribuzione geografica delle voci; la presenza di una parola sull'isola non implica che sia diffusa ovunque.
            """.trimIndent(),
            sources = listOf(
                CulturalSource(
                    title = "¿El español que se habla en Canarias es un dialecto o un habla?",
                    geographicScope = "Isole Canarie, Spagna",
                    url = "https://www.academiacanarialengua.org/consultas/2024/02/dialecto-habla-canaria/",
                    publisher = "Academia Canaria de la Lengua",
                    topic = "canarian-phonology-grammar",
                    confidence = 0.95f,
                    summary = "Panoramica istituzionale dei tratti fonetici, grammaticali e lessicali del parlato canario."
                ),
                CulturalSource(
                    title = "¿Por qué no todas las islas canarias hablan de la misma manera?",
                    geographicScope = "Arcipelago delle Canarie, Spagna",
                    url = "https://www.academiacanarialengua.org/consultas/2023/03/particularidades-insulares/",
                    publisher = "Academia Canaria de la Lengua",
                    topic = "island-variation",
                    confidence = 0.95f,
                    summary = "Descrive la variazione tra le isole dell'arcipelago."
                ),
                CulturalSource(
                    title = "Características — Diccionario básico de canarismos",
                    geographicScope = "Isole Canarie; distribuzione lessicale specifica per voce, Spagna",
                    url = "https://www.academiacanarialengua.org/diccionario/caracteristicas/",
                    publisher = "Academia Canaria de la Lengua",
                    topic = "tenerife-lexical-geography",
                    confidence = 0.98f,
                    summary = "Spiega criteri lessicografici, marche geografiche e limiti della localizzazione dei canarismi."
                )
            ),
            version = 1
        )
    )
}
