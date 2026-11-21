package cybergarden.vinity.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cybergarden.vinity.api.AppScreen
import cybergarden.vinity.api.SetSummaryDto
import cybergarden.vinity.api.SourceDto
import cybergarden.vinity.app.AppStore

@Composable
fun TopBar(store: AppStore) {
    val width = LocalWidth.current
    val inline = width == Width.WIDE || width == Width.NARROW
    val phone = width == Width.PHONE
    Column(Modifier.fillMaxWidth().background(C.surface)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = if (inline) 0.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (phone) 8.dp else 16.dp),
        ) {
            Brand(store)
            SetPicker(store, Modifier)
            if (inline) MainTabs(store, Modifier)
            Spacer(Modifier.weight(1f))
            UploadChip(store)
            if (phone) SquareButton(Glyph.PLUS, "Загрузить PDF", store::pickArticles) else PrimaryButton("Загрузить PDF", store::pickArticles, glyph = Glyph.PLUS)
        }
        if (!inline) MainTabs(store, Modifier.fillMaxWidth().padding(horizontal = 4.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.divider))
    }
}

@Composable
private fun Brand(store: AppStore) {
    Row(
        Modifier.clip(R8).press(onClick = store::goHome).padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(24.dp).clip(R6).background(C.primary), contentAlignment = Alignment.Center) {
            Text("V", style = T.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = Color.White)
        }
        if (LocalWidth.current != Width.PHONE) Text("Vinity", style = T.h2)
    }
}

@Composable
private fun MainTabs(store: AppStore, modifier: Modifier) {
    val selected = if (store.screen == AppScreen.ARTICLE) AppScreen.ARTICLES else store.screen
    val tabs = listOf(
        TabSpec(AppScreen.ARTICLES, "Статьи", store.set?.articles?.size),
        TabSpec(AppScreen.VALUES, "Все значения", store.set?.let { store.totalValues }),
        TabSpec(AppScreen.OVERVIEW, "Обзор"),
        TabSpec(AppScreen.QUALITY, "Качество"),
        TabSpec(AppScreen.AREAS, "Области"),
    )
    UnderlineTabs(tabs, selected, store::openScreen, modifier.horizontalScroll(rememberScrollState()), vertical = if (LocalWidth.current == Width.WIDE || LocalWidth.current == Width.NARROW) 17.dp else 8.dp)
}

@Composable
private fun SetPicker(store: AppStore, modifier: Modifier) {
    val current = store.set ?: return
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier.clip(R8).background(C.surface).border(1.dp, C.border, R8).press { open = !open }.padding(start = 12.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "${current.profile.title} · ${setTitle(current.ref, current.uploads)}",
                Modifier.widthIn(max = if (LocalWidth.current == Width.PHONE) 170.dp else 320.dp),
                style = T.body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (store.hasUnseenElsewhere()) NewDot("В другом наборе есть новые статьи")
            GlyphIcon(Glyph.DOWN, C.fg2)
        }
        DropdownMenu(open, onDismissRequest = { open = false }, modifier = Modifier.background(C.surface).heightIn(max = 560.dp)) {
            store.sets.forEach { summary ->
                summary.choices().forEach { source ->
                    val checked = summary.ref == current.ref && (source == null || source == current.source)
                    SetChoice(store, summary, source, checked) {
                        open = false
                        store.openSet(summary.ref, source)
                    }
                }
            }
        }
    }
}

private fun SetSummaryDto.choices(): List<SourceDto?> = if (sources.size > 1) sources else listOf(null)

@Composable
private fun SetChoice(store: AppStore, summary: SetSummaryDto, source: SourceDto?, checked: Boolean, onClick: () -> Unit) {
    val detail = listOfNotNull(
        plural(summary.articleCount, Articles),
        (source ?: summary.sources.singleOrNull())?.title() ?: if (summary.sources.isEmpty()) "только PDF" else null,
    ).joinToString(" · ")
    Row(
        Modifier
            .padding(horizontal = 4.dp, vertical = 1.dp)
            .widthIn(min = 300.dp)
            .clip(R6)
            .background(if (checked) C.primary1 else Color.Transparent)
            .press(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("${summary.profileTitle} · ${setTitle(summary.ref, summary.uploads)}", style = T.body, color = if (checked) C.primaryInk else C.fg, maxLines = 1)
        if (store.setHasUnseen(summary.ref)) NewDot("Есть новые статьи")
        Spacer(Modifier.weight(1f))
        Text(detail, Modifier.padding(start = 16.dp), style = T.small, color = C.fg2, maxLines = 1)
    }
}

@Composable
fun NewDot(hint: String) {
    WithHint(hint) { Box(Modifier.size(8.dp).clip(CircleShape).background(C.primary)) }
}

@Composable
private fun UploadChip(store: AppStore) {
    val active = store.activeUploads
    if (active.isEmpty()) return
    val sent = active.sumOf { it.sent }
    val total = active.sumOf { it.total }.coerceAtLeast(1L)
    DefaultButton(
        "Загрузка: ${active.size} · ${(sent * 100 / total).toInt()} %",
        { store.openScreen(AppScreen.ARTICLES) },
        leading = { Spinner() },
    )
}
