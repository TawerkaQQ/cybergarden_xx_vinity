package cybergarden.vinity.api

object ApiRoutes {
    const val PROFILE_PARAMETER = "profile"
    const val SET_PARAMETER = "set"
    const val PUBLICATION_PARAMETER = "publication"
    const val PAGE_PARAMETER = "page"
    const val FRAGMENT_PARAMETER = "fragment"
    const val STRICT_PARAMETER = "strict"

    const val DATASETS = "/api/datasets"
    const val DATASET = "$DATASETS/{$PROFILE_PARAMETER}/{$SET_PARAMETER}"
    const val SCORE = "$DATASET/score"
    const val PAGE = "$DATASET/publications/{$PUBLICATION_PARAMETER}/pages/{$PAGE_PARAMETER}"

    fun dataset(ref: DatasetRef) = "$DATASETS/${ref.path}"
    fun score(ref: DatasetRef) = "${dataset(ref)}/score"
    fun page(ref: DatasetRef, publicationId: String, page: Int) = "${dataset(ref)}/publications/$publicationId/pages/$page"
}
