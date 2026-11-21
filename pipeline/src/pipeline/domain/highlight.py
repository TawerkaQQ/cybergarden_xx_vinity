from dataclasses import dataclass

Box = tuple[float, float, float, float]


@dataclass(frozen=True)
class Highlight:
    page: int
    fragment: str
    boxes: tuple[Box, ...]
    value_boxes: tuple[Box, ...]
    exact: bool

    def to_json(self) -> dict:
        return {
            "page": self.page,
            "fragment": self.fragment,
            "boxes": [list(box) for box in self.boxes],
            "value_boxes": [list(box) for box in self.value_boxes],
            "exact": self.exact,
        }
