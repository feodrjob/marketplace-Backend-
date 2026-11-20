package catalog.web.controller

import catalog.domain.service.PublicCatalogService
import catalog.web.dto.ProductResponse
import catalog.web.mapper.ProductDtoMapper
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Отдаёт публичный каталог через HTTP.
 */
@RestController
@RequestMapping("/api/v1/catalog/products")
class ProductController(
    private val catalogService: PublicCatalogService,
    private val productMapper: ProductDtoMapper,
) {

    // Возвращает товары публичной витрины.
    @GetMapping
    fun listProducts(): List<ProductResponse> {
        val products = catalogService.listProducts()

        return productMapper.toResponseList(products)
    }

    // Возвращает карточку публичного товара.
    @GetMapping("/{id}")
    fun getProduct(
        @PathVariable(name = "id") productId: UUID,
    ): ProductResponse {
        val product = catalogService.getProduct(productId)

        return productMapper.toResponse(product)
    }
}