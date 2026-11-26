package catalog.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

/**
 * Изображение товара в ответе API.
 */
@Schema(description = "Ссылка на изображение товара", requiredProperties = ["mediaId", "sortOrder"])
data class ProductImageResponse(
    @field:Schema(description = "Идентификатор файла в Media, URL получается через API Media", example = "00000000-0000-0000-0000-000000000201")
    val mediaId: UUID,
    @field:Schema(description = "Порядок изображения, меньшее число означает более ранний показ", example = "0")
    val sortOrder: Int,
)