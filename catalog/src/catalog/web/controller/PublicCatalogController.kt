package catalog.web.controller

import catalog.domain.model.CategoryListingQuery
import catalog.domain.service.PublicCatalogService
import catalog.exception.InvalidCatalogQueryException
import catalog.web.dto.category.CategoryResponse
import catalog.web.dto.product.ProductPageResponse
import catalog.web.dto.product.ProductResponse
import catalog.web.mapper.CategoryDtoMapper
import catalog.web.mapper.ProductDtoMapper
import catalog.web.mapper.ProductQueryMapper
import org.springframework.util.MultiValueMap
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/*
* Принимает публичные запросы категорий и товаров каталога
* */
@RestController
@RequestMapping("/api/v1/catalog")
class PublicCatalogController(
    private val publicCatalog: PublicCatalogService,
    private val productQueryMapper: ProductQueryMapper,
    private val productDtoMapper: ProductDtoMapper,
    private val categoryDtoMapper: CategoryDtoMapper,
) {
    @GetMapping("/products")
    fun listProducts(@RequestParam parameters: MultiValueMap<String, String>): ProductPageResponse {
        val query = productQueryMapper.toQuery(parameters)
        return productDtoMapper.toResponse(publicCatalog.listProducts(query))
    }

    @GetMapping("/products/{id}")
    fun getProduct(@PathVariable("id") id: UUID): ProductResponse {
        return productDtoMapper.toResponse(publicCatalog.getProduct(id))
    }

    @GetMapping("/categories")
    fun listCategories(@RequestParam parameters: MultiValueMap<String, String>): List<CategoryResponse> {
        for ((name, values) in parameters) {
            if (name !in setOf("tree", "parentId") || values.size != 1) {
                throw InvalidCatalogQueryException("Invalid category query parameter: $name")
            }
        }
        val tree = when (parameters.getFirst("tree")) {
            null, "true" -> true
            "false" -> false
            else -> throw InvalidCatalogQueryException("tree must be true or false")
        }
        val parentId = parameters.getFirst("parentId")?.let {
            try {
                UUID.fromString(it)
            } catch (_: IllegalArgumentException) {
                throw InvalidCatalogQueryException("parentId must be a UUID")
            }
        }
        return publicCatalog.listCategories(CategoryListingQuery(tree, parentId))
            .map { categoryDtoMapper.toResponse(it) }
    }
}
