package com.owenleung.dewey.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.owenleung.dewey.R
import com.owenleung.dewey.engine.EngineError
import com.owenleung.dewey.store.Fmt
import com.owenleung.dewey.store.ItemType
import com.owenleung.dewey.ui.theme.C
import com.owenleung.dewey.ui.theme.Theme
import java.util.Date

// 4c · the states every screen needs, and the small parts every screen uses.

/** A white rounded card with the border colour. */
fun Modifier.card(c: com.owenleung.dewey.ui.theme.DeweyColors): Modifier =
    this.clip(RoundedCornerShape(Theme.Radius.card)).background(c.card).border(1.dp, c.border, RoundedCornerShape(Theme.Radius.card))

@Composable
fun Card(modifier: Modifier = Modifier, padding: Dp = 0.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().card(C).padding(padding), content = content)
}

@Composable
fun Divider() = HorizontalDivider(color = C.divider, thickness = 1.dp)

/** First load: grey skeleton rows. */
@Composable
fun SkeletonRows(count: Int = 5) {
    Card {
        repeat(count) { i ->
            if (i > 0) Divider()
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(C.divider))
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.width((160 + (i * 37) % 110).dp).height(13.dp).clip(RoundedCornerShape(Theme.Radius.tag)).background(C.divider))
                    Box(Modifier.width(110.dp).height(10.dp).clip(RoundedCornerShape(Theme.Radius.tag)).background(C.divider.copy(alpha = 0.7f)))
                }
            }
        }
    }
}

/** Offline / stale: "Offline · as of 12 min ago". */
@Composable
fun AsOfBanner(date: Date?, error: EngineError?) {
    if (date == null || error == null) return
    Row(
        Modifier.clip(CircleShape).background(C.tagSoft).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (error == EngineError.RateLimit) Icons.Outlined.HourglassEmpty else Icons.Outlined.CloudOff, null,
            tint = C.secondary, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text("${if (error == EngineError.RateLimit) "Slowed down" else "Offline"} · as of ${Fmt.ago(date)}",
            style = Theme.footnote.copy(fontWeight = FontWeight.Medium), color = C.secondary)
    }
}

/** An error with nothing saved to show: never a blank screen. */
@Composable
fun ErrorState(error: EngineError, schoologyUrl: String? = null, retry: (() -> Unit)? = null, open: ((String) -> Unit)? = null) {
    val (icon, title) = when (error) {
        is EngineError.Network -> Icons.Outlined.CloudOff to "Offline"
        is EngineError.Parse -> Icons.Outlined.FindInPage to "Couldn't read this page"
        EngineError.RateLimit -> Icons.Outlined.HourglassEmpty to "Too many requests"
        else -> Icons.Outlined.ErrorOutline to "Something went wrong"
    }
    Card(padding = 24.dp) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = C.tertiary, modifier = Modifier.size(36.dp))
            Text(title, style = Theme.headline, color = C.text)
            Text(error.text, style = Theme.subhead, color = C.secondary, textAlign = TextAlign.Center)
            if (error is EngineError.Parse && schoologyUrl != null && open != null) {
                PillButton("Open this page in Schoology", external = true) { open(schoologyUrl) }
            } else if (retry != null) {
                PillButton("Try again", onClick = retry)
            }
        }
    }
}

@Composable
fun EmptyState(title: String, detail: String? = null, art: Boolean = false, icon: ImageVector = Icons.Outlined.TaskAlt) {
    Card(padding = 28.dp) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (art) Image(painterResource(R.drawable.dewey_caught_up), null, Modifier.size(72.dp))
            else Icon(icon, null, tint = C.good, modifier = Modifier.size(36.dp))
            Text(title, style = Theme.headline, color = C.text)
            if (detail != null) Text(detail, style = Theme.subhead, color = C.secondary, textAlign = TextAlign.Center)
        }
    }
}

/** The large-title header used by the tab screens: date line + title + trailing buttons. */
@Composable
fun ScreenHeader(title: String, kicker: String? = null, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Theme.Space.xs), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (kicker != null) Text(kicker.uppercase(), style = Theme.caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp), color = C.secondary)
            Text(title, style = Theme.largeTitle, color = C.text)
        }
        trailing()
    }
}

/** "SUBMISSION", "INSTRUCTIONS": the small label at the top of a card. */
@Composable
fun SectionLabel(text: String) {
    Text(text, style = Theme.caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp), color = C.tertiary)
}

