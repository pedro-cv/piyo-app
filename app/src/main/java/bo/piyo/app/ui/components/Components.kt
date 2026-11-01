package bo.piyo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bo.piyo.app.domain.LineColors
import bo.piyo.app.domain.model.Line
import bo.piyo.app.domain.model.LineMode
import bo.piyo.app.ui.theme.DurationStyle
import bo.piyo.app.ui.theme.LineNumberStyle
import bo.piyo.app.ui.theme.LocalPiyo
import bo.piyo.app.ui.theme.Roboto

/** Base components from the canvas: chip, banner, card, pill button, list rows, segments. */

/** Square chip for micros, pill for trufis (named services). */
@Composable
fun LineChip(line: Line, small: Boolean = false, modifier: Modifier = Modifier, color: Color = LineColors.colorOf(line)) {
    val trufi = line.lineMode == LineMode.TRUFI
    val h = if (small) 26.dp else 36.dp
    val minW = if (small) 36.dp else 46.dp
    val radius = if (trufi) 999.dp else if (small) 7.dp else 10.dp
    Box(
        modifier = modifier
            .height(h)
            .defaultMinSize(minWidth = minW)
            .clip(RoundedCornerShape(radius))
            .background(color)
            .padding(horizontal = if (small) 7.dp else 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = line.display,
            style = LineNumberStyle.copy(fontSize = if (trufi) 13.sp else if (small) 14.sp else 18.sp),
            color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun OfflineBanner(text: String, modifier: Modifier = Modifier) {
    val t = LocalPiyo.current
    Row(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(t.offline).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Rounded.WifiOff, null, tint = t.offlineInk, modifier = Modifier.size(20.dp))
        Text(text, color = t.offlineInk, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/** Uppercase 12 px section label. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(), modifier = modifier.padding(top = 8.dp, start = 2.dp),
        fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.7.sp, color = LocalPiyo.current.muted,
    )
}

@Composable
fun PiyoCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, selected: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val t = LocalPiyo.current
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(t.surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) t.primary else t.line, shape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
fun PillButton(text: String, selected: Boolean = false, onClick: () -> Unit, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    val t = LocalPiyo.current
    Row(
        modifier = modifier
            .height(36.dp)
            .clip(CircleShape)
            .background(if (selected) t.primary else t.surface)
            .border(1.dp, if (selected) t.primary else t.line, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, null, modifier = Modifier.size(18.dp), tint = if (selected) t.onPrimary else t.ink)
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (selected) t.onPrimary else t.ink, maxLines = 1)
    }
}

/** 48 dp pill. primary = filled green; ghost = outlined. */
@Composable
fun PiyoButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = true, enabled: Boolean = true, icon: ImageVector? = null) {
    val t = LocalPiyo.current
    val bg = if (primary) t.primary else t.surface
    val fg = if (primary) t.onPrimary else t.ink
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(CircleShape)
            .background(if (enabled) bg else bg.copy(alpha = 0.45f))
            .let { if (primary) it else it.border(1.dp, t.line, CircleShape) }
            .clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, color = fg, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

/** 44 dp round icon button (".ib" in the canvas). */
@Composable
fun IconCircleButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = false, tint: Color = LocalPiyo.current.ink) {
    val t = LocalPiyo.current
    Box(
        modifier = modifier.size(44.dp).clip(CircleShape).background(if (filled) t.surface2 else Color.Transparent).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(24.dp)) }
}

/** Walk segment "🚶 80 m" with the walk icon. */
@Composable
fun WalkSegment(meters: Int) {
    val t = LocalPiyo.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Icon(Icons.Rounded.DirectionsWalk, null, tint = t.muted, modifier = Modifier.size(18.dp))
        Text("$meters m", fontSize = 13.sp, color = t.muted)
    }
}

@Composable
fun SegmentArrow() { Text("›", color = LocalPiyo.current.line, fontSize = 16.sp) }

/** Big duration "25 min". */
@Composable
fun Duration(minutes: Int, suffix: String = "min") {
    val t = LocalPiyo.current
    Row(verticalAlignment = Alignment.Bottom) {
        Text(minutes.toString(), style = DurationStyle, color = t.ink)
        Spacer(Modifier.width(4.dp))
        Text(suffix, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = t.muted, modifier = Modifier.padding(bottom = 3.dp))
    }
}

/** Grouped list container (Favoritos, Datos). */
@Composable
fun GroupedList(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val t = LocalPiyo.current
    Column(modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(t.surface).border(1.dp, t.line, RoundedCornerShape(16.dp)), content = content)
}

@Composable
fun ListRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    divider: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = LocalPiyo.current
    Column {
        Row(
            modifier = modifier.fillMaxWidth().heightIn(min = 56.dp).let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            leading?.invoke()
            Column(Modifier.weight(1f), content = content)
            trailing?.invoke(this)
        }
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).padding(start = 14.dp).background(t.line))
    }
}

/** Round 36 dp icon container used as a list leading element (".ic"). */
@Composable
fun LeadingIcon(icon: ImageVector, tint: Color = LocalPiyo.current.primary, bg: Color = LocalPiyo.current.primarySoft) {
    Box(Modifier.size(36.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/**
 * Loading placeholder for a list: [rows] rows with a round leading block and two text bars that pulse
 * between 35 % and 80 % opacity. Same geometry as [ListRow] so the content does not jump when it lands.
 */
@Composable
fun SkeletonList(rows: Int = 4, modifier: Modifier = Modifier) {
    val t = LocalPiyo.current
    val pulse = rememberInfiniteTransition(label = "skeleton")
    val alpha by pulse.animateFloat(
        initialValue = 0.35f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "alpha",
    )
    val bone = t.surface2.copy(alpha = alpha)
    GroupedList(modifier) {
        repeat(rows) { i ->
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(bone))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.fillMaxWidth(if (i % 2 == 0) 0.62f else 0.48f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(bone))
                    Box(Modifier.fillMaxWidth(0.80f).height(11.dp).clip(RoundedCornerShape(6.dp)).background(bone))
                }
            }
            if (i < rows - 1) Box(Modifier.fillMaxWidth().height(1.dp).padding(start = 14.dp).background(t.line))
        }
    }
}

@Composable
fun Note(text: String, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Start) {
    Text(text, modifier = modifier, fontSize = 12.sp, lineHeight = 17.sp, color = LocalPiyo.current.muted, textAlign = align, fontFamily = Roboto)
}

