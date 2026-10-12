package com.example.comfyui_remote.domain

import java.time.LocalDateTime

/**
 * PrimitiveNode "Run widget replace on values" (Phase 102): the frontend's applyTextReplacements
 * (utils/searchAndReplace.ts), applied to the primitive's string value before it reaches its targets.
 */
object TextReplacement {

    /** A graph node a pattern can name: by its "Node name for S&R" property, else by title. */
    data class NodeRef(val snrName: String?, val title: String?, val widgets: Map<String, String?>)

    private val PATTERN = Regex("%([^%]+)%")
    private val DATE_TOKENS = Regex("dd?|MM?|hh?|mm?|ss?|yyy?y?")
    private val UNSAFE = Regex("[/?<>\\\\:*|\"\\u0000-\\u001F\\u007F]")

    fun apply(value: String, nodes: List<NodeRef>, now: LocalDateTime): String =
        PATTERN.replace(value) { match ->
            val text = match.groupValues[1]
            val split = text.split(".")
            if (split.size != 2) {
                // The frontend formats only the part before the first dot ("date:yyyy.MM" gives just the year)
                if (split[0].startsWith("date:")) return@replace formatDate(split[0].substring(5), now)
                return@replace match.value
            }
            val node = nodes.firstOrNull { it.snrName == split[0] } ?: nodes.firstOrNull { it.title == split[0] }
                ?: return@replace match.value
            if (!node.widgets.containsKey(split[1])) return@replace match.value
            UNSAFE.replace(node.widgets[split[1]] ?: "", "_")
        }

    /** Date tokens d, M, h, m, s (padded to the token's length) and y (yy: two digits, yyy/yyyy: the year), 24-hour clock. */
    fun formatDate(format: String, now: LocalDateTime): String =
        DATE_TOKENS.replace(format) { token ->
            val t = token.value
            val number = when (t[0]) {
                'd' -> now.dayOfMonth
                'M' -> now.monthValue
                'h' -> now.hour
                'm' -> now.minute
                's' -> now.second
                else -> if (t.length == 2) now.year % 100 else now.year
            }
            number.toString().padStart(t.length, '0')
        }
}
