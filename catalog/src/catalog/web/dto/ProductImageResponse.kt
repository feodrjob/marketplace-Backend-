package catalog.web.dto

import java.util.UUID

/**
 * Изображение товара в ответе API.
 */
data class ProductImageResponse(
    val mediaId: UUID,
    val sortOrder: Int,
)