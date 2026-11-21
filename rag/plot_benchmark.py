import argparse
import json
from pathlib import Path

import matplotlib

matplotlib.use("Agg")

from matplotlib import pyplot as plt
from matplotlib.ticker import FuncFormatter

COLORS = ("#778397", "#007a73", "#8268b1")
INK = "#17283e"
MUTED = "#4b5f75"
GRID = "#e0e7ef"
METRICS = (("precision", "Точность"), ("recall", "Полнота"), ("f1", "F1"))
SOURCES = (("table", "Таблицы"), ("figure", "Рисунки"), ("text", "Текст"))


def main() -> None:
    parser = argparse.ArgumentParser(description="Графики строгих метрик для одного набора PDF")
    parser.add_argument("scores", nargs="+", type=Path)
    parser.add_argument("--label", action="append", default=[])
    parser.add_argument("--out", required=True, type=Path)
    args = parser.parse_args()
    if len(args.scores) > len(COLORS) or (args.label and len(args.label) != len(args.scores)):
        parser.error("Передайте до трёх отчётов и по одной подписи для каждого")
    try:
        reports = [json.loads(path.read_text(encoding="utf-8")) for path in args.scores]
        validate_corpus(reports)
    except (KeyError, OSError, ValueError) as error:
        parser.exit(1, f"{error}\n")

    labels = args.label or [path.parent.name for path in args.scores]
    args.out.mkdir(parents=True, exist_ok=True)
    with plt.rc_context({
        "font.family": "DejaVu Sans",
        "axes.edgecolor": GRID,
        "axes.labelcolor": MUTED,
        "text.color": INK,
        "xtick.color": MUTED,
        "ytick.color": MUTED,
        "figure.dpi": 152,
        "savefig.facecolor": "#ffffff",
    }):
        plot_overview(reports, labels, args.out / "comparison.png")
        plot_publications(reports, labels, args.out / "by-publication.png")


def validate_corpus(reports: list[dict]) -> None:
    def signature(report: dict) -> tuple:
        articles = {name: row["strict"]["gold"] for name, row in report["by_publication"].items()}
        sources = {name: row["strict"]["gold"] for name, row in report["by_source"].items()}
        return articles, sources

    first = signature(reports[0])
    if any(signature(report) != first for report in reports[1:]):
        raise ValueError("У отчётов разные наборы статей или объёмы эталона")


def percent(value: float) -> str:
    return f"{value:.2%}".replace(".", ",")


def integer(value: int) -> str:
    return f"{value:,}".replace(",", " ")


def style_axis(axis, grid_axis: str) -> None:
    axis.set_axisbelow(True)
    axis.grid(axis=grid_axis, color=GRID, linewidth=0.7)
    axis.tick_params(length=0, pad=8)
    axis.spines[["top", "right", "bottom", "left"]].set_visible(False)


def plot_overview(reports: list[dict], labels: list[str], path: Path) -> None:
    figure, (overall, sources) = plt.subplots(1, 2, figsize=(11.6, 4.6))
    figure.text(0.07, 0.94, "Качество извлечения чисел из PDF", fontsize=19, weight="bold")
    rows = reports[0]["micro"]["strict"]["gold"]
    articles = len(reports[0]["by_publication"])
    figure.text(0.07, 0.875, f"dev  ·  документов {articles}  ·  {integer(rows)} строк в эталоне", color=MUTED)

    metric_values = [[report["micro"]["strict"][key] for key, _ in METRICS] for report in reports]
    source_values = [[report["by_source"][key]["strict"]["recall"] for key, _ in SOURCES] for report in reports]
    source_labels = [
        f"{label}\n{integer(reports[0]['by_source'][key]['strict']['gold'])} строк"
        for key, label in SOURCES
    ]
    draw_bars(overall, "Строгие micro-метрики", [label for _, label in METRICS], metric_values, labels, 1.07)
    maximum = max(max(values) for values in source_values)
    draw_bars(sources, "Строгая полнота по источнику", source_labels, source_values, labels,
              min(1.07, max(0.12, maximum * 1.24)))
    handles, names = overall.get_legend_handles_labels()
    figure.legend(handles, names, loc="lower center", bbox_to_anchor=(0.5, 0.04), frameon=False, ncols=len(labels))
    figure.subplots_adjust(left=0.075, right=0.978, top=0.745, bottom=0.24, wspace=0.25)
    figure.savefig(path)
    plt.close(figure)


def draw_bars(axis, title: str, categories: list[str], series: list[list[float]], labels: list[str], ymax: float) -> None:
    style_axis(axis, "y")
    axis.set_title(title, loc="left", fontsize=12, weight="bold", pad=14)
    axis.set_xticks(range(len(categories)), categories)
    axis.set_ylim(0, ymax)
    axis.yaxis.set_major_formatter(FuncFormatter(lambda x, _: f"{x:.0%}"))
    width = 0.68 / len(series)
    for idx, (values, label) in enumerate(zip(series, labels, strict=True)):
        positions = [j + width * (idx - (len(series) - 1) / 2) for j in range(len(categories))]
        bars = axis.bar(positions, values, width * 0.9, color=COLORS[idx], label=label)
        axis.bar_label(bars, labels=[percent(value) for value in values], padding=4, fontsize=8, color=INK)


def plot_publications(reports: list[dict], labels: list[str], path: Path) -> None:
    ids = sorted(reports[0]["by_publication"])
    figure, (axis, counts) = plt.subplots(
        1, 2, figsize=(9.4, max(4.3, len(ids) * 0.34 + 1.8)), sharey=True,
        gridspec_kw={"width_ratios": (6, 1)},
    )
    figure.text(0.11, 0.94, "Строгий F1 по статьям", fontsize=19, weight="bold")
    rows = reports[0]["micro"]["strict"]["gold"]
    figure.text(0.11, 0.90, f"{len(ids)} статей  ·  {integer(rows)} числовых строк в эталоне", color=MUTED)
    style_axis(axis, "x")
    axis.set_yticks(range(len(ids)), ids)
    axis.invert_yaxis()
    axis.set_xlim(0, 1.04)
    axis.set_xticks([0.0, 0.2, 0.4, 0.6, 0.8, 1.0], ["0%", "20%", "40%", "60%", "80%", "100%"])
    size = 0.76 / len(reports)
    for idx, (report, label) in enumerate(zip(reports, labels, strict=True)):
        values = [report["by_publication"][name]["strict"]["f1"] for name in ids]
        positions = [j + size * (idx - (len(reports) - 1) / 2) for j in range(len(ids))]
        axis.barh(positions, values, size * 0.88, color=COLORS[idx], label=label)
        for position, value in zip(positions, values, strict=True):
            if value > 0:
                axis.text(value + 0.012, position, percent(value), va="center", color=MUTED, fontsize=7.3)

    counts.set_axis_off()
    counts.set_title("В эталоне", loc="right", color=MUTED, fontsize=10)
    counts.set_xlim(0, 1)
    for position, name in enumerate(ids):
        value = reports[0]["by_publication"][name]["strict"]["gold"]
        counts.text(0.98, position, integer(value), ha="right", va="center", color=MUTED, fontsize=9)
    handles, names = axis.get_legend_handles_labels()
    figure.legend(handles, names, loc="lower center", bbox_to_anchor=(0.5, 0.03), frameon=False, ncols=len(labels))
    figure.subplots_adjust(left=0.11, right=0.945, top=0.83, bottom=0.12, wspace=0.04)
    figure.savefig(path)
    plt.close(figure)


if __name__ == "__main__":
    main()
