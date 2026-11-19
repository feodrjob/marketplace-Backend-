package catalog.domain.model

import catalog.common.enums.ProductStatus
import java.time.Instant
import java.util.UUID

class ProductModel(
    val id: UUID,
    val sellerId: UUID,
    val categoryId: UUID,
    title: String?,
    description: String?,
    val price: Long,
    val status: ProductStatus,
    images: List<ProductImageRef>?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val title: String = requireTitle(title)
    val description: String? = TextValues.trimToNull(description)
    val images: List<ProductImageRef> = sortedUniqueImages(id, images)

    fun isActive(): Boolean = status == ProductStatus.ACTIVE

    companion object {
        const val TITLE_MAX_LENGTH = 255
        const val MIN_PRICE = 0L

        private fun requireTitle(title: String?): String {
            val normalized = TextValues.trimToNull(title)
                ?: throw IllegalArgumentException("Field title is required")
            if (normalized.length > TITLE_MAX_LENGTH) {
                throw IllegalArgumentException("Field title is longer than $TITLE_MAX_LENGTH")
            }
            return normalized
        }

        private fun sortedUniqueImages(productId: UUID, images: List<ProductImageRef>?): List<ProductImageRef> {
            val sorted = (images ?: emptyList()).sortedWith(compareBy({ it.sortOrder }, { it.mediaId }))
            val seen = mutableSetOf<UUID>()
            for (image in sorted) {
                if (image.productId != productId) {
                    throw IllegalArgumentException("Product image belongs to another product")
                }
                if (!seen.add(image.mediaId)) {
                    throw IllegalArgumentException("Product image is duplicated")
                }
            }
            return sorted
        }
    }

    init {
        if (price < MIN_PRICE) {
            throw IllegalArgumentException("Price must not be negative")
        }
    }
}
