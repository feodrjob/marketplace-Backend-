package catalog.web.dto.error

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

/*
* Описывает ошибку каталога в формате Problem Details
* */
@Schema(description = "Ошибка в формате Problem Details", requiredProperties = ["type", "title", "status", "detail", "instance", "timestamp"])
data class ProblemResponse(
    @field:Schema(description = "Идентификатор типа ошибки", example = "about:blank")
    val type: String,
    @field:Schema(description = "Краткое название HTTP ошибки", example = "Bad Request")
    val title: String,
    @field:Schema(description = "HTTP код ошибки", example = "400")
    val status: Int,
    @field:Schema(description = "Описание причины ошибки", example = "Price minimum is greater than maximum")
    val detail: String,
    @field:Schema(description = "Путь запроса без query string", example = "/api/v1/catalog/products")
    val instance: String,
    @field:Schema(description = "Время формирования ошибки в формате ISO 8601", example = "2026-11-27T00:00:00Z")
    val timestamp: Instant,
)
