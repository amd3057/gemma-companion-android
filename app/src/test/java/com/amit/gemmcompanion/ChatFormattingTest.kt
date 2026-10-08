package com.amit.gemmcompanion

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatFormattingTest {
    @Test
    fun formatChatMarkdown_preservesBoldBulletsAndCode() {
        val formatted = formatChatMarkdown("**Plan**\n- First item\n- `code`")

        assertTrue(formatted.text.contains("Plan"))
        assertTrue(formatted.text.contains("• First item"))
        assertTrue(formatted.text.contains("code"))
        assertTrue(formatted.spanStyles.isNotEmpty())
        assertTrue(formatted.text.startsWith("Plan") || formatted.text.contains("Plan"))
    }
}
