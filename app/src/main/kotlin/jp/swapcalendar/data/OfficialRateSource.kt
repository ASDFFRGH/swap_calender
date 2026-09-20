package jp.swapcalendar.data

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import java.math.BigDecimal
import javax.inject.Inject

private const val RATE_PAGE_URL = "https://www.gaikaex.com/gaikaex/mark/rate/"
private const val RATE_JSON_URL = "https://www.gaikaex.com/gaikaex/mark/quote.json"

data class FxRate(
    val symbol: String,
    val bid: BigDecimal,
    val ask: BigDecimal,
) {
    val midpoint: BigDecimal get() = bid.add(ask).divide(BigDecimal("2"))
}

data class FxRateSnapshot(val fetchedAt: String, val rates: List<FxRate>)

class OfficialRateSource @Inject constructor(private val client: HttpClient) {
    suspend fun latest(): FxRateSnapshot {
        val pageResponse = client.get(RATE_PAGE_URL) {
            header(HttpHeaders.UserAgent, "SwapCalendar-Android")
        }
        val rateResponse = client.get(RATE_JSON_URL) {
            header(HttpHeaders.UserAgent, "SwapCalendar-Android")
        }
        if (!pageResponse.status.isSuccess() || !rateResponse.status.isSuccess()) {
            error("GMO外貨の為替レートを取得できませんでした")
        }
        return parseOfficialRates(pageResponse.bodyAsText(), rateResponse.bodyAsText())
    }
}

internal fun parseOfficialRates(pageHtml: String, rateJson: String): FxRateSnapshot {
    val symbolsByCode = Jsoup.parse(pageHtml).select(".commoditie-parent[data-code]").mapNotNull { row ->
        val symbol = row.selectFirst(".fxPair")?.text()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        row.attr("data-code") to symbol
    }.toMap()
    val root = Json.parseToJsonElement(rateJson).jsonObject
    val fetchedAt = root["time"]?.jsonPrimitive?.content ?: error("レート取得日時がありません")
    val rateObject = root["rate"]?.jsonObject ?: error("為替レートがありません")
    val rates = rateObject.mapNotNull { (code, element) ->
        val symbol = symbolsByCode[code] ?: return@mapNotNull null
        val values = element.jsonArray
        FxRate(
            symbol = symbol,
            bid = values[5].jsonPrimitive.content.toBigDecimal(),
            ask = values[6].jsonPrimitive.content.toBigDecimal(),
        )
    }
    require(rates.isNotEmpty()) { "為替レートがありません" }
    return FxRateSnapshot(fetchedAt, rates)
}
