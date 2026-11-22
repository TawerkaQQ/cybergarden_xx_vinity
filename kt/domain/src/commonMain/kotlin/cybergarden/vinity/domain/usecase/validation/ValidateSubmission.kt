package cybergarden.vinity.domain.usecase.validation

import cybergarden.vinity.domain.model.Submission
import cybergarden.vinity.domain.model.Violation
import cybergarden.vinity.domain.port.PageTextSource
import cybergarden.vinity.domain.port.TextNormalizer

class ValidateSubmission(private val rules: List<SubmissionRule>) {

    operator fun invoke(submission: Submission): List<Violation> = rules.flatMap { it.check(submission) }

    companion object {
        fun of(pages: PageTextSource?, normalizer: TextNormalizer, articles: Set<String>?) = ValidateSubmission(
            listOfNotNull(
                IdentityRule(),
                ScopeRule(),
                VocabularyRule(),
                ReferenceRule(),
                ValueRule(),
                DuplicateRule(),
                pages?.let { FragmentRule(it, normalizer) },
                articles?.let(::CoverageRule),
            ),
        )
    }
}
