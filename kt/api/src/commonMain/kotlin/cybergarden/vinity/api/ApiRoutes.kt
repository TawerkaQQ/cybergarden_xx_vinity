package cybergarden.vinity.api

object ApiRoutes {
    const val DATASETS = "/api/datasets"
    const val DATASET = "$DATASETS/{dataset}"
    const val SCORE = "$DATASET/score"
    const val PAGE = "$DATASET/publications/{publication}/pages/{page}"
    const val FRAGMENT_PARAMETER = "fragment"
    const val STRICT_PARAMETER = "strict"

    fun dataset(name: String) = "$DATASETS/$name"
    fun score(name: String) = "${dataset(name)}/score"
    fun page(name: String, publicationId: String, page: Int) = "${dataset(name)}/publications/$publicationId/pages/$page"
}
