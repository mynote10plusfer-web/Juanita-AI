package ai.juanita.app.profile

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PersonalProfileStoreTest {
    private lateinit var testDirectory: File
    private lateinit var store: PersonalProfileStore

    @Before
    fun setUp() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        testDirectory = File(target.cacheDir, "personal-profile-test").apply {
            deleteRecursively()
            mkdirs()
        }
        val isolatedContext = object : ContextWrapper(target) {
            override fun getFilesDir(): File = testDirectory
            override fun getApplicationContext(): Context = this
        }
        store = PersonalProfileStore(isolatedContext)
    }

    @After
    fun tearDown() {
        testDirectory.deleteRecursively()
    }

    @Test
    fun profileSurvivesStoreRecreationAndPreservesFieldProvenance() {
        store.updateField(PersonalProfile.PREFERRED_NAME, "Ric")
        store.updateField(PersonalProfile.LANGUAGES, "Italiano, spagnolo, ceco")

        val reloaded = PersonalProfileStore(isolatedContext()).load()

        assertEquals("Ric", reloaded.value(PersonalProfile.PREFERRED_NAME))
        assertEquals("Italiano, spagnolo, ceco", reloaded.value(PersonalProfile.LANGUAGES))
        assertEquals(PersonalProfileField.SOURCE_USER_CONFIRMED,
            reloaded.fields[PersonalProfile.PREFERRED_NAME]?.source)
        assertEquals(true, reloaded.fields[PersonalProfile.PREFERRED_NAME]?.confirmedByUser)
    }

    @Test
    fun blankValueRemovesOnlyTheSelectedField() {
        store.updateField(PersonalProfile.PREFERRED_NAME, "Ric")
        store.updateField(PersonalProfile.HOME_LOCALITY, "Adeje")
        store.updateField(PersonalProfile.PREFERRED_NAME, " ")

        val profile = store.load()
        assertEquals(null, profile.value(PersonalProfile.PREFERRED_NAME))
        assertEquals("Adeje", profile.value(PersonalProfile.HOME_LOCALITY))
    }

    @Test
    fun inferredValueCannotBePersistedWithoutUserConfirmation() {
        assertThrows(IllegalArgumentException::class.java) {
            store.updateField(
                PersonalProfile.AGE,
                "50",
                source = PersonalProfileField.SOURCE_CONFIRMED_INFERENCE,
                confirmedByUser = false
            )
        }
        assertFalse(store.load().fields.containsKey(PersonalProfile.AGE))
    }

    @Test
    fun clearingProfileDoesNotDeleteAnyOtherAppData() {
        store.updateField(PersonalProfile.PREFERRED_NAME, "Ric")
        val unrelatedFile = File(testDirectory, "unrelated-memory-fixture.txt").apply {
            writeText("preserve")
        }

        store.clear()

        assertEquals(null, store.load().value(PersonalProfile.PREFERRED_NAME))
        assertEquals("preserve", unrelatedFile.readText())
    }

    private fun isolatedContext(): Context {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        return object : ContextWrapper(target) {
            override fun getFilesDir(): File = testDirectory
            override fun getApplicationContext(): Context = this
        }
    }
}
