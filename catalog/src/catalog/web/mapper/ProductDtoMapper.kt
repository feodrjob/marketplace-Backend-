package catalog.web.mapper

import catalog.domain.model.ProductPage
import catalog.web.dto.ProductPageResponse
import catalog.domain.model.ProductImageRef
import catalog.domain.model.PublicProductCard
import catalog.web.dto.ProductImageResponse
import catalog.web.dto.ProductResponse
import org.springframework.stereotype.Component

/**
 * Преобразует карточки товаров в DTO.
 */
@Component
class ProductDtoMapper {
    fun toResponse(page: ProductPage): ProductPageResponse = ProductPageResponse(
        items = toResponseList(page.items),
        page = page.page,
        size = page.size,
        total = page.total,
    )


    // Преобразует карточку в ответ.
    fun toResponse(product: PublicProductCard): ProductResponse {
        return ProductResponse(
            id = product.id,
            title = product.title,
            description = product.description,
            price = product.price,
            categoryId = product.categoryId,
            categoryName = product.categoryName,
            categorySlug = product.categorySlug,
            images = product.images.map { toImageResponse(it) },
            sellerId = product.sellerId,
            companyName = product.companyName,
            sellerDescription = product.sellerDescription,
            createdAt = product.createdAt,
            updatedAt = product.updatedAt,
        )
    }

    // Преобразует список товаров.
    fun toResponseList(products: List<PublicProductCard>): List<ProductResponse> {
        return products.map { toResponse(it) }
    }

    // Преобразует ссылку на изображение.
    private fun toImageResponse(image: ProductImageRef): ProductImageResponse {
        return ProductImageResponse(
            mediaId = image.mediaId,
            sortOrder = image.sortOrder,
        )
    }
}