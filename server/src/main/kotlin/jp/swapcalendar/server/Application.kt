package jp.swapcalendar.server

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.statuspages.exception
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import jp.swapcalendar.api.ErrorResponseDto
import jp.swapcalendar.api.ImportResponseDto
import kotlinx.serialization.json.Json
import java.time.YearMonth
import java.time.format.DateTimeParseException

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module(databaseOverride: Database? = null) {
    val appLog = environment.log
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = false; explicitNulls = true }) }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            appLog.error("Request failed", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponseDto("INTERNAL_ERROR", "処理に失敗しました"))
        }
    }
    val database = databaseOverride ?: Database.connect(
        System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5433/swap_calendar",
        System.getenv("DATABASE_USER") ?: "swap_calendar",
        System.getenv("DATABASE_PASSWORD") ?: "swap_calendar",
    )
    val importer = ImportService(database)
    monitor.subscribe(io.ktor.server.application.ApplicationStopped) { database.close() }

    routing {
        route("/api") {
            get("/health") { call.respond(mapOf("status" to "ok")) }
            get("/currency-pairs") { call.respond(database.pairs()) }
            get("/availability") { call.respond(database.availability()) }
            get("/swap-points") {
                val month = call.request.queryParameters["month"].toYearMonthOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponseDto("INVALID_MONTH", "monthはYYYY-MM形式で指定してください"))
                if (month < YearMonth.of(2014, 12)) {
                    return@get call.respond(HttpStatusCode.NotFound, ErrorResponseDto("MONTH_OUT_OF_RANGE", "取得可能範囲外です"))
                }
                val response = database.month(month)
                    ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponseDto("MONTH_NOT_AVAILABLE", "この月はまだ取得されていません"))
                call.respond(response)
            }
            post("/internal/fetch") {
                val expected = System.getenv("FETCH_TOKEN")
                if (expected.isNullOrBlank() || call.request.header("Authorization") != "Bearer $expected") {
                    return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponseDto("UNAUTHORIZED", "認証に失敗しました"))
                }
                val month = call.request.queryParameters["month"].toYearMonthOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponseDto("INVALID_MONTH", "monthはYYYY-MM形式で指定してください"))
                call.respond(ImportResponseDto(month.toString(), importer.import(month)))
            }
        }
    }
}

private fun String?.toYearMonthOrNull(): YearMonth? = try {
    this?.let(YearMonth::parse)
} catch (_: DateTimeParseException) {
    null
}
