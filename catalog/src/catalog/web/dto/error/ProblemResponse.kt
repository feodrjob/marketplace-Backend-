package catalog.web.dto.error

import java.time.Instant

/**
 * Описывает ошибку публичного API.
 */
data class ProblemResponse(
    val type: String,
    val title: String,
    val status: Int,
    val detail: String,
    val instance: String,
    val timestamp: Instant,
)