/** A card with a small label on top. */
@Composable
fun LabeledCard(label: String, content: @Composable ColumnScope.() -> Unit) {
    Card(padding = 16.dp) {
        SectionLabel(label)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

/** HW / TEST / TASK / CW, as a vertical bar under the To Do checkbox. A test stands out: black instead of grey. */
@Composable
fun TypeBar(type: ItemType, modifier: Modifier = Modifier) {
    val exam = type == ItemType.EXAM
    Box(
        modifier.width(18.dp).heightIn(min = 38.dp).clip(RoundedCornerShape(Theme.Radius.tag)).background(if (exam) C.text else C.tagSoft)
            .semantics { contentDescription = type.label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            type.label, maxLines = 1, softWrap = false,
            style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.6.sp),
            color = if (exam) C.card else C.secondary,
            modifier = Modifier.rotate(-90f).layout { m, c ->
                // Measure unbounded, then swap width and height for the rotation.
                val p = m.measure(Constraints())
                layout(p.height, p.width) { p.place(-(p.width - p.height) / 2, (p.width - p.height) / 2) }
            },
        )
    }
}

/** HW / TEST / TASK / CW, inline. */
@Composable
fun TypeTag(type: ItemType) {
    val exam = type == ItemType.EXAM
    Text(
        type.label,
        style = Theme.caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
        color = if (exam) C.card else C.secondary,
        modifier = Modifier.clip(RoundedCornerShape(Theme.Radius.tag)).background(if (exam) C.text else C.tagSoft).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** A pill button. `external` adds the ↗ arrow for anything that leaves the app. */
@Composable
fun PillButton(title: String, color: Color = C.accent, filled: Boolean = false, external: Boolean = false, modifier: Modifier = Modifier,
               onClick: () -> Unit) {
    Row(
        modifier.heightIn(min = 30.dp).clip(CircleShape).background(if (filled) color else color.copy(alpha = 0.1f))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = Theme.Space.m, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = Theme.footnote.copy(fontWeight = FontWeight.SemiBold), color = if (filled) C.onAccent else color, maxLines = 1)
        if (external) {
            Spacer(Modifier.width(3.dp))
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, tint = if (filled) C.onAccent else color, modifier = Modifier.size(13.dp))
        }
    }
}

/** A full-width capsule button, 48 dp tall. */
@Composable
fun WideButton(title: String, icon: ImageVector? = null, filled: Boolean = true, color: Color = C.accent, external: Boolean = false,
               modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).clip(CircleShape).background(if (filled) color else C.card)
            .then(if (filled) Modifier else Modifier.border(BorderStroke(1.dp, C.border), CircleShape))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        val tint = if (filled) C.onAccent else color
        if (icon != null) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)) }
        Text(title, style = Theme.headline, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (external) { Spacer(Modifier.width(6.dp)); Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, tint = tint, modifier = Modifier.size(16.dp)) }
    }
}

/** A score's effect on the grade: red down, green up, grey when it didn't move ("+0.00%"). */
@Composable
fun impactColor(d: Double): Color = if (Fmt.unmoved(d)) C.tertiary else if (d < 0) C.late else C.good

/** The small "NEW" capsule on a score the student hasn't looked at. */
@Composable
fun NewTag() {
    Text("NEW", style = Theme.caption.copy(fontWeight = FontWeight.Bold), color = C.onAccent,
        modifier = Modifier.clip(CircleShape).background(C.accent).padding(horizontal = 5.dp, vertical = 1.dp))
}

@Composable
fun CountBadge(text: String, modifier: Modifier = Modifier) {
    Text(text, style = Theme.caption.copy(fontWeight = FontWeight.SemiBold), color = C.onAccent,
        modifier = modifier.clip(CircleShape).background(C.accent).padding(horizontal = 6.dp, vertical = 1.dp))
}

/** Markdown from the engine (instructions, pages, posts, messages), links tappable. */
@Composable
fun MarkdownText(md: String, style: TextStyle = Theme.subhead, onLink: (String) -> Unit) {
    val text = markdown(md, C.accent, onLink)
    SelectionContainer { Text(text, style = style, color = C.text) }
}

/** A small Markdown reader: headings, bullets, **bold**, *italic*, `code`, [links](url). */
fun markdown(md: String, linkColor: Color, onLink: (String) -> Unit): AnnotatedString = buildAnnotatedString {
    val lines = md.trim().lines()
    lines.forEachIndexed { i, raw ->
        var line = raw
        val heading = Regex("""^(#{1,6})\s+(.*)$""").find(line)
        val bullet = Regex("""^\s*[-*+]\s+(.*)$""").find(line)
        val numbered = Regex("""^\s*(\d+)[.)]\s+(.*)$""").find(line)
        when {
            heading != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { inline(heading.groupValues[2], linkColor, onLink) }
            bullet != null -> { append("•  "); inline(bullet.groupValues[1], linkColor, onLink) }
            numbered != null -> { append("${numbered.groupValues[1]}.  "); inline(numbered.groupValues[2], linkColor, onLink) }
            line.trim().startsWith(">") -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { inline(line.trim().removePrefix(">").trim(), linkColor, onLink) }
            else -> { line = line.replace(Regex("""\\([\\`*_{}\[\]()#+\-.!])"""), "$1"); inline(line, linkColor, onLink) }
        }
        if (i < lines.size - 1) append("\n")
    }
}

private val inlineRe = Regex("""\[([^\]]*)\]\(([^)\s]+)(?:\s+"[^"]*")?\)|\*\*([^*]+)\*\*|__([^_]+)__|\*([^*\s][^*]*)\*|_([^_\s][^_]*)_|`([^`]+)`|(https?://[^\s<)]+)""")

private fun AnnotatedString.Builder.inline(s: String, linkColor: Color, onLink: (String) -> Unit) {
    var at = 0
    for (m in inlineRe.findAll(s)) {
        append(s.substring(at, m.range.first))
        val g = m.groupValues
        when {
            m.groups[1] != null -> link(g[1].ifEmpty { g[2] }, g[2], linkColor, onLink)
            m.groups[3] != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { inline(g[3], linkColor, onLink) }
            m.groups[4] != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { inline(g[4], linkColor, onLink) }
            m.groups[5] != null -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { inline(g[5], linkColor, onLink) }
            m.groups[6] != null -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { inline(g[6], linkColor, onLink) }
            m.groups[7] != null -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(g[7]) }
            m.groups[8] != null -> link(g[8], g[8], linkColor, onLink)
        }
        at = m.range.last + 1
    }
    append(s.substring(at))
}

private fun AnnotatedString.Builder.link(text: String, url: String, color: Color, onLink: (String) -> Unit) {
    withLink(LinkAnnotation.Clickable(url, TextLinkStyles(SpanStyle(color = color, textDecoration = TextDecoration.Underline))) { onLink(url) }) {
        append(text)
    }
}

val Zero = PaddingValues(0.dp)
