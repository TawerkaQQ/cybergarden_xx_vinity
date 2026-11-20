package cybergarden.vinity.data.csv

import cybergarden.vinity.domain.model.Coded
import cybergarden.vinity.domain.model.Device
import cybergarden.vinity.domain.model.Issue
import cybergarden.vinity.domain.model.Layer
import cybergarden.vinity.domain.model.Material
import cybergarden.vinity.domain.model.Measurement
import cybergarden.vinity.domain.model.MetricResult
import cybergarden.vinity.domain.model.Publication
import cybergarden.vinity.domain.model.Source
import java.math.BigDecimal

internal fun RowReader.toPublication() = Publication(
    publicationId = required("publication_id"),
    title = text("title"),
    year = int("year"),
    doi = text("DOI"),
    authors = list("authors"),
    inScope = bool("in_scope"),
    exclusionReason = text("exclusion_reason"),
)

internal fun RowReader.toMaterial() = Material(
    publicationId = required("publication_id"),
    materialId = required("material_id"),
    materialNameRaw = text("material_name_raw"),
    formulaNormalized = text("formula_normalized"),
    materialClass = code("material_class"),
    dimensionality = code("dimensionality"),
    additives = text("additives"),
    bandgapValue = double("bandgap_value"),
    bandgapUnit = text("bandgap_unit"),
    bandgapMethod = code("bandgap_method"),
    note = text("note"),
)

internal fun RowReader.toDevice() = Device(
    publicationId = required("publication_id"),
    deviceId = required("device_id"),
    absorberMaterialId = text("absorber_material_id"),
    deviceLabel = text("device_label"),
    deviceRole = code("device_role"),
    deviceType = code("device_type"),
    architecture = code("architecture"),
    substrateType = code("substrate_type"),
    activeArea = double("active_area"),
    areaUnit = text("area_unit"),
    areaDefinition = code("area_definition"),
    encapsulation = text("encapsulation"),
    note = text("note"),
)

internal fun RowReader.toLayer() = Layer(
    publicationId = required("publication_id"),
    layerId = required("layer_id"),
    deviceId = required("device_id"),
    layerOrder = int("layer_order"),
    layerFunction = code("layer_function"),
    layerMaterial = text("layer_material"),
    layerThickness = text("layer_thickness"),
    thicknessUnit = text("thickness_unit"),
    depositionMethod = text("deposition_method"),
    annealingTemperature = text("annealing_temperature"),
    annealingTime = text("annealing_time"),
    atmosphere = text("atmosphere"),
    note = text("note"),
)

internal fun RowReader.toMeasurement() = Measurement(
    publicationId = required("publication_id"),
    measurementId = required("measurement_id"),
    deviceIds = list("device_id"),
    measurementType = code("measurement_type"),
    spectrum = text("spectrum"),
    lightIntensity = double("light_intensity"),
    intensityUnit = text("intensity_unit"),
    scanDirection = code("scan_direction"),
    scanRate = text("scan_rate"),
    maskUsed = bool("mask_used"),
    certified = bool("certified"),
    certificationLab = text("certification_lab"),
    temperature = text("temperature"),
    stabilityProtocol = code("stability_protocol"),
    stressConditions = text("stress_conditions"),
    note = text("note"),
)

internal fun RowReader.toMetricResult() = MetricResult(
    publicationId = required("publication_id"),
    resultId = required("result_id"),
    deviceId = text("device_id"),
    measurementId = text("measurement_id"),
    materialId = text("material_id"),
    metricName = required("metric_name"),
    metricValue = double("metric_value"),
    metricUnit = text("metric_unit"),
    aggregation = code("aggregation"),
    spreadValue = double("spread_value"),
    nDevices = int("n_devices"),
    timePoint = double("time_point"),
    timeUnit = text("time_unit"),
    rawValue = text("raw_value"),
    rawUnit = text("raw_unit"),
    normalizedValue = double("normalized_value"),
    normalizedUnit = text("normalized_unit"),
    origin = code("result_origin"),
    citedPublicationId = text("cited_publication_id"),
    source = Source(
        page = int("source_page"),
        section = text("source_section"),
        table = text("source_table"),
        figure = text("source_figure"),
        fragment = text("source_fragment"),
    ),
    extractionStatus = code("extraction_status"),
    duplicateOf = text("duplicate_of"),
    note = text("note"),
)

internal fun RowReader.toIssue() = Issue(
    publicationId = required("publication_id"),
    issueId = required("issue_id"),
    recordIds = list("record_ids"),
    checkName = required("check_name"),
    description = text("description"),
    sourcePage = int("source_page"),
    sourceFragment = text("source_fragment"),
)

internal fun Publication.toRow() = cells(
    publicationId, title, year, doi, authors.joinToString("; ").ifEmpty { null }, inScope, exclusionReason,
)

internal fun Material.toRow() = cells(
    publicationId, materialId, materialNameRaw, formulaNormalized, materialClass, dimensionality, additives,
    bandgapValue, bandgapUnit, bandgapMethod, note,
)

internal fun Device.toRow() = cells(
    publicationId, deviceId, absorberMaterialId, deviceLabel, deviceRole, deviceType, architecture,
    substrateType, activeArea, areaUnit, areaDefinition, encapsulation, note,
)

internal fun Layer.toRow() = cells(
    publicationId, layerId, deviceId, layerOrder, layerFunction, layerMaterial, layerThickness, thicknessUnit,
    depositionMethod, annealingTemperature, annealingTime, atmosphere, note,
)

internal fun Measurement.toRow() = cells(
    publicationId, measurementId, deviceIds.joinToString(";").ifEmpty { null }, measurementType, spectrum,
    lightIntensity, intensityUnit, scanDirection, scanRate, maskUsed, certified, certificationLab, temperature,
    stabilityProtocol, stressConditions, note,
)

internal fun MetricResult.toRow() = cells(
    publicationId, resultId, deviceId, measurementId, materialId, metricName, metricValue, metricUnit,
    aggregation, spreadValue, nDevices, timePoint, timeUnit, rawValue, rawUnit, normalizedValue, normalizedUnit,
    origin, citedPublicationId, source.page, source.section, source.table, source.figure, source.fragment,
    extractionStatus, duplicateOf, note,
)

internal fun Issue.toRow() = cells(
    publicationId, issueId, recordIds.joinToString(";").ifEmpty { null }, checkName, description,
    sourcePage, sourceFragment,
)

private fun cells(vararg values: Any?): List<String> = values.map { it.toCell() }

private fun Any?.toCell(): String = when (this) {
    null -> ""
    is Coded -> code
    is Double -> BigDecimal.valueOf(this).stripTrailingZeros().toPlainString()
    else -> toString()
}
