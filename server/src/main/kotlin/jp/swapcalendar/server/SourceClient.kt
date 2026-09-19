package jp.swapcalendar.server

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.YearMonth

data class SourceResponse(val status: Int, val body: String)

class SourceClient(
    private val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
) {
    fun fetch(month: YearMonth): SourceResponse {
        val value = "%04d%02d".format(month.year, month.monthValue)
        val request = HttpRequest.newBuilder()
            .uri(URI("https://www.gaikaex.com/gaikaex/mark/swap/calendar.php?date=$value"))
            .timeout(Duration.ofSeconds(20))
            .header("User-Agent", System.getenv("SOURCE_USER_AGENT") ?: "swap-calendar/0.1")
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        return SourceResponse(response.statusCode(), response.body())
    }
}

