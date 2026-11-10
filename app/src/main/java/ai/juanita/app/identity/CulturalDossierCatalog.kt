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
                    url = "https://www.revistapuntogenero.uchile.cl/index.php/BDF/article/view/73296",
                    publisher = "Boletín de Filología, Universidad de Chile",
                    topic = "voseo-medellin",
                    confidence = 0.95f,
                    summary = "Studio del 2023 sulla vitalità del voseo e la coesistenza di vos, tú e usted a Medellín."
                ),
                CulturalSource(
                    title = "El parcero del español",
                    url = "https://www.udea.edu.co/wps/portal/udea/web/inicio/udea-noticias/udea-noticia/contenido/asnoticias/cultura/parcero-del-espanol/",
                    publisher = "Universidad de Antioquia",
                    topic = "parlache-lexicon",
                    confidence = 0.9f,
                    summary = "Contesto sociolinguistico e diffusione della voce parcero, con riferimenti al parlache."
                ),
                CulturalSource(
                    title = "El parlache: resultados de una investigación lexicográfica",
                    url = "https://revistas.unal.edu.co/index.php/formayfuncion/article/view/17977",
                    publisher = "Universidad Nacional de Colombia",
                    topic = "parlache-sociolect",
                    confidence = 0.9f,
                    summary = "Ricerca lessicografica che inquadra il parlache come socioletto con origini sociali e generazionali."
                )
            ),
            version = 1
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
                    url = "https://www.academiacanarialengua.org/consultas/2024/02/dialecto-habla-canaria/",
                    publisher = "Academia Canaria de la Lengua",
                    topic = "canarian-phonology-grammar",
                    confidence = 0.95f,
                    summary = "Panoramica istituzionale dei tratti fonetici, grammaticali e lessicali del parlato canario."
                ),
                CulturalSource(
                    title = "¿Por qué no todas las islas canarias hablan de la misma manera?",
                    url = "https://www.academiacanarialengua.org/consultas/2023/03/particularidades-insulares/",
                    publisher = "Academia Canaria de la Lengua",
                    topic = "island-variation",
                    confidence = 0.95f,
                    summary = "Descrive la variazione tra le isole dell'arcipelago."
                ),
                CulturalSource(
                    title = "Características — Diccionario básico de canarismos",
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
