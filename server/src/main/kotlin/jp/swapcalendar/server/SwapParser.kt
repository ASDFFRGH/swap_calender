package jp.swapcalendar.server

import jp.swapcalendar.api.PublicationStateDto
import org.jsoup.Jsoup
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

data class ParsedPoint(
    val sourcePairId: Int,
    val symbol: String,
    val displayOrder: Int,
    val tradeDate: LocalDate,
    val spDays: Int,
    val buySwap: BigDecimal?,
    val sellSwap: BigDecimal?,
    val state: PublicationStateDto,
)

data class ParsedMonth(val month: YearMonth, val points: List<ParsedPoint>)

class SwapParser {
    private val headingPattern = Regex("(\\d{4})年(\\d{1,2})月")
    private val dayPattern = Regex("(\\d{1,2})月(\\d{1,2})日")
    private val pairClassPattern = Regex("th_commodity_(\\d+)")

    fun parse(html: String, requestedMonth: YearMonth): ParsedMonth {
        val document = Jsoup.parse(html)
        val heading = document.selectFirst(".month_nav .back")?.text()
            ?: error("MONTH_HEADING_MISSING")
        val headingMatch = headingPattern.find(heading) ?: error("MONTH_HEADING_INVALID")
        val actualMonth = YearMonth.of(headingMatch.groupValues[1].toInt(), headingMatch.groupValues[2].toInt())
        require(actualMonth == requestedMonth) { "MONTH_MISMATCH" }

        val symbolsById = document.select("#currencyPair_select option")
            .associate { option ->
                option.attr("value").toInt() to Regex("[A-Z]{3}/[A-Z]{3}").find(option.text())!!.value
            }
        require(symbolsById.isNotEmpty()) { "PAIR_LIST_MISSING" }

        val result = mutableListOf<ParsedPoint>()
        document.select(".tbl_list.pcOnly table[id^=swaplist]").forEach { table ->
            val ids = table.select("thead tr:first-child th[colspan=3]").map { header ->
                val className = header.classNames().firstOrNull { it.startsWith("th_commodity_") }
                    ?: error("PAIR_CLASS_MISSING")
                pairClassPattern.matchEntire(className)?.groupValues?.get(1)?.toInt()
                    ?: error("PAIR_ID_INVALID")
            }
            table.select("tbody tr").forEach { row ->
                val cells = row.select("td")
                if (cells.isEmpty()) return@forEach
                val dateMatch = dayPattern.find(cells[0].text()) ?: error("TRADE_DATE_INVALID")
                val date = LocalDate.of(
                    requestedMonth.year,
                    dateMatch.groupValues[1].toInt(),
                    dateMatch.groupValues[2].toInt(),
                )
                require(YearMonth.from(date) == requestedMonth) { "TRADE_DATE_OUT_OF_MONTH" }
                require(cells.size == 1 + ids.size * 3) { "COLUMN_COUNT_MISMATCH" }
                ids.forEachIndexed { index, sourcePairId ->
                    val offset = 1 + index * 3
                    val spDays = cells[offset].text().trim().toIntOrNull() ?: error("SP_DAYS_INVALID")
                    val buy = cells[offset + 1].text().trim().toDecimalOrNull()
                    val sell = cells[offset + 2].text().trim().toDecimalOrNull()
                    result += ParsedPoint(
                        sourcePairId = sourcePairId,
                        symbol = symbolsById[sourcePairId] ?: error("PAIR_SYMBOL_MISSING"),
                        displayOrder = symbolsById.keys.indexOf(sourcePairId),
                        tradeDate = date,
                        spDays = spDays,
                        buySwap = buy,
                        sellSwap = sell,
                        state = if (buy == null && sell == null) PublicationStateDto.UNPUBLISHED else PublicationStateDto.PUBLISHED,
                    )
                }
            }
        }
        require(result.isNotEmpty()) { "SWAP_TABLE_MISSING" }
        require(result.distinctBy { it.sourcePairId to it.tradeDate }.size == result.size) { "DUPLICATE_POINT" }
        return ParsedMonth(requestedMonth, result)
    }

    private fun String.toDecimalOrNull(): BigDecimal? = if (isBlank()) null else toBigDecimal()
}

