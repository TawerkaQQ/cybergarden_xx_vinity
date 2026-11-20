from typing import Literal

from pydantic import BaseModel, ConfigDict

MaterialClass = Literal["perovskite", "CIGS", "CdTe", "kesterite", "Sb2Se3", "thin-film Si", "other"]
Dimensionality = Literal["3D", "2D", "2D-3D"]
BandgapMethod = Literal["Tauc", "EQE", "PL", "cited"]
DeviceRole = Literal["control", "target", "record"]
DeviceType = Literal["cell", "minimodule", "module"]
Architecture = Literal["n-i-p", "p-i-n", "substrate", "superstrate"]
SubstrateType = Literal["rigid", "flexible"]
AreaDefinition = Literal["designated illumination", "aperture", "total"]
LayerFunction = Literal[
    "front_contact", "ETL", "absorber", "passivation", "HTL", "interlayer", "recombination", "back_contact"
]
MeasurementType = Literal["J-V", "MPP tracking", "EQE", "stability"]
ScanDirection = Literal["reverse", "forward", "average"]
StabilityProtocol = Literal["ISOS-D", "ISOS-L", "ISOS-T", "other"]
Metric = Literal["PCE", "Voc", "Jsc", "FF", "Rs", "Rsh", "HI", "Jsc_EQE", "stabilized_PCE", "T80", "retained_PCE"]
Aggregation = Literal["champion", "mean", "median"]
Origin = Literal["primary", "cited"]
Status = Literal["extracted", "present_not_extractable"]


class Record(BaseModel):
    model_config = ConfigDict(extra="forbid")


class Publication(Record):
    title: str | None
    year: int | None
    doi: str | None
    authors: list[str]
    in_scope: bool
    exclusion_reason: str | None


class Bandgap(Record):
    raw: str
    method: BandgapMethod | None
    page: int | None
    quote: str | None


class Material(Record):
    key: str
    material_name_raw: str
    formula: str | None
    material_class: MaterialClass | None
    dimensionality: Dimensionality | None
    additives: str | None
    bandgap: Bandgap | None


class Area(Record):
    raw: str
    definition: AreaDefinition | None


class Layer(Record):
    function: LayerFunction
    material: str
    thickness_raw: str | None
    deposition_method: str | None
    annealing_temperature: str | None
    annealing_time: str | None
    atmosphere: str | None
    note: str | None


class Device(Record):
    key: str
    label: str
    material_key: str | None
    role: DeviceRole | None
    device_type: DeviceType | None
    architecture: Architecture | None
    substrate_type: SubstrateType | None
    area: Area | None
    encapsulation: str | None
    note: str | None
    layers: list[Layer]


class Measurement(Record):
    key: str
    device_keys: list[str]
    type: MeasurementType
    spectrum: str | None
    intensity_raw: str | None
    scan_direction: ScanDirection | None
    scan_rate: str | None
    mask_used: bool | None
    certified: bool | None
    certification_lab: str | None
    temperature: str | None
    stability_protocol: StabilityProtocol | None
    stress_conditions: str | None
    note: str | None


class Mention(Record):
    metric: Metric
    raw_value: str | None
    raw_unit: str | None
    device_key: str | None
    measurement_key: str | None
    aggregation: Aggregation | None
    spread_raw: str | None
    n_devices: int | None
    time_point_raw: str | None
    origin: Origin
    cited_ref: str | None
    status: Status
    page: int
    section: str | None
    table: str | None
    figure: str | None
    quote: str
    note: str | None


class ArticleStructure(Record):
    publication: Publication
    materials: list[Material]
    devices: list[Device]
    measurements: list[Measurement]


class ArticleMentions(Record):
    mentions: list[Mention]


class ExtractResult(Record):
    publication_id: str
    prompt_version: str
    publication: Publication
    materials: list[Material]
    devices: list[Device]
    measurements: list[Measurement]
    mentions: list[Mention]

    @classmethod
    def combine(
        cls, publication_id: str, prompt_version: str, structure: ArticleStructure, mentions: ArticleMentions
    ) -> "ExtractResult":
        return cls(
            publication_id=publication_id,
            prompt_version=prompt_version,
            publication=structure.publication,
            materials=structure.materials,
            devices=structure.devices,
            measurements=structure.measurements,
            mentions=mentions.mentions,
        )
