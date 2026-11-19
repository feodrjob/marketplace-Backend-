package catalog.web.dto

import java.time.Instant
import java.util.UUID

/**
 * Товар в публичном ответе API.
 */
data class ProductResponse(
    val id: UUID,
    val title: String,
    val description: String?,
    val price: Long,
    val categoryId: UUID,
    val categoryName: String,
    val categorySlug: String,
    val images: List<ProductImageResponse>,
    val sellerId: UUID,
    val companyName: String?,
    val sellerDescription: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)