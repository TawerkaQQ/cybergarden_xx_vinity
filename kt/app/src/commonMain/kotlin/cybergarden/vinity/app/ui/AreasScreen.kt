package cybergarden.vinity.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cybergarden.vinity.api.AppRoute
import cybergarden.vinity.api.DemoCaseDto
import cybergarden.vinity.api.ProfileDto
import cybergarden.vinity.api.ProfileUploadDto
import cybergarden.vinity.api.SystemDto
import cybergarden.vinity.app.AppStore

@Composable
fun AreasScreen(store: AppStore) {
    val system = store.system
    val profiles = store.profiles
    val demo = store.demo
    val upload = store.profileUpload
    ScreenList("areas") {
        centered("head") {
            PageHeader(
                "Области",
                "Ядро одно для всех областей. Область — это профиль: таблицы, словари, единицы и правила проверки. Код под область не пишется.",
            )
        }
        system?.let { numbers -> centered("system") { SystemBlock(numbers) } }
        centered("profiles-head") { SectionHeader("Подключённые области") }
        centered("profiles") {
            AdaptiveGrid(profiles.size, 320.dp) { index, modifier -> ProfileCard(store, profiles[index], modifier) }
        }
        centered("connect-head") {
            SectionHeader("Подключить новую область", "Нужны два файла: profiles/<имя>/profile.json (таблицы, словари, единицы) и rules.md (правила для модели)")
        }
        centered("connect") { ConnectBlock(store, upload) }
        if (demo.isNotEmpty()) {
            centered("demo-head") {
                SectionHeader("Сценарий демо", "Заготовленные случаи из настоящих данных. Ссылка открывает приложение сразу на нужном месте.")
            }
            blockItems(demo.withIndex().toList(), key = { "demo-${it.index}" }) { (_, case), first -> DemoRow(store, case, first) }
        }
    }
}

@Composable
private fun SystemBlock(system: SystemDto) {
    val numbers = listOf(
        "Статей" to system.articles,
        "Страниц" to system.pages,
        "Сканов" to system.scans,
        "Значений" to system.values,
        "Замечаний" to system.issues,
        "Наборов" to system.sets,
        "Областей" to system.profiles,
    )
    Block {
        SectionHeader("Система в цифрах", "По всем наборам: эталон или результат системы, что есть")
        AdaptiveGrid(numbers.size, 140.dp, gap = 0.dp, equalHeight = true) { index, modifier ->
            Column(modifier.padding(12.dp), verticalArrangement = Arrangement.Bottom) {
                Muted(numbers[index].first, small = true)
                Text(grouped(numbers[index].second), Modifier.padding(top = 2.dp), style = T.big)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileCard(store: AppStore, profile: ProfileDto, modifier: Modifier) {
    val sets = store.sets.filter { it.ref.profile == profile.name }
    Block(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(profile.title, Modifier.weight(1f), style = T.h2)
            Badge(profile.name)
        }
        Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (profile.scope.isNotBlank()) Muted(profile.scope, small = true)
            BadgeGroup("Таблицы", profile.tables.map { "${it.name} · ${it.columns}" to null })
            BadgeGroup(
                "Показатели и единицы",
                profile.metrics.take(PROFILE_METRICS).map { metric ->
                    listOfNotNull(metric.code, unitText(metric.unit).ifEmpty { null }).joinToString(", ") to metric.title
                },
            )
            BadgeGroup("Проверки", profile.checks.filter { it.enabled }.map { profile.checkTitle(it.name) to it.rule })
            if (sets.isNotEmpty()) {
                FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    sets.forEach { summary ->
                        DefaultButton(
                            "Открыть набор ${setTitle(summary.ref, summary.uploads)} · ${plural(summary.articleCount, Articles)}",
                            { store.openSet(summary.ref, null) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BadgeGroup(title: String, badges: List<Pair<String, String?>>) {
    if (badges.isEmpty()) return
    Column {
        Muted(title, Modifier.padding(bottom = 4.dp), small = true)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            badges.forEach { (text, hint) -> Badge(text, hint = hint) }
        }
    }
}

@Composable
private fun ConnectBlock(store: AppStore, upload: ProfileUploadDto?) {
    val phone = LocalWidth.current == Width.PHONE
    val text = "Загрузите profile.json и rules.md новой области — она сразу появится в списке областей и в выборе при загрузке статей."
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Block {
            if (phone) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GlyphIcon(Glyph.PLUG, C.fg2, Modifier.padding(top = 3.dp))
                        Text(text, style = T.body)
                    }
                    DefaultButton("Выбрать файлы профиля", store::pickProfile)
                }
            } else {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlyphIcon(Glyph.PLUG, C.fg2)
                    Text(text, Modifier.weight(1f), style = T.body)
                    DefaultButton("Выбрать файлы профиля", store::pickProfile)
                }
            }
        }
        upload?.let { result ->
            val created = result.profile
            if (created != null) {
                Alert(Tone.OK, Glyph.CHECK) {
                    Text("Область «${created.title}» подключена", style = T.alert)
                    Text("Ни одной строки кода: только профиль. Новую область можно выбрать при загрузке статей.", style = T.body)
                }
            } else {
                Alert(Tone.ERR, Glyph.WARN) {
                    Text("Профиль не подключён", style = T.alert)
                    result.problem?.let { Text(it, style = T.body) }
                }
            }
        }
    }
}

@Composable
private fun DemoRow(store: AppStore, case: DemoCaseDto, first: Boolean) {
    Row(
        Modifier.fillMaxWidth().topDivider(!first, C.divider).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(case.title, style = T.body)
            Muted(case.detail, Modifier.subline(), small = true)
        }
        GhostButton(Glyph.LINK, "Скопировать ссылку", { AppRoute.parse(case.route)?.let { store.copyLink(it) } })
        DefaultButton("Открыть", { store.openRoute(case.route) }, glyph = Glyph.PLAY)
    }
}

private fun grouped(value: Int): String = value.toString().reversed().chunked(3).joinToString(" ").reversed()

private const val PROFILE_METRICS = 7
