package cybergarden.vinity.domain.model

data class Submission(
    val publications: List<Publication> = emptyList(),
    val materials: List<Material> = emptyList(),
    val devices: List<Device> = emptyList(),
    val layers: List<Layer> = emptyList(),
    val measurements: List<Measurement> = emptyList(),
    val results: List<MetricResult> = emptyList(),
    val issues: List<Issue> = emptyList(),
) {
    fun forPublications(ids: Set<String>) = Submission(
        publications = publications.filter { it.publicationId in ids },
        materials = materials.filter { it.publicationId in ids },
        devices = devices.filter { it.publicationId in ids },
        layers = layers.filter { it.publicationId in ids },
        measurements = measurements.filter { it.publicationId in ids },
        results = results.filter { it.publicationId in ids },
        issues = issues.filter { it.publicationId in ids },
    )
}

data class Publication(
    val publicationId: String,
    val title: String?,
    val year: Int?,
    val doi: String?,
    val authors: List<String>,
    val inScope: Boolean?,
    val exclusionReason: String?,
)

data class Material(
    val publicationId: String,
    val materialId: String,
    val materialNameRaw: String?,
    val formulaNormalized: String?,
    val materialClass: MaterialClass?,
    val dimensionality: Dimensionality?,
    val additives: String?,
    val bandgapValue: Double?,
    val bandgapUnit: String?,
    val bandgapMethod: BandgapMethod?,
    val note: String?,
)

data class Device(
    val publicationId: String,
    val deviceId: String,
    val absorberMaterialId: String?,
    val deviceLabel: String?,
    val deviceRole: DeviceRole?,
    val deviceType: DeviceType?,
    val architecture: Architecture?,
    val substrateType: SubstrateType?,
    val activeArea: Double?,
    val areaUnit: String?,
    val areaDefinition: AreaDefinition?,
    val encapsulation: String?,
    val note: String?,
)

data class Layer(
    val publicationId: String,
    val layerId: String,
    val deviceId: String,
    val layerOrder: Int?,
    val layerFunction: LayerFunction?,
    val layerMaterial: String?,
    val layerThickness: String?,
    val thicknessUnit: String?,
    val depositionMethod: String?,
    val annealingTemperature: String?,
    val annealingTime: String?,
    val atmosphere: String?,
    val note: String?,
)

data class Measurement(
    val publicationId: String,
    val measurementId: String,
    val deviceIds: List<String>,
    val measurementType: MeasurementType?,
    val spectrum: String?,
    val lightIntensity: Double?,
    val intensityUnit: String?,
    val scanDirection: ScanDirection?,
    val scanRate: String?,
    val maskUsed: Boolean?,
    val certified: Boolean?,
    val certificationLab: String?,
    val temperature: String?,
    val stabilityProtocol: StabilityProtocol?,
    val stressConditions: String?,
    val note: String?,
)

data class Source(
    val page: Int?,
    val section: String?,
    val table: String?,
    val figure: String?,
    val fragment: String?,
)

data class MetricResult(
    val publicationId: String,
    val resultId: String,
    val deviceId: String?,
    val measurementId: String?,
    val materialId: String?,
    val metricName: String,
    val metricValue: Double?,
    val metricUnit: String?,
    val aggregation: Aggregation?,
    val spreadValue: Double?,
    val nDevices: Int?,
    val timePoint: Double?,
    val timeUnit: String?,
    val rawValue: String?,
    val rawUnit: String?,
    val normalizedValue: Double?,
    val normalizedUnit: String?,
    val origin: ResultOrigin?,
    val citedPublicationId: String?,
    val source: Source,
    val extractionStatus: ExtractionStatus?,
    val duplicateOf: String?,
    val note: String?,
) {
    val scoredMetric: ScoredMetric? get() = codeOf<ScoredMetric>(metricName)
}

data class Issue(
    val publicationId: String,
    val issueId: String,
    val recordIds: List<String>,
    val checkName: String,
    val description: String?,
    val sourcePage: Int?,
    val sourceFragment: String?,
)
