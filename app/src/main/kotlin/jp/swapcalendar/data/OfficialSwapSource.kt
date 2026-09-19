package jp.swapcalendar.data

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import jp.swapcalendar.api.MonthResponseDto
import jp.swapcalendar.api.PairPointsDto
import jp.swapcalendar.api.PublicationStateDto
import jp.swapcalendar.api.SourceDto
import jp.swapcalendar.api.SwapPointDto
import org.jsoup.Jsoup
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

private const val OFFICIAL_URL = "https://www.gaikaex.com/gaikaex/mark/swap/calendar.php"

class OfficialSwapSource(private val client: HttpClient) {
    suspend fun month(value: String): MonthResponseDto {
        val month = runCatching { YearMonth.parse(value) }
            .getOrElse { throw ApiException("INVALID_MONTH", "年月の形式が正しくありません") }
        if (month < YearMonth.of(2014, 12)) {
            throw ApiException("MONTH_OUT_OF_RANGE", "2014年12月より前のデータは取得できません")
        }
        val response = client.get(OFFICIAL_URL) {
            parameter("date", "%04d%02d".format(month.year, month.monthValue))
            header(HttpHeaders.UserAgent, "SwapCalendar-Android/0.2")
        }
        if (!response.status.isSuccess()) {
            throw ApiException("SOURCE_HTTP_${response.status.value}", "公式ページの取得に失敗しました")
        }
        return parse(response.bodyAsText(), month, Instant.now().toString())
    }

    fun parse(html: String, requestedMonth: YearMonth, fetchedAt: String): MonthResponseDto {
        try {
            val document = Jsoup.parse(html)
            val heading = document.selectFirst(".month_nav .back")?.text()
                ?: throw ParseFailure("ページの年月を確認できません")
            val headingMatch = Regex("(\\d{4})年(\\d{1,2})月").find(heading)
                ?: throw ParseFailure("ページの年月が不正です")
            val actualMonth = YearMonth.of(
                headingMatch.groupValues[1].toInt(),
                headingMatch.groupValues[2].toInt(),
            )
            if (actualMonth != requestedMonth) {
                throw ApiException("MONTH_NOT_AVAILABLE", "この月は公式ページに掲載されていません")
            }

            val pairOptions = document.select("#currencyPair_select option").map { option ->
                val id = option.attr("value").toIntOrNull()
                    ?: throw ParseFailure("通貨ペアIDが不正です")
                val symbol = Regex("[A-Z]{3}/[A-Z]{3}").find(option.text())?.value
                    ?: throw ParseFailure("通貨ペア名が不正です")
                id to symbol
            }
            if (pairOptions.isEmpty()) throw ParseFailure("通貨ペア一覧がありません")
            val symbolsById = pairOptions.toMap()
            val pointsById = linkedMapOf<Int, MutableList<SwapPointDto>>()
            val pairClassPattern = Regex("th_commodity_(\\d+)")
            val dayPattern = Regex("(\\d{1,2})月(\\d{1,2})日")

            document.select(".tbl_list.pcOnly table[id^=swaplist]").forEach { table ->
                val ids = table.select("thead tr:first-child th[colspan=3]").map { header ->
                    val className = header.classNames().firstOrNull { it.startsWith("th_commodity_") }
                        ?: throw ParseFailure("通貨ペア列を確認できません")
                    pairClassPattern.matchEntire(className)?.groupValues?.get(1)?.toInt()
                        ?: throw ParseFailure("通貨ペア列が不正です")
                }
                table.select("tbody tr").forEach rowLoop@{ row ->
                    val cells = row.select("td")
                    if (cells.isEmpty()) return@rowLoop
                    if (cells.size != 1 + ids.size * 3) throw ParseFailure("データの列数が一致しません")
                    val match = dayPattern.find(cells[0].text())
                        ?: throw ParseFailure("取引日が不正です")
                    val date = LocalDate.of(
                        requestedMonth.year,
                        match.groupValues[1].toInt(),
                        match.groupValues[2].toInt(),
                    )
                    if (YearMonth.from(date) != requestedMonth) throw ParseFailure("対象月外の日付です")
                    ids.forEachIndexed { index, pairId ->
                        if (pairId !in symbolsById) throw ParseFailure("通貨ペア名が見つかりません")
                        val offset = 1 + index * 3
                        val spDays = cells[offset].text().trim().toIntOrNull()
                            ?: throw ParseFailure("SP日数が不正です")
                        val buy = cells[offset + 1].text().trim().nullIfBlank()
                        val sell = cells[offset + 2].text().trim().nullIfBlank()
                        buy?.toBigDecimalOrNull() ?: if (buy != null) throw ParseFailure("買スワップが不正です") else null
                        sell?.toBigDecimalOrNull() ?: if (sell != null) throw ParseFailure("売スワップが不正です") else null
                        pointsById.getOrPut(pairId) { mutableListOf() } += SwapPointDto(
                            tradeDate = date.toString(),
                            spDays = spDays,
                            buySwap = buy,
                            sellSwap = sell,
                            publicationState = if (buy == null && sell == null) {
                                PublicationStateDto.UNPUBLISHED
                            } else {
                                PublicationStateDto.PUBLISHED
                            },
                        )
                    }
                }
            }
            if (pointsById.isEmpty()) throw ParseFailure("スワップデータがありません")
            val pairs = pairOptions.mapNotNull { (id, symbol) ->
                pointsById[id]?.let { points -> PairPointsDto(id.toString(), symbol, points) }
            }
            val allPoints = pairs.flatMap { pair -> pair.points.map { pair.id to it.tradeDate } }
            if (allPoints.distinct().size != allPoints.size) throw ParseFailure("重複データがあります")
            return MonthResponseDto(
                month = requestedMonth.toString(),
                source = SourceDto("GMO外貨", OFFICIAL_URL, fetchedAt),
                pairs = pairs,
            )
        } catch (exception: ApiException) {
            throw exception
        } catch (exception: Exception) {
            throw ApiException("SOURCE_FORMAT_CHANGED", exception.message ?: "公式ページの解析に失敗しました")
        }
    }

    private fun String.nullIfBlank(): String? = takeIf { it.isNotBlank() }
    private class ParseFailure(message: String) : Exception(message)
}
