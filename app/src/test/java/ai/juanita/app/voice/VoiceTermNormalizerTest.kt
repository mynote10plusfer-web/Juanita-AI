package ai.juanita.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class VoiceTermNormalizerTest {

    @Test
    fun learnsDoubleTOnlyAfterExplicitCorrection() {
        val correction = VoiceTermNormalizer.extractExplicitDoubleLetterCorrection(
            previousUserUtterance = "Suzuki GSX 8T",
            correctionUtterance = "No, sono due T"
        )

        assertNotNull(correction)
        assertEquals("Suzuki GSX 8T", correction?.alias)
        assertEquals("Suzuki GSX-8TT", correction?.canonical)
    }

    @Test
    fun understandsSpokenLetterNameInItalian() {
        val correction = VoiceTermNormalizer.extractExplicitDoubleLetterCorrection(
            previousUserUtterance = "Suzuki GSX 8T",
            correctionUtterance = "No, sono due ti"
        )

        assertEquals("Suzuki GSX 8TT", correction?.canonical)
    }

    @Test
    fun correctsWrongFinalLetterWhenUserExplicitlyNamesTheDoubleT() {
        val correction = VoiceTermNormalizer.extractExplicitDoubleLetterCorrection(
            previousUserUtterance = "Suzuki GSX 8S",
            correctionUtterance = "No, sono due T"
        )

        assertEquals("Suzuki GSX-8TT", correction?.canonical)
    }

    @Test
    fun understandsPhoneticNumberAndSpokenLetterInModelName() {
        val correction = VoiceTermNormalizer.extractExplicitDoubleLetterCorrection(
            previousUserUtterance = "Suzuki GSX otto esse",
            correctionUtterance = "No, sono due ti"
        )

        assertEquals("Suzuki GSX-8TT", correction?.canonical)
    }

    @Test
    fun doesNotGuessWhenUserHasNotConfirmedRepeatedLetter() {
        assertNull(
            VoiceTermNormalizer.extractExplicitDoubleLetterCorrection(
                previousUserUtterance = "Suzuki GSX 8T",
                correctionUtterance = "No, è una T"
            )
        )
    }

    @Test
    fun handlesPunctuationAtEndOfPreviouslyRecognizedPhrase() {
        val correction = VoiceTermNormalizer.extractExplicitDoubleLetterCorrection(
            previousUserUtterance = "Suzuki GSX 8T.",
            correctionUtterance = "No, sono due T."
        )

        assertEquals("Suzuki GSX 8TT", correction?.canonical)
    }

    @Test
    fun persistsAndRestoresConfirmedCorrectionsWithoutChangingThem() {
        val original = ConfirmedVoiceTermCorrection("Suzuki GSX 8T", "Suzuki GSX-8TT")
        assertEquals(original, ConfirmedVoiceTermCorrection.fromStorageText(original.toStorageText()))
    }

    @Test
    fun appliesOnlyTheConfirmedPhraseAlias() {
        val correction = ConfirmedVoiceTermCorrection("Suzuki GSX 8T", "Suzuki GSX-8TT")

        assertEquals(
            "Ho visto la Suzuki GSX-8TT ieri.",
            VoiceTermNormalizer.normalize("Ho visto la Suzuki GSX 8T ieri.", listOf(correction))
        )
    }

    @Test
    fun longerAliasesAreAppliedBeforeShorterAliases() {
        val corrections = listOf(
            ConfirmedVoiceTermCorrection("GSX 8T", "GSX-8TT"),
            ConfirmedVoiceTermCorrection("Suzuki GSX 8T", "Suzuki GSX-8TT")
        )

        assertEquals(
            "Suzuki GSX-8TT",
            VoiceTermNormalizer.normalize("Suzuki GSX 8T", corrections)
        )
    }
}
