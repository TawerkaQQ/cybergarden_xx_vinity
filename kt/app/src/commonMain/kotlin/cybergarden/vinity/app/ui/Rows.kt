package cybergarden.vinity.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cybergarden.vinity.api.IssueDto
import cybergarden.vinity.api.IssueReviewStateDto
import cybergarden.vinity.api.ProfileDto
import cybergarden.vinity.api.ValueDto
import cybergarden.vinity.api.ValueReviewStateDto
import cybergarden.vinity.app.AppStore
import cybergarden.vinity.app.Evidence

@Composable
fun ValueRow(
    store: AppStore,
    value: ValueDto,
    first: Boolean,
    showRecord: Boolean,
    extra: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val selected = (store.evidence as? Evidence.OnValue)?.let { it.article == value.publicationId && it.id == value.id } == true
    val source = remember { MutableInteractionSource() }
    val hover = hovered(source)
    val phone = LocalWidth.current == Width.PHONE
    val background = when {
        selected -> C.primary1
        hover -> C.surface2
        else -> C.surface
    }
    val rowModifier = modifier
        .fillMaxWidth()
        .background(background)
        .topDivider(!first, C.divider)
        .hoverable(source)
        .clickable(source, indication = null) { store.openValue(value.publicationId, value.id) }
        .hand()
        .alpha(if (value.duplicateOf != null) 0.72f else 1f)
        .padding(12.dp)
    if (phone) {
        Column(rowModifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCell(store, value, showRecord, Modifier.weight(1f))
                AmountCell(value)
            }
            ValueBadges(store, value, extra, Modifier.fillMaxWidth(), Alignment.Start)
        }
    } else {
        Row(rowModifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCell(store, value, showRecord, Modifier.weight(1f))
            AmountCell(value)
            ValueBadges(store, value, extra, Modifier.widthIn(min = 96.dp, max = 240.dp), Alignment.End)
            GlyphIcon(Glyph.RIGHT, C.fg3)
        }
    }
}

