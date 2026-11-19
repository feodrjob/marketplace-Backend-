package catalog.domain.mapper

import catalog.domain.model.CategoryModel
import catalog.domain.model.ProductModel
import catalog.domain.model.PublicProductCard
import catalog.domain.model.SellerShop

class PublicProductMapper {
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
