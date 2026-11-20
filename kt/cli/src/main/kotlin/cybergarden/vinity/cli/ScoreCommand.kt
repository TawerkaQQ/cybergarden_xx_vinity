package cybergarden.vinity.cli

import cybergarden.vinity.data.csv.CsvSubmissionRepository
import cybergarden.vinity.domain.model.Severity
import cybergarden.vinity.domain.usecase.Counts
import cybergarden.vinity.domain.usecase.MatchMode
import cybergarden.vinity.domain.usecase.ScoreReport
import cybergarden.vinity.domain.usecase.ScoreSubmission
import java.util.Locale

class ScoreCommand(private val command: Command.Score) {

    private val repository = CsvSubmissionRepository()
    private val score = ScoreSubmission()

    fun run(): Int {
        val candidate = repository.load(command.candidate)
        val gold = repository.load(command.gold)
        val loadErrors = candidate.violations.count { it.severity == Severity.ERROR }
        if (loadErrors > 0) println("В сдаче $loadErrors ошибок формата, подробности — команда validate\n")

        val mode = if (command.strict) MatchMode.STRICT else MatchMode.LOOSE
        print(score(candidate.submission, gold.submission, mode))
        return 0
    }

    private fun print(report: ScoreReport) {
        println("Режим: ${if (report.mode == MatchMode.STRICT) "строгий (значение + агрегация + страница)" else "мягкий (показатель + значение + происхождение)"}")
        println("Статей оценено: ${report.publicationsScored} из ${report.publicationsInGold} в эталоне")
        println("in_scope совпал: ${report.scope.agreed} из ${report.scope.total}")
        println()
        println(header("Срез"))
        println(line("все упоминания", report.allMentions))
        println(line("основные записи", report.primaryRecords))
        println()
        println(header("Показатель"))
        report.byMetric.forEach { (metric, counts) -> println(line(metric, counts)) }
        if (command.byPublication) {
            println()
            println(header("Статья"))
            report.byPublication.forEach { (publication, counts) -> println(line(publication, counts)) }
        }
    }

    private fun header(title: String) =
        String.format(Locale.ROOT, "%-18s %6s %6s %6s %5s %5s %5s", title, "P", "R", "F1", "TP", "FP", "FN")

    private fun line(title: String, counts: Counts) = String.format(
        Locale.ROOT,
        "%-18s %5.1f%% %5.1f%% %5.1f%% %5d %5d %5d",
        title,
        counts.precision * 100,
        counts.recall * 100,
        counts.f1 * 100,
        counts.truePositive,
        counts.falsePositive,
        counts.falseNegative,
    )
}
