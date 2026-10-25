package com.vuegram.core

/**
 * Turns TDLib's error messages into something a viewer can act on. Telegram rate limits ("FLOOD_WAIT_37",
 * "Too Many Requests: retry after 37") become "wait 37 seconds"; network trouble says so; anything else is shown as
 * Telegram wrote it.
 */
object Errors {
    sealed class Kind {
        data class SlowDown(val seconds: Int) : Kind()
        object Network : Kind()
        data class Other(val message: String) : Kind()
    }

    private val floodWait = Regex("""FLOOD_WAIT_(\d+)|retry after (\d+)""", RegexOption.IGNORE_CASE)
    private val network = Regex("""network|timeout|timed out|connection|unreachable|offline""", RegexOption.IGNORE_CASE)

    fun classify(message: String?): Kind {
        val text = message.orEmpty().trim()
        floodWait.find(text)?.let { match ->
            val seconds = (match.groupValues[1].ifEmpty { match.groupValues[2] }).toIntOrNull() ?: 0
            return Kind.SlowDown(seconds)
        }
        if (network.containsMatchIn(text)) return Kind.Network
        return Kind.Other(text.ifEmpty { Texts.unknownError() })
    }
}
