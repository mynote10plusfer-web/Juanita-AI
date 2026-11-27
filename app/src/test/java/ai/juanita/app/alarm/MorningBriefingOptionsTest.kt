package ai.juanita.app.alarm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MorningBriefingOptionsTest {
    @Test
    fun defaultsPreserveWeatherAndCalendarOnly() {
        val options = MorningBriefingOptions()
        assertTrue(options.weather)
        assertTrue(options.calendar)
        assertFalse(options.horoscope)
        assertFalse(options.news)
    }

    @Test
    fun optionalSectionsCanBeEnabledIndependently() {
        val options = MorningBriefingOptions().copy(horoscope = true, news = true)
        assertTrue(options.horoscope)
        assertTrue(options.news)
        assertTrue(options.weather)
        assertTrue(options.calendar)
    }

    @Test
    fun disablingWeatherDoesNotDisableOtherSections() {
        val options = MorningBriefingOptions().copy(weather = false)
        assertFalse(options.weather)
        assertTrue(options.calendar)
    }
}