@Composable
private fun MetricCell(store: AppStore, value: ValueDto, showRecord: Boolean, modifier: Modifier) {
    val profile = store.profile
    val title = profile.metricTitle(value.metric)
    val sub = listOfNotNull(
        value.recordLabel.takeIf { showRecord },
        profile.aggregationTitle(value.aggregation),
        value.links.filter { it.concise && it.id != value.record }.joinToString(" · ") { it.label }.ifEmpty { null },
        value.pageAndPlace(),
    ).joinToString(" · ")
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = T.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            value.metric?.takeIf { it != title }?.let { Text(it, Modifier.padding(start = 6.dp), style = T.body, color = C.fg2, maxLines = 1) }
        }
        Text(sub, Modifier.subline(), style = T.small, color = C.fg2, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AmountCell(value: ValueDto) {
    val review = value.review
    val written = value.written()
    Column(horizontalAlignment = Alignment.End) {
        Text(
            value.fixedAmount(),
            style = T.bodyMedium.copy(textDecoration = if (review?.state == ValueReviewStateDto.REJECTED) TextDecoration.LineThrough else null),
            color = if (review?.state == ValueReviewStateDto.REJECTED) C.fg2 else C.fg,
            textAlign = TextAlign.End,
        )
        when {
            review?.state == ValueReviewStateDto.FIXED -> Text(value.amount(), style = T.small.copy(textDecoration = TextDecoration.LineThrough), color = C.fg2)
            written != null -> Text("в статье «$written»", style = T.small, color = C.fg2, textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ValueBadges(store: AppStore, value: ValueDto, extra: Boolean, modifier: Modifier, alignment: Alignment.Horizontal) {
    val issues = store.activeIssueCount(value)
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp, alignment), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        value.review?.let { Badge(it.state.title(), it.state.tone()) }
        if (issues > 0) Badge("Замечание", Tone.ERR)
        if (extra && value.quote.status.found()) Badge("Сверх эталона", Tone.INFO, hint = BeyondGoldHint)
        else if (extra) Badge("Лишнее", Tone.WARN, hint = "У экспертов этого значения нет")
        if (value.duplicateOf != null) Badge("Повтор", hint = "То же число, найденное в другом месте статьи")
        value.repeats.firstOrNull()?.let { Badge("Есть в ${it.publicationId}", Tone.INFO, hint = RepeatHint) }
    }
}

@Composable
fun MissedValueRow(profile: ProfileDto?, value: ValueDto, first: Boolean, showRecord: Boolean) {
    val sub = listOfNotNull(value.recordLabel.takeIf { showRecord }, profile.aggregationTitle(value.aggregation), value.pageAndPlace()).joinToString(" · ")
    WithHint("Эксперты нашли это значение, система — нет") {
        Row(
            Modifier.fillMaxWidth().background(C.surface2).topDivider(!first, C.divider).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Row {
                    val title = profile.metricTitle(value.metric)
                    Text(title, style = T.body, color = C.fg2)
                    value.metric?.takeIf { it != title }?.let { Text(it, Modifier.padding(start = 6.dp), style = T.body, color = C.fg2) }
                }
                Text(sub, style = T.small, color = C.fg2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(value.amount(), style = T.body, color = C.fg2)
            Row(Modifier.widthIn(min = 96.dp), horizontalArrangement = Arrangement.End) { Badge("Пропущено", Tone.MISS) }
            Spacer(Modifier.width(16.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IssueCard(store: AppStore, issue: IssueDto, gold: Boolean?, withArticle: Boolean = false) {
    val profile = store.profile
    val off = issue.review?.state == IssueReviewStateDto.DISMISSED
    Alert(if (off) Tone.OFF else Tone.ERR, Glyph.WARN) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(profile.checkTitle(issue.check), style = T.alert, color = if (off) C.fg2 else C.fg, modifier = Modifier.weight(1f, fill = false))
            if (withArticle) Text(issue.publicationId, Modifier.subline(), style = T.small, color = C.fg2)
        }
        val badges = issue.review != null || gold != null
        if (badges) {
            FlowRow(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                issue.review?.let { Badge(it.state.title(), if (it.state == IssueReviewStateDto.CONFIRMED) Tone.OK else Tone.NEUTRAL) }
                when (gold) {
                    true -> Badge("Есть у экспертов")
                    false -> Badge("Нашли только мы", Tone.PRI, hint = "В эталоне экспертов такого замечания нет")
                    null -> Unit
                }
            }
        }
        issue.description?.let { Text(it, style = T.body, color = if (off) C.fg2 else C.fg) }
        issue.review?.reason?.takeIf { off }?.let { Text("Причина: $it", Modifier.subline(), style = T.small, color = C.fg2) }
        profile.checkRule(issue.check)?.let { Text("Правило: $it", style = T.small, color = C.fg2) }
        if (issue.values.isNotEmpty()) {
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                issue.values.take(ISSUE_CHIPS).forEach { value ->
                    Chip(value.chip(), pressed = false, onClick = { store.openValue(issue.publicationId, value.id) })
                }
                if (issue.values.size > ISSUE_CHIPS) Text("и ещё ${issue.values.size - ISSUE_CHIPS}", style = T.small, color = C.fg2, modifier = Modifier.align(Alignment.CenterVertically))
            }
        }
        Row(Modifier.padding(top = 8.dp)) {
            DefaultButton("Разобрать с цитатами", { store.openIssue(issue.publicationId, issue.id) }, glyph = Glyph.TARGET)
        }
    }
}

@Composable
fun MissedIssueCard(profile: ProfileDto?, issue: IssueDto, withArticle: Boolean = false) {
    Alert(Tone.NEUTRAL, Glyph.WARN, dashed = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(profile.checkTitle(issue.check), style = T.alert)
            if (withArticle) Text(issue.publicationId, Modifier.subline(), style = T.small, color = C.fg2)
        }
        Row(Modifier.padding(vertical = 4.dp)) { Badge("Есть у экспертов, система пропустила", Tone.MISS) }
        issue.description?.let { Text(it, style = T.body) }
    }
}

@Composable
fun ArticleLabel(id: String, title: String?) {
    Column {
        Text(id, style = T.body.copy(fontWeight = FontWeight.Medium))
        title?.let { Text(it, style = T.small, color = C.fg2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.subline().widthIn(max = 220.dp)) }
    }
}

private const val ISSUE_CHIPS = 8
