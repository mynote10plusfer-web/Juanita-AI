package ai.juanita.app.astrology

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class AstrologyContextTest {
    @Test fun sunSignBoundaryDates() {
        assertEquals("libra", AstrologyContext.sunSign(LocalDate.of(1975, 10, 16)))
        assertEquals("scorpio", AstrologyContext.sunSign(LocalDate.of(2000, 10, 23)))
        assertEquals("capricorn", AstrologyContext.sunSign(LocalDate.of(2000, 12, 22)))
        assertEquals("aries", AstrologyContext.sunSign(LocalDate.of(2000, 3, 21)))
    }
}
