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
        val jose = identity("José", IdentityPresentation.MASCULINE, "ES", "España", "Canarias", "Tenerife", "Canario")

        store.create(juanita)
        store.create(jose)

        val reloaded = DigitalIdentityStore(isolatedContext()).list().associateBy { it.id }
        assertEquals(2, reloaded.size)
        assertEquals("Paisa", reloaded[juanita.id]?.origin?.community)
        assertEquals(IdentityPresentation.MASCULINE, reloaded[jose.id]?.presentation)
        assertEquals(juanita.id, reloaded[juanita.id]?.memoryNamespace)
        assertEquals(jose.id, reloaded[jose.id]?.memoryNamespace)
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
    fun identityNamespaceCannotBeAssignedToAnotherIdentity() {
        val identity = identity("Juanita", IdentityPresentation.FEMININE, "CO", "Colombia")
        val invalid = identity.copy(memoryNamespace = UUID.randomUUID().toString())

        assertThrows(IllegalArgumentException::class.java) { store.create(invalid) }
        assertFalse(testDirectory.walkTopDown().any { it.isFile && it.name.endsWith(".identity.enc") })
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
            personality = IdentityPersonality(
                warmth = 0.91f, humour = 0.82f, curiosity = 0.73f, expressiveness = 0.64f,
                formality = 0.15f, empathy = 0.88f, seriousness = 0.42f, directness = 0.77f,
                sociability = 0.69f, spontaneity = 0.83f, patience = 0.92f, optimism = 0.71f,
                assertiveness = 0.58f, playfulness = 0.86f, discretion = 0.79f
            )
        )
        store.create(original)
        val restored = DigitalIdentityStore(isolatedContext()).get(original.id)!!
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
        assertTrue(instruction.contains("patience 80"))
        assertTrue(instruction.contains("playfulness 20"))
        assertTrue(instruction.contains("does not determine personality"))
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
        assertTrue(approved.toSystemInstruction().contains("vos, tú e usted"))
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
