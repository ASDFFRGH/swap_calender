package jp.swapcalendar.api

import kotlinx.serialization.Serializable

@Serializable
data class SourceDto(val name: String, val url: String, val lastFetchedAt: String)

@Serializable
data class SwapPointDto(
    val tradeDate: String,
    val spDays: Int,
    val buySwap: String?,
    val sellSwap: String?,
    val publicationState: PublicationStateDto,
)

@Serializable
enum class PublicationStateDto { PUBLISHED, UNPUBLISHED }

@Serializable
data class PairPointsDto(val id: String, val symbol: String, val points: List<SwapPointDto>)

@Serializable
data class MonthResponseDto(val month: String, val source: SourceDto, val pairs: List<PairPointsDto>)

@Serializable
data class CurrencyPairDto(val id: String, val symbol: String, val displayOrder: Int, val active: Boolean)

@Serializable
data class CurrencyPairsResponseDto(val pairs: List<CurrencyPairDto>)

@Serializable
data class AvailabilityResponseDto(val sourceFirstMonth: String, val availableMonths: List<String>)

@Serializable
data class ErrorResponseDto(val code: String, val message: String)

@Serializable
data class ImportResponseDto(val month: String, val recordCount: Int)
