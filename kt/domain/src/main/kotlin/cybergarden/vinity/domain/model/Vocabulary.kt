package cybergarden.vinity.domain.model

interface Coded {
    val code: String
}

inline fun <reified E> codeOf(value: String): E? where E : Enum<E>, E : Coded =
    enumValues<E>().firstOrNull { it.code == value }

enum class MaterialClass(override val code: String) : Coded {
    PEROVSKITE("perovskite"),
    CIGS("CIGS"),
    CDTE("CdTe"),
    KESTERITE("kesterite"),
    SB2SE3("Sb2Se3"),
    THIN_FILM_SI("thin-film Si"),
    OTHER("other"),
}

enum class Dimensionality(override val code: String) : Coded {
    BULK("3D"),
    LAYERED("2D"),
    MIXED("2D-3D"),
}

enum class BandgapMethod(override val code: String) : Coded {
    TAUC("Tauc"),
    EQE("EQE"),
    PL("PL"),
    CITED("cited"),
}

enum class DeviceRole(override val code: String) : Coded {
    CONTROL("control"),
    TARGET("target"),
    RECORD("record"),
}

enum class DeviceType(override val code: String) : Coded {
    CELL("cell"),
    MINIMODULE("minimodule"),
    MODULE("module"),
}

enum class Architecture(override val code: String) : Coded {
    NIP("n-i-p"),
    PIN("p-i-n"),
    SUBSTRATE("substrate"),
    SUPERSTRATE("superstrate"),
}

enum class SubstrateType(override val code: String) : Coded {
    RIGID("rigid"),
    FLEXIBLE("flexible"),
}

enum class AreaDefinition(override val code: String) : Coded {
    DESIGNATED_ILLUMINATION("designated illumination"),
    APERTURE("aperture"),
    TOTAL("total"),
}

enum class LayerFunction(override val code: String) : Coded {
    FRONT_CONTACT("front_contact"),
    ETL("ETL"),
    ABSORBER("absorber"),
    PASSIVATION("passivation"),
    HTL("HTL"),
    INTERLAYER("interlayer"),
    RECOMBINATION("recombination"),
    BACK_CONTACT("back_contact"),
}

enum class MeasurementType(override val code: String) : Coded {
    JV("J-V"),
    MPP_TRACKING("MPP tracking"),
    EQE("EQE"),
    STABILITY("stability"),
}

enum class ScanDirection(override val code: String) : Coded {
    REVERSE("reverse"),
    FORWARD("forward"),
    AVERAGE("average"),
}

enum class StabilityProtocol(override val code: String) : Coded {
    ISOS_D("ISOS-D"),
    ISOS_L("ISOS-L"),
    ISOS_T("ISOS-T"),
    OTHER("other"),
}

enum class Aggregation(override val code: String) : Coded {
    CHAMPION("champion"),
    MEAN("mean"),
    MEDIAN("median"),
}

enum class ResultOrigin(override val code: String) : Coded {
    PRIMARY("primary"),
    CITED("cited"),
}

enum class ExtractionStatus(override val code: String) : Coded {
    EXTRACTED("extracted"),
    PRESENT_NOT_EXTRACTABLE("present_not_extractable"),
    ABSENT("absent"),
}

enum class ScoredMetric(override val code: String, val unit: String) : Coded {
    PCE("PCE", "%"),
    VOC("Voc", "V"),
    JSC("Jsc", "mA/cm2"),
    FF("FF", "%"),
    RS("Rs", "Ohm*cm2"),
    RSH("Rsh", "Ohm*cm2"),
    HI("HI", "%"),
    JSC_EQE("Jsc_EQE", "mA/cm2"),
    STABILIZED_PCE("stabilized_PCE", "%"),
    T80("T80", "h"),
    RETAINED_PCE("retained_PCE", "%"),
}
