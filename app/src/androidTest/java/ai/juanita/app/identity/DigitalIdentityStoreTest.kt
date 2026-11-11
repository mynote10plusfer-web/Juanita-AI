package ai.juanita.app.identity

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DigitalIdentityStoreTest {
    private lateinit var testDirectory: File
    private lateinit var store: DigitalIdentityStore

    @Before
    fun setUp() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        testDirectory = File(target.cacheDir, "digital-identity-test-${UUID.randomUUID()}").apply {
            deleteRecursively()
            mkdirs()
        }
        store = DigitalIdentityStore(isolatedContext())
    }

    @After
    fun tearDown() {
        testDirectory.deleteRecursively()
    }

    @Test
    fun identitiesPersistIndependentlyAcrossStoreRecreation() {
        val juanita = identity("Juanita", IdentityPresentation.FEMININE, "CO", "Colombia", "Antioquia", "Medellín", "Paisa")
        val jose = identity("José", IdentityPresentation.MASCULINE, "ES", "España", "Andalucía", "Sevilla", "Andaluz")

        store.create(juanita)
        store.create(jose)

        val reloaded = DigitalIdentityStore(isolatedContext()).list().associateBy { it.id }
        assertEquals(2, reloaded.size)
        assertEquals("Paisa", reloaded[juanita.id]?.origin?.community)
        assertEquals(IdentityPresentation.MASCULINE, reloaded[jose.id]?.presentation)
        assertEquals(SHARED_IDENTITY_MEMORY_NAMESPACE, reloaded[juanita.id]?.memoryNamespace)
        assertEquals(SHARED_IDENTITY_MEMORY_NAMESPACE, reloaded[jose.id]?.memoryNamespace)
    }

    @Test
    fun identityContentsAreEncryptedAtRest() {
        val identity = identity("NomeRiservatoJuanita", IdentityPresentation.FEMININE, "CO", "Colombia")
        store.create(identity)
        val encryptedFile = File(File(testDirectory, "juanita_digital_identities"), "${identity.id}.identity.enc")
        val storedBytes = encryptedFile.readText()

        assertFalse(storedBytes.contains("NomeRiservatoJuanita"))
        assertFalse(storedBytes.contains("Colombia"))
        assertNotNull(store.get(identity.id))
    }

    @Test
    fun updatingOrDeletingOneIdentityDoesNotChangeAnother() {
        val juanita = identity("Juanita", IdentityPresentation.FEMININE, "CO", "Colombia")
        val jose = identity("José", IdentityPresentation.MASCULINE, "ES", "España")
        store.create(juanita)
        store.create(jose)

        store.update(juanita.copy(biography = "Biografia personale di Juanita"))
        assertEquals("Biografia personale di Juanita", store.get(juanita.id)?.biography)
        assertEquals("José", store.get(jose.id)?.displayName)

        assertEquals(true, store.delete(juanita.id))
        assertNull(store.get(juanita.id))
        assertNotNull(store.get(jose.id))
        assertEquals(1, store.list().size)
    }

    @Test
    fun regionalOriginAndCharacterTraitsRemainSeparateWhileMemoryNamespaceIsShared() {
        val juanita = identity("Juanita", IdentityPresentation.FEMININE, "CO", "Colombia", "Antioquia", "Medellín", "Paisa")
            .copy(personality = IdentityPersonality(warmth = 0.95f, humour = 0.8f))
        val jose = identity("José", IdentityPresentation.MASCULINE, "ES", "España", "Andalucía", "Sevilla", "Andaluz")
            .copy(personality = IdentityPersonality(warmth = 0.35f, humour = 0.2f))
        store.create(juanita)
        store.create(jose)

        store.update(juanita.copy(
            origin = CulturalOrigin("ES", "España", "Canarias", "Tenerife", "Canario"),
            personality = juanita.personality.copy(warmth = 0.99f)
        ))

        val updatedJuanita = store.get(juanita.id)!!
        val unchangedJose = store.get(jose.id)!!
        assertEquals("Canarias", updatedJuanita.origin.region)
        assertEquals(0.99f, updatedJuanita.personality.warmth, 0.001f)
        assertEquals("Andalucía", unchangedJose.origin.region)
        assertEquals(0.35f, unchangedJose.personality.warmth, 0.001f)
        assertEquals(SHARED_IDENTITY_MEMORY_NAMESPACE, updatedJuanita.memoryNamespace)
        assertEquals(SHARED_IDENTITY_MEMORY_NAMESPACE, unchangedJose.memoryNamespace)
    }

    @Test
    fun userCanCreateIndependentCollectionsOfAnySizeForBothCharacterFamilies() {
        val juanitas = (1..5).map { index ->
            identity("Juanita $index", IdentityPresentation.FEMININE, "CO", "Colombia", "Region $index")
        }
        val jose = (1..4).map { index ->
            identity("José $index", IdentityPresentation.MASCULINE, "ES", "España", "Region $index")
        }

        (juanitas + jose).forEach(store::create)

        val saved = store.list()
        assertEquals(9, saved.size)
        assertEquals(5, saved.count { it.presentation == IdentityPresentation.FEMININE })
        assertEquals(4, saved.count { it.presentation == IdentityPresentation.MASCULINE })
        assertEquals(juanitas.map { it.displayName }.toSet(), saved.filter { it.presentation == IdentityPresentation.FEMININE }.map { it.displayName }.toSet())
        assertTrue(saved.all { it.memoryNamespace == SHARED_IDENTITY_MEMORY_NAMESPACE })
    }

    @Test
    fun duplicateGetsIndependentIdAndArchiveRestoreNeverDeletesSharedProfileData() {
        val original = identity("Juanita Paisa", IdentityPresentation.FEMININE, "CO", "Colombia")
            .copy(biography = "Biografia originale")
        store.create(original)

        val duplicate = store.duplicate(original.id, "Juanita Canaria")
        assertTrue(duplicate.id != original.id)
        assertEquals("Juanita Canaria", duplicate.displayName)
        assertEquals("Biografia originale", duplicate.biography)

        store.setArchived(original.id, true)
        assertTrue(store.get(original.id)!!.archived)
        assertFalse(store.get(duplicate.id)!!.archived)

        store.setArchived(original.id, false)
        assertFalse(store.get(original.id)!!.archived)
        assertEquals(2, store.list().size)
        assertEquals(SHARED_IDENTITY_MEMORY_NAMESPACE, store.get(original.id)!!.memoryNamespace)
    }

    @Test
    fun unreadableIdentityFailsClosedAndPreservesItsFile() {
        val identity = identity("Juanita", IdentityPresentation.FEMININE, "CO", "Colombia")
        store.create(identity)
        val encryptedFile = File(File(testDirectory, "juanita_digital_identities"), "${identity.id}.identity.enc")
        encryptedFile.writeText("not-valid-ciphertext")

        assertThrows(IllegalStateException::class.java) { store.get(identity.id) }
        assertEquals("not-valid-ciphertext", encryptedFile.readText())
    }


    @Test
    fun expandedCharacterTraitsPersistAcrossStoreRecreation() {
        val original = identity("Juanita Paisa", IdentityPresentation.FEMININE, "CO", "Colombia").copy(
            voiceName = "Sulafat",
            biography = "Una biografia di prova persistente.",
            personality = IdentityPersonality(
                irony = 0.93f, sweetness = 0.84f, sensuality = 0.72f, friendliness = 0.88f,
                warmth = 0.91f, humour = 0.82f, curiosity = 0.73f, expressiveness = 0.64f,
                formality = 0.15f, empathy = 0.88f, seriousness = 0.42f, directness = 0.77f,
                sociability = 0.69f, spontaneity = 0.83f, patience = 0.92f, optimism = 0.71f,
                assertiveness = 0.58f, playfulness = 0.86f, discretion = 0.79f
            )
        )
        store.create(original)
        val restored = DigitalIdentityStore(isolatedContext()).get(original.id)!!
        assertEquals("Sulafat", restored.voiceName)
        assertEquals("Una biografia di prova persistente.", restored.biography)
        assertEquals(0.93f, restored.personality.irony, 0.001f)
        assertEquals(0.84f, restored.personality.sweetness, 0.001f)
        assertEquals(0.72f, restored.personality.sensuality, 0.001f)
        assertEquals(0.88f, restored.personality.friendliness, 0.001f)
        assertEquals(0.77f, restored.personality.directness, 0.001f)
        assertEquals(0.69f, restored.personality.sociability, 0.001f)
        assertEquals(0.83f, restored.personality.spontaneity, 0.001f)
        assertEquals(0.92f, restored.personality.patience, 0.001f)
        assertEquals(0.71f, restored.personality.optimism, 0.001f)
        assertEquals(0.58f, restored.personality.assertiveness, 0.001f)
        assertEquals(0.86f, restored.personality.playfulness, 0.001f)
        assertEquals(0.79f, restored.personality.discretion, 0.001f)
    }

    @Test
    fun changingVoiceFromSettingsPersistsOnlyForTheSelectedCharacter() {
        val juanita = identity("Juanita Paisa", IdentityPresentation.FEMININE, "CO", "Colombia")
        val anotherJuanita = identity("Juanita Canaria", IdentityPresentation.FEMININE, "ES", "España")
        store.create(juanita)
        store.create(anotherJuanita)

        store.update(juanita.copy(voiceName = "Sulafat"))
        val reopened = DigitalIdentityStore(isolatedContext())
        assertEquals("Sulafat", reopened.get(juanita.id)?.voiceName)
        assertEquals("Kore", reopened.get(anotherJuanita.id)?.voiceName)
    }

    @Test
    fun newIdentityReceivesDefinedCharacterDefaults() {
        val original = identity("José", IdentityPresentation.MASCULINE, "ES", "España")
        store.create(original)
        val restored = DigitalIdentityStore(isolatedContext()).get(original.id)!!
        assertEquals(0.55f, restored.personality.directness, 0.001f)
        assertEquals(0.6f, restored.personality.sociability, 0.001f)
        assertEquals(0.75f, restored.personality.discretion, 0.001f)
    }


    @Test
    fun systemInstructionTurnsCharacterTraitsIntoBehaviorAndRejectsStereotypes() {
        val profile = identity("Juanita", IdentityPresentation.FEMININE, "CO", "Colombia", "Antioquia", "Medellín", "Paisa")
            .copy(personality = IdentityPersonality(directness = 0.9f, patience = 0.8f, playfulness = 0.2f))
        val instruction = profile.toSystemInstruction()
        assertTrue(instruction.contains("directness 90"))
        assertTrue(instruction.contains("irony 50"))
        assertTrue(instruction.contains("sweetness 70"))
        assertTrue(instruction.contains("sensuality 50"))
        assertTrue(instruction.contains("patience 80"))
        assertTrue(instruction.contains("playfulness 20"))
        assertTrue(instruction.contains("does not determine personality"))
        assertTrue(instruction.contains("same encrypted knowledge"))
        assertTrue(instruction.contains("does not merge character personalities"))
        assertTrue(instruction.contains("without aggression"))
        assertTrue(instruction.contains("never turn warmth or empathy into flattery"))
    }

    @Test
    fun culturalCatalogIsSourceBackedOfflineAndRequiresExplicitApproval() {
        val dossiers = CulturalDossierCatalog.dossiers
        assertEquals(2, dossiers.size)
        dossiers.forEach { dossier ->
            assertTrue(dossier.summary.isNotBlank())
            assertTrue(dossier.sources.size >= 3)
            dossier.sources.forEach { source ->
                assertTrue(source.title.isNotBlank())
                assertTrue(source.publisher.isNotBlank())
                assertTrue(source.url.startsWith("https://"))
                assertTrue(source.confidence in 0f..1f)
            }
            assertNull(dossier.approvedAtMillis)
        }

        val base = identity("Juanita", IdentityPresentation.FEMININE, "CO", "Colombia")
        assertFalse(base.toSystemInstruction().contains("USER-APPROVED CULTURAL RESEARCH CONTEXT"))
        val approved = base.copy(culturalDossier = dossiers.first().copy(approvedAtMillis = 123L))
        assertTrue(approved.toSystemInstruction().contains("USER-APPROVED CULTURAL RESEARCH CONTEXT"))
        assertTrue(approved.toSystemInstruction().contains("Vos, tú e usted"))
    }

    @Test
    fun approvedCulturalDossierPersistsEncryptedAndCanBeRevoked() {
        val base = identity("Juanita", IdentityPresentation.FEMININE, "CO", "Colombia")
        val approved = base.copy(culturalDossier = CulturalDossierCatalog.dossiers.first().copy(approvedAtMillis = 456L))
        store.create(approved)
        val restored = DigitalIdentityStore(isolatedContext()).get(base.id)!!
        assertEquals(456L, restored.culturalDossier?.approvedAtMillis)
        assertEquals(3, restored.culturalDossier?.sources?.size)
        assertTrue(restored.toSystemInstruction().contains("USER-APPROVED CULTURAL RESEARCH CONTEXT"))

        store.update(restored.copy(culturalDossier = null))
        val revoked = DigitalIdentityStore(isolatedContext()).get(base.id)!!
        assertNull(revoked.culturalDossier)
        assertFalse(revoked.toSystemInstruction().contains("USER-APPROVED CULTURAL RESEARCH CONTEXT"))
    }

    @Test
    fun sameRegionalDossierAppliesToMasculineAndFeminineIdentitiesWithoutChangingTheirPresentation() {
        val dossier = CulturalDossierCatalog.dossiers.first().copy(approvedAtMillis = 123L)
        val feminine = identity("Juanita", IdentityPresentation.FEMININE, "CO", "Colombia")
            .copy(culturalDossier = dossier)
        val masculine = identity("Jose", IdentityPresentation.MASCULINE, "CO", "Colombia")
            .copy(culturalDossier = dossier)

        val feminineInstruction = feminine.toSystemInstruction()
        val masculineInstruction = masculine.toSystemInstruction()
        assertTrue(feminineInstruction.contains("Vos, tú e usted"))
        assertTrue(masculineInstruction.contains("Vos, tú e usted"))
        assertTrue(feminineInstruction.contains("feminine grammatical self-reference"))
        assertTrue(masculineInstruction.contains("masculine grammatical self-reference"))
        assertEquals(feminine.culturalDossier, masculine.culturalDossier)
        assertEquals(feminine.origin, masculine.origin)
    }

    private fun identity(
        name: String,
        presentation: IdentityPresentation,
        countryCode: String,
        countryName: String,
        region: String = "",
        locality: String = "",
        community: String = ""
    ) = DigitalIdentity(
        id = UUID.randomUUID().toString(),
        displayName = name,
        presentation = presentation,
        origin = CulturalOrigin(countryCode, countryName, region, locality, community),
        preferredLanguageTag = if (countryCode == "CO") "es-CO" else "es-ES",
        languages = listOf("es", "es")
    )

    private fun isolatedContext(): Context {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        return object : ContextWrapper(target) {
            override fun getFilesDir(): File = testDirectory
            override fun getApplicationContext(): Context = this
        }
    }
}
