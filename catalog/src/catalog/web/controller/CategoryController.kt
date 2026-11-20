package catalog.web.controller

import catalog.domain.model.CategoryListingQuery
import catalog.domain.service.PublicCatalogService
import catalog.web.dto.CategoryResponse
import catalog.web.mapper.CategoryDtoMapper
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Отдаёт публичные категории через HTTP.
 */
@RestController
@RequestMapping("/api/v1/catalog/categories")
class CategoryController(
    private val catalogService: PublicCatalogService,
    private val categoryMapper: CategoryDtoMapper,
) {

    // Возвращает публичные категории.
    @GetMapping
    fun listCategories(
        @RequestParam(name = "tree", defaultValue = "true")
        tree: Boolean,

        @RequestParam(name = "parentId", required = false)
        parentId: UUID?,
    ): List<CategoryResponse> {
        val query = CategoryListingQuery(
            tree = tree,
            parentId = parentId,
        )

        val categories = catalogService.listCategories(query)

        return categoryMapper.toResponseList(categories)
    }
}