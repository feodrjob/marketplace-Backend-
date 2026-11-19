package catalog.domain.model

import java.time.Instant
import java.util.UUID

/**
 * Карточка товара для гостя.
 *
 * <p>
 * Собирает название, цену, категорию, фото и магазин.
 * Если магазин не найден, [companyName] и [sellerDescription] остаются пустыми,
 * а сама карточка всё равно открывается.
 * </p>
 *
 * @param id идентификатор товара
 * @param title название
 * @param description описание или `null`
 * @param price цена
 * @param categoryId идентификатор публичной категории
 * @param categoryName название категории
 * @param categorySlug URL-имя категории
 * @param images фото в порядке показа
 * @param sellerId аккаунт продавца
 * @param companyName название магазина или `null`
 * @param sellerDescription описание магазина или `null`
 * @param createdAt момент создания товара
 * @param updatedAt момент последней правки товара
 * @since 0.0.1
 * @author lllnightlll
 * @see ProductModel
 */
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
