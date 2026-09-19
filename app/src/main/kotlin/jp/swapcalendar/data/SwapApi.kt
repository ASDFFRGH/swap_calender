package jp.swapcalendar.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import jp.swapcalendar.api.ErrorResponseDto
import jp.swapcalendar.api.MonthResponseDto

class SwapApi(private val client: HttpClient, private val baseUrl: String) {
    suspend fun month(month: String): MonthResponseDto {
        val response = client.get("$baseUrl/api/swap-points") { parameter("month", month) }
        if (!response.status.isSuccess()) {
            val error = runCatching { response.body<ErrorResponseDto>() }.getOrNull()
            throw ApiException(error?.code ?: "HTTP_${response.status.value}", error?.message ?: "通信に失敗しました")
        }
        return response.body()
    }
}

class ApiException(val code: String, override val message: String) : Exception(message)

