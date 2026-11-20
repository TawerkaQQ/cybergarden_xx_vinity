package cybergarden.vinity.data.csv

enum class SubmissionTable(val fileName: String, val columns: List<String>) {
    PUBLICATIONS(
        "publications.csv",
        listOf("publication_id", "title", "year", "DOI", "authors", "in_scope", "exclusion_reason"),
    ),
    MATERIALS(
        "materials.csv",
        listOf(
            "publication_id", "material_id", "material_name_raw", "formula_normalized", "material_class",
            "dimensionality", "additives", "bandgap_value", "bandgap_unit", "bandgap_method", "note",
        ),
    ),
    DEVICES(
        "devices.csv",
        listOf(
            "publication_id", "device_id", "absorber_material_id", "device_label", "device_role", "device_type",
            "architecture", "substrate_type", "active_area", "area_unit", "area_definition", "encapsulation", "note",
        ),
    ),
    LAYERS(
        "layers.csv",
        listOf(
            "publication_id", "layer_id", "device_id", "layer_order", "layer_function", "layer_material",
            "layer_thickness", "thickness_unit", "deposition_method", "annealing_temperature", "annealing_time",
            "atmosphere", "note",
        ),
    ),
    MEASUREMENTS(
        "measurements.csv",
        listOf(
            "publication_id", "measurement_id", "device_id", "measurement_type", "spectrum", "light_intensity",
            "intensity_unit", "scan_direction", "scan_rate", "mask_used", "certified", "certification_lab",
            "temperature", "stability_protocol", "stress_conditions", "note",
        ),
    ),
    RESULTS(
        "results.csv",
        listOf(
            "publication_id", "result_id", "device_id", "measurement_id", "material_id", "metric_name",
            "metric_value", "metric_unit", "aggregation", "spread_value", "n_devices", "time_point", "time_unit",
            "raw_value", "raw_unit", "normalized_value", "normalized_unit", "result_origin", "cited_publication_id",
            "source_page", "source_section", "source_table", "source_figure", "source_fragment",
            "extraction_status", "duplicate_of", "note",
        ),
    ),
    ISSUES(
        "issues.csv",
        listOf("publication_id", "issue_id", "record_ids", "check_name", "description", "source_page", "source_fragment"),
    );

    val tableName: String get() = fileName.removeSuffix(".csv")
}
