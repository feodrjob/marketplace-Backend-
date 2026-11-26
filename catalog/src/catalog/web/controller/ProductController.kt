package catalog.web.controller

import catalog.domain.service.PublicCatalogService
import catalog.web.dto.ProductResponse
import catalog.web.dto.ProductPageResponse
import catalog.web.mapper.ProductQueryMapper
import org.springframework.util.MultiValueMap
import org.springframework.web.bind.annotation.RequestParam
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
    private val productQueryMapper: ProductQueryMapper,
) {

    // Возвращает товары публичной витрины.
    @GetMapping
    fun listProducts(@RequestParam parameters: MultiValueMap<String, String>): ProductPageResponse {
        val query = productQueryMapper.toQuery(parameters)
        return productMapper.toResponse(catalogService.listProducts(query))
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