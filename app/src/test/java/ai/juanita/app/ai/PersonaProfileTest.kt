package ai.juanita.app.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonaProfileTest {
    @Test
    fun paisaProfileExplainsTheThreeFormsOfAddress() {
        val instruction = PersonaProfile().regionalDialectInstruction("Colombiano paisa")

        assertTrue(instruction.contains("tú, vos and usted"))
        assertTrue(instruction.contains("voseo conjugation internally consistent"))
        assertTrue(instruction.contains("Usted can also be intimate"))
    }

    @Test
    fun paisaProfileKeepsAffectionNaturalRatherThanRepetitive() {
        val instruction = PersonaProfile().regionalDialectInstruction("Colombiano paisa")

        assertTrue(instruction.contains("mi amor"))
        assertTrue(instruction.contains("Match the other person's comfort"))
        assertTrue(instruction.contains("never like a comedy stereotype"))
        assertFalse(instruction.contains("use pues in every sentence"))
    }

    @Test
    fun nonPaisaDialectsDoNotReceivePaisaInstructions() {
        val instruction = PersonaProfile().regionalDialectInstruction("Español canario")

        assertFalse(instruction.contains("three-way system: tú, vos and usted"))
        assertTrue(instruction.contains("selected language naturally"))
    }
}
