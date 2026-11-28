package ai.juanita.app.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentReferenceDetectorTest {
    @Test
    fun detectsEllipticalItalianFollowUps() {
        assertTrue(RecentReferenceDetector.shouldRecall("E questa?"))
        assertTrue(RecentReferenceDetector.shouldRecall("E quella moto?"))
        assertTrue(RecentReferenceDetector.shouldRecall("E la batteria?"))
        assertTrue(RecentReferenceDetector.shouldRecall("E quanto costa?"))
        assertTrue(RecentReferenceDetector.shouldRecall("Come ti dicevo, la GSX-8TT"))
    }

    @Test
    fun detectsEnglishAndSpanishFollowUps() {
        assertTrue(RecentReferenceDetector.shouldRecall("What about that?"))
        assertTrue(RecentReferenceDetector.shouldRecall("And this model?"))
        assertTrue(RecentReferenceDetector.shouldRecall("Y esa?"))
        assertTrue(RecentReferenceDetector.shouldRecall("En base a eso"))
    }

    @Test
    fun avoidsUnrelatedContinuationAndOrdinaryRequests() {
        assertFalse(RecentReferenceDetector.shouldRecall("E se piove domani?"))
        assertFalse(RecentReferenceDetector.shouldRecall("E poi raccontami una barzelletta"))
        assertFalse(RecentReferenceDetector.shouldRecall("Che tempo farà domani?"))
        assertFalse(RecentReferenceDetector.shouldRecall("Raccontami una barzelletta"))
        assertFalse(RecentReferenceDetector.shouldRecall(""))
    }

    @Test
    fun explicitRecallCuesRemainSupported() {
        assertTrue(RecentReferenceDetector.shouldRecall("Te l'ho appena detto"))
        assertTrue(RecentReferenceDetector.shouldRecall("Cosa avevamo deciso pochi minuti fa?"))
        assertTrue(RecentReferenceDetector.shouldRecall("I just told you a few minutes ago"))
        assertTrue(RecentReferenceDetector.shouldRecall("¿Qué te dije hace un momento?"))
    }
}
