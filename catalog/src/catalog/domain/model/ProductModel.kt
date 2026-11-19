package catalog.domain.model

import catalog.common.enums.ProductStatus
import java.time.Instant
import java.util.UUID

/**
 * Товар каталога во всех статусах.
 *
 * <p>
 * Фото сортируются по [ProductImageRef.sortOrder] и должны указывать на этот [id].
 * </p>
 *
 * @param id идентификатор товара
 * @param sellerId аккаунт продавца
 * @param categoryId категория товара
 * @param title название после обрезки пробелов, не длиннее [TITLE_MAX_LENGTH]
 * @param description описание, пустое значение сохраняется как отсутствие
 * @param price цена в бонусах, не меньше [MIN_PRICE]
 * @param status жизненный цикл
 * @param images связи с файлами Media, можно не передавать
 * @param createdAt момент создания
 * @param updatedAt момент последней правки
 * @since 0.0.1
 * @author lllnightlll
 * @see ProductStatus
 */
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
