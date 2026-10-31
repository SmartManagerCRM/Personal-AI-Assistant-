package com.smartmanager.ai

import com.smartmanager.ai.actions.ContactResolver
import com.smartmanager.ai.actions.PhoneActions
import org.junit.Assert.*
import org.junit.Test

class AppLogicTest {
    private fun s(spoken: String, name: String) = ContactResolver.score(spoken, name)

    @Test fun `arabic spoken names match latin contact names`() {
        assertTrue(s("أحمد", "Ahmed Ben Ali") >= 0.75)
        assertTrue(s("أحمد بن علي", "Ahmed Ben Ali") >= 0.95)
        assertTrue(s("كريم", "Karim") >= 0.9)
        assertTrue(s("يوسف", "Youssef") >= 0.9)
        assertTrue(s("محمد", "Mohamed Trabelsi") >= 0.75)
        assertTrue(s("ahmed", "أحمد") >= 0.9)
        assertEquals(1.0, s("Sarah", "sarah"), 0.001)
    }

    @Test fun `different people do not match`() {
        assertTrue(s("أحمد", "Karim") < 0.75)
        assertTrue(s("سارة", "Mohamed") < 0.75)
        assertTrue(s("Ahmed", "Slim Gharbi") < 0.75)
    }

    @Test fun `glued prepositions are tried as variants`() {
        assertTrue(ContactResolver.nameVariants("لأحمد").contains("أحمد"))
        assertTrue(ContactResolver.nameVariants("بكريم").contains("كريم"))
        assertEquals(listOf("Ali"), ContactResolver.nameVariants("Ali"))
    }

    @Test fun `whatsapp numbers are normalised to international digits`() {
        assertEquals("21620123456", PhoneActions.toWaNumber("20 123 456", "216"))
        assertEquals("21620123456", PhoneActions.toWaNumber("+216 20 123 456", "216"))
        assertEquals("21620123456", PhoneActions.toWaNumber("00216 20123456", "216"))
        assertEquals("33612345678", PhoneActions.toWaNumber("+33 6 12 34 56 78", "216"))
        assertEquals("21620123456", PhoneActions.toWaNumber("020123456", "216"))
    }
}
