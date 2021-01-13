import com.soywiz.klock.*
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * @author Andrew Panasiuk
 */

data class Transaction(
    val date: DateTimeTz,
    val amount: Double,
    val category: String
)

val transactions = mutableMapOf<String, MutableList<Transaction>>()

val dateFormat = DateFormat.invoke("dd.MM.yyyy")

fun main(args: Array<String>) {

    val file = File("src/main/resources/transactions.txt")

    val lines = file.readLines()

    var correctPredictions = 0
    var precisePredictions = 0
    lines.forEachIndexed { index, line ->
        line.split('\t').also {
            try {
                val category = it[2]
                val transaction = Transaction(dateFormat.parse(it.first()), it[1].toDouble(), category)

                if (index > 210) {
                    val predictions = predict(transaction.date, transaction.amount)

                    val list = predictions.entries.filter { it.value.count >= 4 }.sortedByDescending {
                        it.value.coeff
                    }
                    val subList = list.subList(0, 3)

                    if (subList.find { it.key == transaction.category } != null) {
                        correctPredictions++
                        if (subList.first().key == transaction.category) {
                            precisePredictions++
                        }
                    } else {
                        println(transaction)
                        subList.forEach {
                            println("${it.key}: ${it.value} ${it.value.coeff}")
                        }
                        println()
                    }
                }

                transactions.getOrPut(category) { mutableListOf() }
                    .add(transaction)

            } catch (t: Throwable) {
                println("Error while parsing $line\n$t")
            }
        }
    }
    println("Correct predictions: $correctPredictions")
    println("Precise predictions: $correctPredictions")

    println(transactions.keys)
}

fun predict(date: DateTimeTz, amount: Double): Map<String, Prediction> {
    val categories = transactions.keys

    return categories.map {
        val prediction = predictCategory(it, date, amount)
        it to prediction
    }.toMap()
}

data class Prediction(
    val count: Int,
    //нехватка расходов (если чисто отрицательное) или перерасходы по категории за последние n транзакций (чем меньше число тем большая нехватка); от -1 до +бесконечности
    val averageExpensesDiff: Double,
    val expenseDiff1: Double?, //ближайшая схожесть с другими тратами в категории; от 0 до 1
    val expenseDiff2: Double? //2-я ближайшая схожесть с другими тратами в категории; от 0 до 1
) {
    val coeff
        get() = sqrt(
            4 * (0.4 / (averageExpensesDiff + 1)).pow(2) +
                    2 * (0.4 / ((expenseDiff1 ?: 3.0) + 0.1)).pow(2) +
                    (0.4 / ((expenseDiff2 ?: 3.0) + 0.1)).pow(2)
        )
}

fun predictCategory(category: String, date: DateTimeTz, amount: Double): Prediction {
    val transactions = transactions[category] ?: emptyList()
    val N = max(1, transactions.size / 2)

    val (lastNTransactions, averageExpensesAtAll) = getLastTransactions(N, transactions, date, false)

    val n = max(1, N / 2)
    val (_, averageExpensesForNow) = getLastTransactions(n, transactions, date, true)

    val expensesDiff = (averageExpensesForNow - averageExpensesAtAll) / averageExpensesAtAll

    val diffs = lastNTransactions.map { abs((it.amount - amount) / it.amount) }.sorted()

    return Prediction(N, expensesDiff, diffs[0], diffs.getOrNull(1))
}

private fun getLastTransactions(
    n: Int,
    fromTransactions: List<Transaction>,
    today: DateTimeTz, include: Boolean
): Pair<MutableList<Transaction>, Double> {
    val lastTransactions = mutableListOf<Transaction>()
    var i = 0
    while (lastTransactions.size < n && i < fromTransactions.size) {
        val transaction = fromTransactions[fromTransactions.size - 1 - i]
        if (transaction.date != today || include) {
            lastTransactions.add(0, transaction)
        }
        i++
    }
    while (i < fromTransactions.size && fromTransactions[fromTransactions.size - 1 - i].date == lastTransactions.first().date) {
        lastTransactions.add(0, fromTransactions[fromTransactions.size - 1 - i])
        i++
    }
    var firstDay = lastTransactions.first().date
    if (i < fromTransactions.size) {
        firstDay = fromTransactions[fromTransactions.size - 1 - i].date.plus(1.days)
    }

    val lastDay = if (include) today else lastTransactions.last().date

    val sum = lastTransactions.sumByDouble { it.amount }

    val days = DateTimeRange(
        firstDay.local.dateDayStart,
        lastDay.local.dateDayEnd.plus(2.minutes)
    ).duration.days.toInt()

    //средние расходы за последние N транзакций
    val averageExpenses = sum / days
    return Pair(lastTransactions, averageExpenses)
}
