package com.ryoustream.player.domain.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

// ─── Subtitle Settings ────────────────────────────────────────────────────────

data class SubtitleStyle(
    val textColor: Color           = Color.White,
    val backgroundColor: Color     = Color.Black.copy(alpha = 0.6f),
    val outlineColor: Color        = Color.Black,
    val outlineWidth: Float        = 2.5f,
    val fontSize: TextUnit         = 18.sp,
    val fontWeight: FontWeight     = FontWeight.Normal,
    val fontStyle: FontStyle       = FontStyle.Normal,
    val fontFamily: FontFamily     = FontFamily.Default,
    val backgroundEnabled: Boolean = true,
    val outlineEnabled: Boolean    = true,
    val shadowEnabled: Boolean     = true,
    val shadowColor: Color         = Color.Black.copy(alpha = 0.8f),
    val verticalPosition: Float    = 0.85f,   // 0.0=top, 1.0=bottom
    val scaleX: Float              = 1f,
    val scaleY: Float              = 1f,
    val letterSpacing: TextUnit    = 0.sp,
)

// ─── ASS/SSA Parsed Cue ───────────────────────────────────────────────────────

data class AssCue(
    val startMs: Long,
    val endMs: Long,
    val rawText: String,              // original with override codes
    val spans: List<AssSpan>,         // rendered spans
    val style: AssStyleEntry? = null,
    val marginL: Int = 0,
    val marginR: Int = 0,
    val marginV: Int = 0,
    val alignment: Int = 2,           // ASS numpad alignment
)

data class AssSpan(
    val text: String,
    val bold: Boolean        = false,
    val italic: Boolean      = false,
    val underline: Boolean   = false,
    val strikethrough: Boolean = false,
    val color: Color?        = null,   // \c or \1c override
    val outlineColor: Color? = null,   // \3c override
    val alpha: Float         = 1f,     // \alpha
    val fontSize: Float?     = null,   // \fs override
    val fontName: String?    = null,   // \fn override
    val scaleX: Float        = 1f,
    val scaleY: Float        = 1f,
    val isNewLine: Boolean   = false,
    val isDrawing: Boolean   = false,  // \p1 drawing commands
)

data class AssStyleEntry(
    val name: String        = "Default",
    val fontName: String    = "Arial",
    val fontSize: Float     = 20f,
    val primaryColor: Color = Color.White,
    val secondaryColor: Color = Color.Yellow,
    val outlineColor: Color = Color.Black,
    val backColor: Color    = Color.Black.copy(alpha = 0.5f),
    val bold: Boolean       = false,
    val italic: Boolean     = false,
    val underline: Boolean  = false,
    val strikeOut: Boolean  = false,
    val scaleX: Float       = 100f,
    val scaleY: Float       = 100f,
    val spacing: Float      = 0f,
    val angle: Float        = 0f,
    val borderStyle: Int    = 1,
    val outline: Float      = 2f,
    val shadow: Float       = 2f,
    val alignment: Int      = 2,
    val marginL: Int        = 10,
    val marginR: Int        = 10,
    val marginV: Int        = 10,
)

// ─── ASS/SSA Parser ──────────────────────────────────────────────────────────

object AssParser {

    private val TIME_REGEX = Regex("""(\d+):(\d{2}):(\d{2})\.(\d{2})""")
    private val OVERRIDE_REGEX = Regex("""\{([^}]*)\}""")

