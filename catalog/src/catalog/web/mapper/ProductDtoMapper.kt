package catalog.web.mapper

import catalog.domain.model.ProductImageRef
import catalog.domain.model.ProductPage
import catalog.domain.model.PublicProductCard
import catalog.web.dto.product.ProductImageResponse
import catalog.web.dto.product.ProductPageResponse
import catalog.web.dto.product.ProductResponse
import org.springframework.stereotype.Component

/*
* Преобразует доменные карточки и страницы товаров в HTTP ответы
* */
@Component
class ProductDtoMapper {
    fun toResponse(page: ProductPage): ProductPageResponse = ProductPageResponse(
        items = page.items.map { toResponse(it) },
        page = page.page,
        size = page.size,
        total = page.total,
    )

    fun toResponse(product: PublicProductCard): ProductResponse = ProductResponse(
        id = product.id,
        title = product.title,
        description = product.description,
        price = product.price,
        categoryId = product.categoryId,
        categoryName = product.categoryName,
        categorySlug = product.categorySlug,
        images = product.images.map { toResponse(it) },
        sellerId = product.sellerId,
        companyName = product.companyName,
        sellerDescription = product.sellerDescription,
        createdAt = product.createdAt,
        updatedAt = product.updatedAt,
    )

    private fun toResponse(image: ProductImageRef): ProductImageResponse = ProductImageResponse(
        mediaId = image.mediaId,
        sortOrder = image.sortOrder,
    )
}
