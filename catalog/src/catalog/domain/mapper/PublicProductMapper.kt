package catalog.domain.mapper

import catalog.domain.model.CategoryModel
import catalog.domain.model.ProductModel
import catalog.domain.model.PublicProductCard
import catalog.domain.model.SellerShop

/**
 * Собирает карточку витрины из товара, категории и магазина.
 *
 * <p>
 * Магазин необязателен: без него карточка остаётся, а название магазина пустое.
 * </p>
 *
 * @since 0.0.1
 * @author lllnightlll
 * @see PublicProductCard
 */
class PublicProductMapper {
    /**
     * Собирает одну карточку.
     *
     * @param product товар каталога
     * @param category уже проверенная публичная категория этого товара
     * @param shop магазин продавца или `null`, если аккаунт не найден
     * @return карточка для гостя
     */
    fun toCard(product: ProductModel, category: CategoryModel, shop: SellerShop?): PublicProductCard {
        return PublicProductCard(
            id = product.id,
            title = product.title,
            description = product.description,
            price = product.price,
            categoryId = category.id,
            categoryName = category.name,
            categorySlug = category.slug,
            images = product.images,
            sellerId = product.sellerId,
            companyName = shop?.companyName,
            sellerDescription = shop?.description,
            createdAt = product.createdAt,
            updatedAt = product.updatedAt,
        )
    }
}