    fun parse(content: String): List<AssCue> {
        val lines = content.lines()
        val styles = mutableMapOf<String, AssStyleEntry>()
        val cues   = mutableListOf<AssCue>()

        var inEvents = false
        var inStyles = false
        var eventFormat = listOf<String>()
        var styleFormat = listOf<String>()

        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.equals("[V4+ Styles]", ignoreCase = true) ||
                trimmed.equals("[V4 Styles]", ignoreCase = true) -> {
                    inStyles = true; inEvents = false
                }
                trimmed.equals("[Events]", ignoreCase = true) -> {
                    inEvents = true; inStyles = false
                }
                trimmed.startsWith("[") -> {
                    inStyles = false; inEvents = false
                }
                inStyles && trimmed.startsWith("Format:", ignoreCase = true) -> {
                    styleFormat = trimmed.substringAfter(":").split(",").map { it.trim() }
                }
                inStyles && trimmed.startsWith("Style:", ignoreCase = true) -> {
                    parseStyle(trimmed.substringAfter(":"), styleFormat)?.let {
                        styles[it.name] = it
                    }
                }
                inEvents && trimmed.startsWith("Format:", ignoreCase = true) -> {
                    eventFormat = trimmed.substringAfter(":").split(",").map { it.trim() }
                }
                inEvents && trimmed.startsWith("Dialogue:", ignoreCase = true) -> {
                    parseDialogue(trimmed.substringAfter(":"), eventFormat, styles)?.let {
                        cues.add(it)
                    }
                }
            }
        }
        return cues.sortedBy { it.startMs }
    }

    private fun parseStyle(line: String, format: List<String>): AssStyleEntry? {
        val parts = line.split(",").map { it.trim() }
        if (parts.size < format.size) return null
        val m = format.zip(parts).toMap()
        return try {
            AssStyleEntry(
                name         = m["Name"] ?: "Default",
                fontName     = m["Fontname"] ?: "Arial",
                fontSize     = m["Fontsize"]?.toFloatOrNull() ?: 20f,
                primaryColor = parseAssColor(m["PrimaryColour"] ?: "&H00FFFFFF"),
                outlineColor = parseAssColor(m["OutlineColour"] ?: "&H00000000"),
                backColor    = parseAssColor(m["BackColour"] ?: "&H80000000"),
                bold         = m["Bold"] == "1" || m["Bold"] == "-1",
                italic       = m["Italic"] == "1" || m["Italic"] == "-1",
                underline    = m["Underline"] == "1" || m["Underline"] == "-1",
                scaleX       = m["ScaleX"]?.toFloatOrNull() ?: 100f,
                scaleY       = m["ScaleY"]?.toFloatOrNull() ?: 100f,
                spacing      = m["Spacing"]?.toFloatOrNull() ?: 0f,
                angle        = m["Angle"]?.toFloatOrNull() ?: 0f,
                outline      = m["Outline"]?.toFloatOrNull() ?: 2f,
                shadow       = m["Shadow"]?.toFloatOrNull() ?: 2f,
                alignment    = m["Alignment"]?.toIntOrNull() ?: 2,
                marginL      = m["MarginL"]?.toIntOrNull() ?: 10,
                marginR      = m["MarginR"]?.toIntOrNull() ?: 10,
                marginV      = m["MarginV"]?.toIntOrNull() ?: 10,
            )
        } catch (e: Exception) { null }
    }

    private fun parseDialogue(
        line: String,
        format: List<String>,
        styles: Map<String, AssStyleEntry>,
    ): AssCue? {
        // Text field may contain commas — split only up to format.size fields
        val parts = line.split(",", limit = format.size).map { it.trim() }
        if (parts.size < format.size) return null
        val m = format.zip(parts).toMap()

        val start   = parseTime(m["Start"]  ?: return null) ?: return null
        val end     = parseTime(m["End"]    ?: return null) ?: return null
        val rawText = m["Text"] ?: return null
        val styleN  = m["Style"] ?: "Default"
        val style   = styles[styleN]

        val marginL = m["MarginL"]?.toIntOrNull() ?: style?.marginL ?: 0
        val marginR = m["MarginR"]?.toIntOrNull() ?: style?.marginR ?: 0
        val marginV = m["MarginV"]?.toIntOrNull() ?: style?.marginV ?: 0
        val align   = style?.alignment ?: 2

        return AssCue(
            startMs   = start,
            endMs     = end,
            rawText   = rawText,
            spans     = parseOverrides(rawText, style),
            style     = style,
            marginL   = marginL,
            marginR   = marginR,
            marginV   = marginV,
            alignment = align,
        )
    }

    /** Parse override codes like {\b1\i1\c&H00FF00&} and produce spans */
    fun parseOverrides(text: String, baseStyle: AssStyleEntry?): List<AssSpan> {
        val spans    = mutableListOf<AssSpan>()
        var bold     = baseStyle?.bold    ?: false
        var italic   = baseStyle?.italic  ?: false
        var underline = baseStyle?.underline ?: false
        var strikethrough = false
        var color: Color?       = baseStyle?.primaryColor
        var outlineC: Color?    = baseStyle?.outlineColor
        var alpha               = 1f
        var fontSize: Float?    = baseStyle?.fontSize
        var fontName: String?   = baseStyle?.fontName
        var scaleX              = (baseStyle?.scaleX ?: 100f) / 100f
        var scaleY              = (baseStyle?.scaleY ?: 100f) / 100f
        var inDrawing           = false
        var lastIndex           = 0

        val matches = OVERRIDE_REGEX.findAll(text)

        for (match in matches) {
            // Text before this override block
            val before = text.substring(lastIndex, match.range.first)
            if (before.isNotEmpty()) {
                before.split("\\N", "\\n").forEachIndexed { idx, part ->
                    if (idx > 0) spans.add(AssSpan("", isNewLine = true))
                    if (part.isNotEmpty()) {
                        spans.add(AssSpan(
                            text          = part,
                            bold          = bold,
                            italic        = italic,
                            underline     = underline,
                            strikethrough = strikethrough,
                            color         = color,
                            outlineColor  = outlineC,
                            alpha         = alpha,
                            fontSize      = fontSize,
                            fontName      = fontName,
                            scaleX        = scaleX,
                            scaleY        = scaleY,
                            isDrawing     = inDrawing,
                        ))
                    }
                }
            }

            // Parse override codes inside { }
            val codes = match.groupValues[1]
            parseOverrideCodes(codes).forEach { (tag, value) ->
                when (tag.lowercase()) {
                    "b"     -> bold          = value != "0"
                    "i"     -> italic        = value != "0"
                    "u"     -> underline     = value != "0"
                    "s"     -> strikethrough = value != "0"
                    "c", "1c" -> color       = parseAssColor(value)
                    "3c"    -> outlineC       = parseAssColor(value)
                    "alpha" -> alpha          = parseAlpha(value)
                    "1a"    -> alpha          = 1f - parseAlpha(value)
                    "fs"    -> fontSize       = value.toFloatOrNull()
                    "fn"    -> fontName       = value.ifEmpty { null }
                    "fscx"  -> scaleX         = (value.toFloatOrNull() ?: 100f) / 100f
                    "fscy"  -> scaleY         = (value.toFloatOrNull() ?: 100f) / 100f
                    "p"     -> inDrawing      = value != "0"
                    "r"     -> { // reset to style defaults
                        bold  = baseStyle?.bold  ?: false
                        italic= baseStyle?.italic ?: false
                        color = baseStyle?.primaryColor
                        fontSize = baseStyle?.fontSize
                        fontName = baseStyle?.fontName
                    }
                }
            }
            lastIndex = match.range.last + 1
        }

        // Remaining text after last override
        val tail = text.substring(lastIndex)
        if (tail.isNotEmpty()) {
            tail.split("\\N", "\\n").forEachIndexed { idx, part ->
                if (idx > 0) spans.add(AssSpan("", isNewLine = true))
                if (part.isNotEmpty()) {
                    spans.add(AssSpan(
                        text          = part,
                        bold          = bold,
                        italic        = italic,
                        underline     = underline,
                        strikethrough = strikethrough,
                        color         = color,
                        outlineColor  = outlineC,
                        alpha         = alpha,
                        fontSize      = fontSize,
                        fontName      = fontName,
                        scaleX        = scaleX,
                        scaleY        = scaleY,
                        isDrawing     = inDrawing,
                    ))
                }
            }
        }

        return spans.filter { !it.isDrawing }
    }

    private fun parseOverrideCodes(codes: String): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        // Split on \ but keep the tag together
        val parts = codes.split("\\").filter { it.isNotEmpty() }
        for (part in parts) {
            // Find where tag name ends and value begins
            val tagMatch = Regex("""^([a-zA-Z0-9]+)(.*)$""").find(part)
            if (tagMatch != null) {
                val tag   = tagMatch.groupValues[1]
                val value = tagMatch.groupValues[2].trim()
                    .removePrefix("(").removeSuffix(")")
                    .trim()
                result.add(tag to value)
            }
        }
        return result
    }

    private fun parseTime(t: String): Long? {
        val m = TIME_REGEX.find(t) ?: return null
        val h   = m.groupValues[1].toLong()
        val min = m.groupValues[2].toLong()
        val sec = m.groupValues[3].toLong()
        val cs  = m.groupValues[4].toLong()   // centiseconds
        return h * 3_600_000 + min * 60_000 + sec * 1_000 + cs * 10
    }

    /**
     * Parse ASS colour: &HAABBGGRR → ARGB Color
     * Alpha in ASS: 0x00 = opaque, 0xFF = transparent
     */
    fun parseAssColor(hex: String): Color {
        val clean = hex.removePrefix("&H").removePrefix("0x")
            .padStart(8, '0').take(8)
        return try {
            val v  = clean.toLong(16)
            val a  = ((v shr 24) and 0xFF).toInt()
            val b  = ((v shr 16) and 0xFF).toInt()
            val g  = ((v shr  8) and 0xFF).toInt()
            val r  = (v          and 0xFF).toInt()
            val alpha = 255 - a   // invert: 0x00=opaque, 0xFF=transparent
            Color(r, g, b, alpha)
        } catch (e: Exception) { Color.White }
    }

    private fun parseAlpha(hex: String): Float {
        val clean = hex.removePrefix("&H").removePrefix("0x").take(2)
        return try {
            val v = clean.toInt(16)
            1f - (v / 255f)
        } catch (e: Exception) { 1f }
    }

    /** Parse SRT content into simple cues */
    fun parseSrt(content: String): List<AssCue> {
        val cues = mutableListOf<AssCue>()
        val blocks = content.trim().split(Regex("""\r?\n\r?\n"""))
        for (block in blocks) {
            val lines = block.trim().lines()
            if (lines.size < 3) continue
            // line 0 = index, line 1 = timestamps, line 2+ = text
            val timeLine = lines.getOrNull(1) ?: continue
            val timeMatch = Regex(
                """(\d{2}:\d{2}:\d{2}[.,]\d{3})\s*-->\s*(\d{2}:\d{2}:\d{2}[.,]\d{3})"""
            ).find(timeLine) ?: continue
            val start = parseSrtTime(timeMatch.groupValues[1]) ?: continue
            val end   = parseSrtTime(timeMatch.groupValues[2]) ?: continue
            val text  = lines.drop(2).joinToString("\n")
            // Strip basic HTML tags from SRT
            val clean = text.replace(Regex("<[^>]+>"), "")
            cues.add(AssCue(
                startMs = start, endMs = end,
                rawText = clean,
                spans   = listOf(AssSpan(clean)),
            ))
        }
        return cues
    }

    private fun parseSrtTime(t: String): Long? {
        val m = Regex("""(\d{2}):(\d{2}):(\d{2})[.,](\d{3})""").find(t) ?: return null
        return m.groupValues[1].toLong() * 3_600_000 +
               m.groupValues[2].toLong() * 60_000 +
               m.groupValues[3].toLong() * 1_000 +
               m.groupValues[4].toLong()
    }
}
