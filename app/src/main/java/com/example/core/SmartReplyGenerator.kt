package com.example.core

import com.example.data.model.ChatMessage

/**
 * Generates 3 instant, zero-cost contextual Smart Reply suggestions based on the latest assistant turn.
 */
object SmartReplyGenerator {

    fun generateSuggestions(messages: List<ChatMessage>): List<String> {
        val lastAssistant = messages.lastOrNull { it.role == "assistant" } ?: return emptyList()
        val body = lastAssistant.content.trim()
        if (body.length < 12) return emptyList()

        val suggestions = LinkedHashSet<String>()
        val lower = body.lowercase()

        if (body.contains("```")) {
            suggestions.add("Walk me through how this code works")
            suggestions.add("Add error handling and edge cases")
            suggestions.add("Show a shorter alternative")
        }
        if (lastAssistant.citations.isNotEmpty() || lower.contains("source") || lower.contains("http")) {
            suggestions.add("Compare the top sources in detail")
            suggestions.add("What changed most recently?")
        }
        if (body.contains("|") && body.contains("---")) {
            suggestions.add("Which option do you recommend?")
            suggestions.add("Summarize the key trade-offs")
        }
        if (body.contains("?")) {
            suggestions.add("Yes, go ahead with that")
            suggestions.add("Give me the concise version")
        }
        if (lower.contains("step") || lower.contains("1.") || lower.contains("2.")) {
            suggestions.add("Expand on step 1")
            suggestions.add("Turn this into a quick checklist")
        }

        val defaults = listOf(
            "Summarize in 3 bullet points",
            "Give me a concrete example",
            "What should I do next?",
        )
        for (d in defaults) {
            if (suggestions.size >= 3) break
            suggestions.add(d)
        }
        return suggestions.take(3)
    }
}
