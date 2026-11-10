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
