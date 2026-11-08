package ai.juanita.app.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersonaProfileBehaviorTest {
    @Test
    fun systemInstructionContainsContextualConversationRules() {
        val instruction = PersonaProfile().toSystemInstruction("Italiano", "Italiano")

        assertTrue(instruction.contains("Answer the direct question first"))
        assertTrue(instruction.contains("at most one useful follow-up question"))
        assertTrue(instruction.contains("never assume an outing"))
        assertTrue(instruction.contains("do not end every reply with a question"))
    }

    @Test
    fun systemInstructionContainsCalibratedEmotionalAndSocialReasoning() {
        val instruction = PersonaProfile().toSystemInstruction("Italiano", "Italiano")

        assertTrue(instruction.contains("tentative hypotheses"))
        assertTrue(instruction.contains("listen and acknowledge when the user is venting"))
        assertTrue(instruction.contains("avoid automatically taking the user's side"))
        assertTrue(instruction.contains("Accept corrections immediately"))
        assertTrue(instruction.contains("Do not diagnose"))
    }

    @Test
    fun systemInstructionEnforcesTruthfulnessAndMemoryGrounding() {
        val instruction = PersonaProfile().toSystemInstruction("Italiano", "Italiano")

        assertTrue(instruction.contains("Never fabricate facts, memories, sources, searches"))
        assertTrue(instruction.contains("If no such tool is available"))
        assertTrue(instruction.contains("Never invent a memory or quotation"))
        assertTrue(instruction.contains("unless its real operation returned success"))
    }

    @Test
    fun systemInstructionPreservesSelectedLanguageAndPersona() {
        val instruction = PersonaProfile(name = "Juanita").toSystemInstruction("Italiano", "Español canario")

        assertTrue(instruction.contains("Reply in the user selected language: Italiano"))
        assertTrue(instruction.contains("Selected regional speech profile: Español canario"))
        assertTrue(instruction.contains("You are Juanita"))
    }
}
