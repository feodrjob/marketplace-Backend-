package catalog.web.controller

import catalog.domain.model.CategoryListingQuery
import catalog.domain.service.PublicCatalogService
import catalog.exception.InvalidCatalogQueryException
import catalog.web.dto.CategoryResponse
import catalog.web.mapper.CategoryDtoMapper
import org.springframework.util.MultiValueMap
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/*
* Принимает публичные запросы дерева и списка категорий
* */
@RestController
@RequestMapping("/api/v1/catalog/categories")
class CategoryController(
    private val catalogService: PublicCatalogService,
    private val categoryMapper: CategoryDtoMapper,
) {
    @GetMapping
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
        return catalogService.listCategories(CategoryListingQuery(tree, parentId))
            .map { categoryMapper.toResponse(it) }
    }
}
