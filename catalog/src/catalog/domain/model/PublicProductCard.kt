package catalog.domain.model

import java.time.Instant
import java.util.UUID

class PublicProductCard(
    val id: UUID,
    val title: String,
    val description: String?,
    val price: Long,
    val categoryId: UUID,
    val categoryName: String,
    val categorySlug: String,
    images: List<ProductImageRef>,
    val sellerId: UUID,
    val companyName: String?,
    val sellerDescription: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val images: List<ProductImageRef> = images.toList()
}
