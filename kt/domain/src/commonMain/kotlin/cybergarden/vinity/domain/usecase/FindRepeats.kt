package cybergarden.vinity.domain.usecase

import cybergarden.vinity.domain.model.Record
import cybergarden.vinity.domain.model.RecordKey
import cybergarden.vinity.domain.model.Status
import cybergarden.vinity.domain.model.Submission
import cybergarden.vinity.domain.model.ValuesSpec

data class Overlap(val first: String, val second: String, val values: Int) {
    fun other(publicationId: String): String? = when (publicationId) {
        first -> second
        second -> first
        else -> null
    }
}

class Repeats(private val links: Map<RecordKey, List<Record>>, val overlaps: List<Overlap>) {
    val values: Int get() = links.size

    fun of(row: Record): List<Record> = links[row.key()].orEmpty()

    fun of(publicationId: String): List<Overlap> = overlaps.filter { it.other(publicationId) != null }
}

class FindRepeats {

    private data class Key(val metric: String, val number: Double, val unit: String)

    operator fun invoke(submission: Submission): Repeats {
        val spec = submission.profile.values
        val byKey = submission.values.asSequence()
            .filter { it[spec.duplicateOf] == null && (it[spec.status] ?: Status.EXTRACTED) == Status.EXTRACTED }
            .mapNotNull { row -> key(row, spec)?.let { it to row } }
            .groupBy({ it.first }, { it.second })
            .filterValues { rows -> rows.distinctBy { it.publicationId }.size > 1 }
        val shared = mutableMapOf<Pair<String, String>, MutableSet<Key>>()
        byKey.forEach { (key, rows) ->
            val publications = rows.map { it.publicationId }.distinct().sorted()
            publications.forEachIndexed { index, first ->
                publications.drop(index + 1).forEach { second -> shared.getOrPut(first to second) { mutableSetOf() } += key }
            }
        }
        val strong = shared.filterValues { it.size >= MIN_SHARED }
        val links = mutableMapOf<RecordKey, MutableList<Record>>()
        strong.forEach { (pair, keys) ->
            keys.forEach { key ->
                val rows = byKey.getValue(key)
                val first = rows.filter { it.publicationId == pair.first }
                val second = rows.filter { it.publicationId == pair.second }
                first.forEach { links.getOrPut(it.key()) { mutableListOf() } += second }
                second.forEach { links.getOrPut(it.key()) { mutableListOf() } += first }
            }
        }
        val overlaps = strong.map { (pair, keys) -> Overlap(pair.first, pair.second, keys.size) }.sortedByDescending { it.values }
        return Repeats(links, overlaps)
    }

    private fun key(row: Record, spec: ValuesSpec): Key? {
        val metric = row[spec.metric]?.lowercase()?.filter { it.isLetterOrDigit() }?.takeIf { it.isNotEmpty() } ?: return null
        val number = row.number(spec.value) ?: return null
        return Key(metric, number, row[spec.unit].orEmpty().lowercase())
    }

    private companion object {
        const val MIN_SHARED = 3
    }
}
