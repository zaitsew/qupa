package com.fabriquefutur.qupa.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text

/** Type scale for reading from about 3 m. Nothing is smaller than 16 sp; body text is 20 sp. */
object Type {
    val Hero = TextStyle(fontSize = 72.sp, fontWeight = FontWeight.ExtraBold, color = QupaColors.Text)
    val Title = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Bold, color = QupaColors.Text)
    val Question = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, color = QupaColors.Text)
    val Metric = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, color = QupaColors.Text)
    val Body = TextStyle(fontSize = 20.sp, color = QupaColors.Text)
    val BodyBold = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = QupaColors.Text)
    val Label = TextStyle(fontSize = 16.sp, color = QupaColors.Muted)
    val Muted = TextStyle(fontSize = 20.sp, color = QupaColors.Muted)
}

private val TileShape = RoundedCornerShape(16.dp)

/**
 * A panel that takes D-pad focus, shows it with a bright border and a small
 * zoom, and reacts to OK. The one building block for chips, answer tiles and
 * name fields.
 */
@Composable
fun FocusTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = TileShape,
    container: Color = QupaColors.Card,
    focusedContainer: Color = QupaColors.CardHigh,
    content: @Composable BoxScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, tween(120), label = "tile-scale")
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(if (focused) focusedContainer else container)
            .border(4.dp, if (focused) QupaColors.Focus else Color.Transparent, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        content = content,
    )
}

/** A setting: small label above the current value. OK cycles to the next value. */
@Composable
fun SettingChip(label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusTile(onClick, modifier) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(label, style = Type.Label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = Type.BodyBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.colors(
            containerColor = QupaColors.Accent,
            contentColor = QupaColors.Ink,
            focusedContainerColor = QupaColors.Accent,
            focusedContentColor = QupaColors.Ink,
            pressedContainerColor = QupaColors.Accent,
            pressedContentColor = QupaColors.Ink,
        ),
        border = ButtonDefaults.border(focusedBorder = Border(BorderStroke(4.dp, QupaColors.Focus))),
        scale = ButtonDefaults.scale(focusedScale = 1.08f),
    ) {
        Text(
            text,
            style = Type.BodyBold.copy(color = QupaColors.Ink),
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.colors(
            containerColor = QupaColors.CardHigh,
            contentColor = QupaColors.Text,
            focusedContainerColor = QupaColors.CardHigh,
            focusedContentColor = QupaColors.Text,
            pressedContainerColor = QupaColors.CardHigh,
            pressedContentColor = QupaColors.Text,
        ),
        border = ButtonDefaults.border(
            border = Border(BorderStroke(2.dp, QupaColors.Track)),
            focusedBorder = Border(BorderStroke(4.dp, QupaColors.Focus)),
        ),
        scale = ButtonDefaults.scale(focusedScale = 1.08f),
    ) {
        Text(text, style = Type.BodyBold, maxLines = 1, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
    }
}

/** A player or team: colour dot, name, optional trailing text such as "+850". */
@Composable
fun PlayerChip(
    name: String,
    colorIndex: Int,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    trailing: String? = null,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(QupaColors.Card)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(20.dp).clip(CircleShape)
                .background(QupaColors.player(colorIndex).copy(alpha = if (dimmed) 0.35f else 1f)),
        )
        Text(
            name,
            style = Type.BodyBold.copy(color = if (dimmed) QupaColors.Muted else QupaColors.Text),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 10.dp).weight(1f, fill = false),
        )
        if (trailing != null) {
            Text(trailing, style = Type.BodyBold.copy(color = QupaColors.Ok), modifier = Modifier.padding(start = 10.dp))
        }
    }
}

/** A thin progress bar. [fraction] runs 0..1. */
@Composable
fun BarFill(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 10.dp) {
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2)).background(QupaColors.Track)) {
        Box(
            Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(height / 2)).background(color),
        )
    }
}

/** The four answer glyphs: triangle, diamond, circle, square. Shape plus colour, for colour-blind players. */
@Composable
fun AnswerGlyph(index: Int, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension
        when (index) {
            0 -> drawPath(
                Path().apply { moveTo(s / 2, 0f); lineTo(s, s); lineTo(0f, s); close() },
                color,
            )
            1 -> drawPath(
                Path().apply { moveTo(s / 2, 0f); lineTo(s, s / 2); lineTo(s / 2, s); lineTo(0f, s / 2); close() },
                color,
            )
            2 -> drawCircle(color, radius = s / 2, center = Offset(s / 2, s / 2))
            else -> drawRect(color, topLeft = Offset.Zero, size = Size(s, s))
        }
    }
}

/** The app mark: four answer tiles around a question mark, as on the icon. */
@Composable
fun MarkArt(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
            val gap = size.minDimension * 0.06f
            val tile = (size.minDimension - gap) / 2
            val corner = androidx.compose.ui.geometry.CornerRadius(tile * 0.2f)
            val left = (size.width - size.minDimension) / 2
            val top = (size.height - size.minDimension) / 2
            for (i in 0..3) {
                val x = left + (i % 2) * (tile + gap)
                val y = top + (i / 2) * (tile + gap)
                drawRoundRect(QupaColors.Answer[i], Offset(x, y), Size(tile, tile), corner)
            }
            drawCircle(Color.White, radius = size.minDimension * 0.24f, center = Offset(size.width / 2, size.height / 2))
        }
        Text("?", style = TextStyle(fontSize = 96.sp, fontWeight = FontWeight.ExtraBold, color = QupaColors.Ink))
    }
}

@Composable
fun Spacer12() = androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))

@Composable
fun SectionRow(content: @Composable () -> Unit) = Row(
    Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
) { content() }
