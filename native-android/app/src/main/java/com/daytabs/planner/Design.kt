package com.daytabs.planner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

internal val actionInk = Color(0xFF231A2A)
internal val actionYellow = Color(0xFFFFD947)
private val displayFont = FontFamily(Font(R.font.bricolage_bold, FontWeight.Bold))
private val featherFont = FontFamily(Font(R.font.feather))

internal data class Palette(
    val background: Color,
    val surface: Color,
    val ink: Color,
    val secondary: Color,
    val date: Color,
    val divider: Color,
    val danger: Color,
    val shadow: Color,
    val completed: Color,
    val cover: Color,
) {
    val remaining = Color(0xFFFF987A)
    val goal = Color(0xFFD9F398)
}

internal val lightPalette = Palette(
    background = Color(0xFFF5F1FF), surface = Color(0xFFFFFEFC), ink = actionInk,
    secondary = Color(0xFF584C62), date = Color(0xFFE7DCFF), divider = Color(0xFFD6C9E0),
    danger = Color(0xFFA12B31), shadow = actionInk, completed = Color(0xFFBCA0FA), cover = Color(0xFFD6BCFF),
)

internal val darkPalette = Palette(
    background = Color(0xFF221D22), surface = Color(0xFF302830), ink = Color(0xFFFFF6EE),
    secondary = Color(0xFFDCCCDD), date = Color(0xFF473449), divider = Color(0xFF68576A),
    danger = Color(0xFFFFAA9D), shadow = Color(0xFF0D0A0D), completed = Color(0xFFC8ABFF),
    cover = Color(0xFFC7A7F3),
)

internal val LocalPalette = staticCompositionLocalOf { lightPalette }
internal val colors: Palette @Composable get() = LocalPalette.current

@Composable
internal fun Copy(
    text: String,
    modifier: Modifier = Modifier,
    size: Int = 16,
    lineHeight: Int = size + 6,
    color: Color = colors.ink,
    weight: FontWeight = FontWeight.Normal,
    display: Boolean = false,
    spacing: Float = 0f,
    align: TextAlign = TextAlign.Start,
    maxLines: Int = Int.MAX_VALUE,
) {
    BasicText(
        text, modifier,
        style = TextStyle(
            color = color, fontSize = size.sp, lineHeight = lineHeight.sp, textAlign = align,
            fontFamily = if (display) displayFont else FontFamily.SansSerif,
            fontWeight = if (display) FontWeight.Bold else weight,
            letterSpacing = spacing.sp,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        ),
        overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
        maxLines = maxLines,
    )
}

@Composable
internal fun Heading(text: String, modifier: Modifier = Modifier) {
    Copy(
        text, modifier.padding(top = 25.dp, bottom = 22.dp).semantics { heading() },
        size = 45, lineHeight = 55, display = true, spacing = -1.6f,
    )
}

@Composable
internal fun Glyph(name: String, size: Int = 22, color: Color = colors.ink) {
    BasicText(
        featherGlyphs.getValue(name).toString(), Modifier.clearAndSetSemantics { },
        style = TextStyle(
            color = color, fontSize = size.sp, lineHeight = size.sp,
            fontFamily = featherFont, platformStyle = PlatformTextStyle(includeFontPadding = false),
        ),
    )
}

@Composable
internal fun IconButton(
    icon: String,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier.size(48.dp).alpha(if (enabled) 1f else 0.35f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Glyph(icon, 20) }
}

@Composable
internal fun Panel(
    modifier: Modifier = Modifier,
    background: Color = colors.surface,
    ink: Color = colors.ink,
    radius: Dp = 9.dp,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Box(modifier.clip(shape).background(background).border(2.dp, ink, shape).padding(2.dp)) { content() }
}

internal fun Modifier.blockShadow(color: Color, depth: Dp, corner: Dp): Modifier =
    drawBehind {
        val offset = depth.toPx()
        drawRoundRect(color, topLeft = Offset(offset, offset),
            size = Size(size.width - offset, size.height - offset), cornerRadius = CornerRadius(corner.toPx()))
    }.padding(end = depth, bottom = depth)

@Composable
internal fun Action(
    label: String,
    modifier: Modifier = Modifier,
    icon: String? = null,
    primary: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
    accessibilityLabel: String? = null,
    onClick: () -> Unit,
) {
    val palette = colors
    val ink = if (primary) actionInk else if (danger) palette.danger else palette.ink
    val shape = RoundedCornerShape(7.dp)
    Box(if (primary) modifier.blockShadow(palette.shadow, 3.dp, 7.dp) else modifier) {
        val accessibility = if (accessibilityLabel == null) Modifier
            else Modifier.semantics { contentDescription = accessibilityLabel }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).alpha(if (enabled) 1f else 0.54f)
                .clip(shape).background(if (primary) actionYellow else palette.surface)
                .border(2.dp, ink, shape)
                .then(accessibility)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) Glyph(icon, 21, ink)
            Copy(label, size = 17, color = ink, weight = FontWeight.Bold, align = TextAlign.Center)
        }
    }
}

@Composable
internal fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    limit: Int,
    multiline: Boolean = false,
    numeric: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val palette = colors
    val shape = RoundedCornerShape(7.dp)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Copy(label, weight = FontWeight.Bold)
        BasicTextField(
            value = value,
            onValueChange = { input ->
                if (input.length <= limit && (!numeric || input.all { it.isDigit() })) onChange(input)
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = if (multiline) 132.dp else 48.dp)
                .clip(shape).background(palette.surface).border(2.dp, palette.ink, shape)
                .semantics { contentDescription = label },
            enabled = enabled,
            textStyle = TextStyle(
                color = palette.ink, fontSize = 16.sp, lineHeight = 22.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
                imeAction = if (multiline) ImeAction.Default else ImeAction.Done,
            ),
            singleLine = !multiline,
            cursorBrush = SolidColor(palette.ink),
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                    contentAlignment = if (multiline) Alignment.TopStart else Alignment.CenterStart,
                ) {
                    if (value.isEmpty() && !numeric) Copy(label, color = palette.secondary)
                    inner()
                }
            },
        )
    }
}

@Composable
internal fun Message(text: String, error: Boolean = false) {
    if (text.isNotEmpty()) {
        Copy(
            text, Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            color = if (error) colors.danger else colors.ink,
        )
    }
}

@Composable
internal fun KaizenDialog(
    label: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Panel(Modifier.padding(horizontal = 12.dp, vertical = 16.dp).widthIn(max = 420.dp)
            .fillMaxWidth().semantics { paneTitle = label }) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content()
            }
        }
    }
}

@Composable
internal fun DialogTitle(title: String, closeLabel: String, size: Int = 29, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Copy(title, Modifier.weight(1f).semantics { heading() }, size = size, lineHeight = size + 6,
            display = true, spacing = -0.7f)
        IconButton("x", closeLabel, onClick = onClose)
    }
}
