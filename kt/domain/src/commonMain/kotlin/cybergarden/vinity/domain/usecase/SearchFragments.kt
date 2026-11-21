package cybergarden.vinity.domain.usecase

import kotlin.math.ln

data class Fragment(val publicationId: String, val page: Int?, val text: String, val valueId: String?)

data class FoundFragment(val fragment: Fragment, val score: Double)

class FragmentIndex(private val fragments: List<Fragment>) {

    private val terms: List<Map<String, Int>> = fragments.map { fragment -> tokens(fragment.text).groupingBy { it }.eachCount() }
    private val lengths: List<Int> = terms.map { it.values.sum() }
    private val averageLength: Double = lengths.average().takeIf { it > 0 } ?: 1.0
    private val frequency: Map<String, Int> = terms.flatMap { it.keys }.groupingBy { it }.eachCount()

    val size: Int get() = fragments.size

    fun search(query: String, limit: Int, perPublication: Int): List<FoundFragment> {
        val wanted = tokens(query).toSet()
        if (wanted.isEmpty()) return emptyList()
        val weights = wanted.associateWith { term ->
            val documents = frequency[term] ?: 0
            ln(1 + (fragments.size - documents + 0.5) / (documents + 0.5))
        }
        val scored = fragments.indices.mapNotNull { index ->
            val counts = terms[index]
            val score = wanted.sumOf { term ->
                val count = counts[term] ?: return@sumOf 0.0
                val norm = count + K1 * (1 - B + B * lengths[index] / averageLength)
                (weights[term] ?: 0.0) * count * (K1 + 1) / norm
            }
            if (score > 0) FoundFragment(fragments[index], score) else null
        }
        val taken = mutableMapOf<String, Int>()
        return scored.sortedByDescending { it.score }.filter { found ->
            val count = taken[found.fragment.publicationId] ?: 0
            (count < perPublication).also { if (it) taken[found.fragment.publicationId] = count + 1 }
        }.take(limit)
    }

    companion object {
        private const val K1 = 1.2
        private const val B = 0.75
        private const val MIN_TOKEN = 2
        private val SEPARATOR = Regex("[^\\p{L}\\p{N}]+")

        fun tokens(text: String): List<String> =
            text.lowercase().split(SEPARATOR).filter { it.length >= MIN_TOKEN || it.firstOrNull()?.isDigit() == true }
    }
}
