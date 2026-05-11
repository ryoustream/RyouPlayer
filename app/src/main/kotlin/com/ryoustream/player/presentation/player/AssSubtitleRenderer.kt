package com.ryoustream.player.presentation.player

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import com.ryoustream.player.domain.model.AssCue
import com.ryoustream.player.domain.model.SubtitleStyle

/**
 * AssSubtitleRenderer
 * Renders ASS/SSA/SRT cues with per-span styling on a Compose Canvas.
 */
@Composable
fun AssSubtitleRenderer(
    cues:       List<AssCue>,
    positionMs: Long,
    style:      SubtitleStyle,
    modifier:   Modifier = Modifier,
) {
    val activeCues = remember(cues, positionMs) {
        cues.filter { positionMs >= it.startMs && positionMs < it.endMs }
    }
    if (activeCues.isEmpty()) return

    val textMeasurer = rememberTextMeasurer()

    Box(modifier = modifier.fillMaxSize()) {
        activeCues.forEach { cue ->
            CueView(
                cue          = cue,
                style        = style,
                textMeasurer = textMeasurer,
            )
        }
    }
}

@Composable
private fun CueView(
    cue:         AssCue,
    style:       SubtitleStyle,
    textMeasurer: TextMeasurer,
) {
    // Build annotated string from spans
    val annotated = buildAnnotatedString {
        var needNewline = false
        cue.spans.forEach { span ->
            if (span.isNewLine) { needNewline = true; return@forEach }
            if (needNewline) { append("\n"); needNewline = false }
            withStyle(SpanStyle(
                color           = (span.color ?: style.textColor).copy(alpha = span.alpha),
                fontSize        = span.fontSize?.sp ?: style.fontSize,
                fontWeight      = if (span.bold) FontWeight.Bold else style.fontWeight,
                fontStyle       = if (span.italic) FontStyle.Italic else style.fontStyle,
                textDecoration  = when {
                    span.underline && span.strikethrough ->
                        TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
                    span.underline     -> TextDecoration.Underline
                    span.strikethrough -> TextDecoration.LineThrough
                    else               -> TextDecoration.None
                },
                letterSpacing   = style.letterSpacing,
            )) { append(span.text) }
        }
    }

    if (annotated.isEmpty()) return

    // ASS numpad alignment → Compose alignment
    val alignment = cue.alignment
    val boxAlign  = when (alignment) {
        7 -> Alignment.TopStart;    8 -> Alignment.TopCenter;    9 -> Alignment.TopEnd
        4 -> Alignment.CenterStart; 5 -> Alignment.Center;       6 -> Alignment.CenterEnd
        1 -> Alignment.BottomStart; 3 -> Alignment.BottomEnd
        else -> Alignment.BottomCenter  // 2 = default
    }
    val textAlign = when (alignment) {
        1, 4, 7 -> TextAlign.Start
        3, 6, 9 -> TextAlign.End
        else    -> TextAlign.Center
    }

    // Bottom padding from style or cue marginV
    val bottomPad = if (alignment <= 3) {
        (cue.marginV.dp).coerceAtLeast(8.dp)
            .plus(((1f - style.verticalPosition) * 100).dp)
    } else 0.dp

    Box(
        modifier         = Modifier.fillMaxSize(),
        contentAlignment = boxAlign,
    ) {
        val bgColor = if (style.backgroundEnabled) style.backgroundColor else Color.Transparent
        // Outline layers + main text via Text composable for simplicity and performance
        Box(
            modifier = Modifier
                .padding(
                    start  = cue.marginL.dp.coerceAtLeast(12.dp),
                    end    = cue.marginR.dp.coerceAtLeast(12.dp),
                    bottom = bottomPad,
                )
        ) {
            // Shadow
            if (style.shadowEnabled) {
                Text(
                    text           = annotated,
                    textAlign      = textAlign,
                    modifier       = Modifier.offset(x = 2.dp, y = 2.dp),
                    style          = TextStyle(
                        color      = style.shadowColor,
                        fontSize   = style.fontSize,
                        fontWeight = style.fontWeight,
                    ),
                )
            }
            // Outline (4 directions)
            if (style.outlineEnabled && style.outlineWidth > 0f) {
                val ow = style.outlineWidth.dp
                listOf(
                    -ow to -ow, ow to -ow, -ow to ow, ow to ow,
                    0.dp to -ow, 0.dp to ow, -ow to 0.dp, ow to 0.dp,
                ).forEach { (dx, dy) ->
                    Text(
                        text      = annotated,
                        textAlign = textAlign,
                        modifier  = Modifier.offset(x = dx, y = dy),
                        style     = TextStyle(
                            color     = cue.spans.firstOrNull()?.outlineColor ?: style.outlineColor,
                            fontSize  = style.fontSize,
                            fontWeight= style.fontWeight,
                        ),
                    )
                }
            }
            // Background box
            if (style.backgroundEnabled) {
                Text(
                    text           = annotated,
                    textAlign      = textAlign,
                    style          = TextStyle(
                        background = bgColor,
                        fontSize   = style.fontSize,
                        fontWeight = style.fontWeight,
                    ),
                )
            }
            // Main text (on top)
            Text(
                text      = annotated,
                textAlign = textAlign,
            )
        }
    }
}
