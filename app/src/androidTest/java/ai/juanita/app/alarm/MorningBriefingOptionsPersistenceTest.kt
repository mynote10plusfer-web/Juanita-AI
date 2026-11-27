package ai.juanita.app.alarm

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MorningBriefingOptionsPersistenceTest {
    @Test
    fun selectedSectionsSurviveReload() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("juanita_alarm", Context.MODE_PRIVATE)
        prefs.edit().remove("briefing_weather").remove("briefing_horoscope")
            .remove("briefing_news").remove("briefing_calendar").commit()

        val expected = MorningBriefingOptions(
            weather = false,
            horoscope = true,
            news = true,
            calendar = false
        )
        expected.save(context)

        assertEquals(expected, MorningBriefingOptions.load(context))
    }
}
