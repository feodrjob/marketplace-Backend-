package catalog.web.controller

import catalog.domain.model.CategoryListingQuery
import catalog.web.dto.error.ProblemResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.Parameters
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.media.ArraySchema
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
@Tag(name = "Categories", description = "Публичные категории витрины")
@RestController
@RequestMapping("/api/v1/catalog/categories")
class CategoryController(
    private val catalogService: PublicCatalogService,
    private val categoryMapper: CategoryDtoMapper,
) {
    @Operation(operationId = "listCatalogCategories", summary = "Получить категории витрины", description = "Категория и все её предки должны быть активны. Порядок: sortOrder, name, id. При parentId возвращаются потомки без самого родителя. В плоском списке children пустой. Неизвестные и повторные параметры возвращают 400")
    @Parameters(
        Parameter(name = "tree", `in` = ParameterIn.QUERY, description = "true — дерево, false — плоский список. Принимаются только true и false в нижнем регистре", schema = Schema(type = "boolean", defaultValue = "true")),
        Parameter(name = "parentId", `in` = ParameterIn.QUERY, description = "UUID родителя поддерева. Неизвестный или неактивный родитель возвращает 404", schema = Schema(type = "string", format = "uuid")),
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Список корней дерева или плоский список категорий", content = [Content(mediaType = "application/json", array = ArraySchema(schema = Schema(implementation = CategoryResponse::class)))]),
        ApiResponse(responseCode = "400", description = "Некорректные параметры запроса или UUID", content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemResponse::class))]),
        ApiResponse(responseCode = "404", description = "Ресурс отсутствует или недоступен для публичной витрины", content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemResponse::class))]),
        ApiResponse(responseCode = "503", description = "PostgreSQL временно недоступен", content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemResponse::class))]),
    )
    @GetMapping
    fun listCategories(@Parameter(hidden = true) @RequestParam parameters: MultiValueMap<String, String>): List<CategoryResponse> {
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
