package cybergarden.vinity.data.text

import cybergarden.vinity.domain.port.TextNormalizer
import java.text.Normalizer

object QuoteNormalizer : TextNormalizer {

    private const val SOFT_HYPHEN = "­"
    private val wrappedWord = Regex("(?U)(?<=\\w)-\\s*\\r?\\n\\s*(?=\\w)")
    private val spaces = Regex("(?U)\\s+")

    override fun normalize(text: String): String {
        val composed = Normalizer.normalize(text, Normalizer.Form.NFKC).replace(SOFT_HYPHEN, "")
        return spaces.replace(wrappedWord.replace(composed, ""), " ").trim()
    }
}
