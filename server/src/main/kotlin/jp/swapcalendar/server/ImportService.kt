package jp.swapcalendar.server

import java.time.YearMonth
import java.util.concurrent.ConcurrentHashMap

class ImportService(
    private val database: Database,
    private val client: SourceClient = SourceClient(),
    private val parser: SwapParser = SwapParser(),
) {
    private val activeMonths = ConcurrentHashMap.newKeySet<YearMonth>()

    fun import(month: YearMonth): Int {
        check(activeMonths.add(month)) { "IMPORT_ALREADY_RUNNING" }
        try {
            val response = client.fetch(month)
            check(response.status == 200) { "SOURCE_HTTP_${response.status}" }
            val parsed = parser.parse(response.body, month)
            database.import(parsed, nowUtc())
            return parsed.points.size
        } finally {
            activeMonths.remove(month)
        }
    }
}

