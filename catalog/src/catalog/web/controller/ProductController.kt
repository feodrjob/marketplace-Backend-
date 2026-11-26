package catalog.web.controller

import catalog.domain.service.PublicCatalogService
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
@Tag(name = "Products", description = "Публичный поиск и карточки товаров")
@RestController
@RequestMapping("/api/v1/catalog/products")
class ProductController(
    private val catalogService: PublicCatalogService,
    private val productMapper: ProductDtoMapper,
    private val productQueryMapper: ProductQueryMapper,
) {

    // Возвращает товары публичной витрины.
    @Operation(
        operationId = "listCatalogProducts",
        summary = "Найти товары публичной витрины",
        description = "Возвращает только ACTIVE товары в активной ветке категорий. " +
            "Разные фильтры объединяются через AND, значения одного фильтра через OR. " +
            "Все параметры передаются не более одного раза. Неизвестные параметры отклоняются с 400. " +
            "Отрицание цены не поддерживается. Для отрицания имя параметра заканчивается на !, например f[sellerId]!. " +
            "В URL f[sellerId]!=uuid знак = отделяет имя от значения. " +
            "total считается в Elasticsearch и может включать недоступные товары при устаревшем индексе.",
    )
    @Parameters(
        Parameter(name = "q", `in` = ParameterIn.QUERY, description = "Поиск по названию и описанию с русской морфологией и опечатками, пустой текст не ограничивает выдачу", example = "книги", schema = Schema(type = "string")),
        Parameter(name = "f[price]", `in` = ParameterIn.QUERY, description = "Включительный диапазон целых неотрицательных цен min,max, min не больше max", example = "100,5000", schema = Schema(type = "string")),
        Parameter(name = "f[category]", `in` = ParameterIn.QUERY, description = "Один или несколько slug категорий через запятую, достаточно совпадения с одним", example = "books,electronics", schema = Schema(type = "string")),
        Parameter(name = "f[categoryId]", `in` = ParameterIn.QUERY, description = "Один или несколько UUID категорий через запятую", example = "00000000-0000-0000-0000-000000000001", schema = Schema(type = "string")),
        Parameter(name = "f[sellerId]", `in` = ParameterIn.QUERY, description = "Один или несколько UUID аккаунтов продавцов через запятую", example = "00000000-0000-0000-0000-000000000010", schema = Schema(type = "string")),
        Parameter(name = "f[category]!", `in` = ParameterIn.QUERY, description = "Исключить категории по slug, несколько значений через запятую", example = "archived", schema = Schema(type = "string")),
        Parameter(name = "f[categoryId]!", `in` = ParameterIn.QUERY, description = "Исключить категории по UUID, несколько значений через запятую", example = "00000000-0000-0000-0000-000000000002", schema = Schema(type = "string")),
        Parameter(name = "f[sellerId]!", `in` = ParameterIn.QUERY, description = "Исключить продавцов по UUID аккаунта, несколько значений через запятую", example = "00000000-0000-0000-0000-000000000011", schema = Schema(type = "string")),
        Parameter(name = "sort", `in` = ParameterIn.QUERY, description = "Без sort: с q — релевантность, без q — createdAt,desc. При равенстве используется id", example = "price,asc", schema = Schema(type = "string", allowableValues = ["price,asc", "price,desc", "createdAt,asc", "createdAt,desc"])),
        Parameter(name = "page", `in` = ParameterIn.QUERY, description = "Номер страницы с нуля. Должно выполняться (page + 1) * size <= 10000", example = "0", schema = Schema(type = "integer", format = "int32", minimum = "0", defaultValue = "0")),
        Parameter(name = "size", `in` = ParameterIn.QUERY, description = "Размер страницы от 1 до 100. Должно выполняться (page + 1) * size <= 10000", example = "20", schema = Schema(type = "integer", format = "int32", minimum = "1", maximum = "100", defaultValue = "20")),
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Страница товаров", content = [Content(mediaType = "application/json", schema = Schema(implementation = ProductPageResponse::class), examples = [ExampleObject(name = "emptyPage", value = """{"items":[],"page":0,"size":20,"total":0}""")])]),
        ApiResponse(responseCode = "400", description = "Некорректные параметры запроса или UUID", content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemResponse::class))]),
        ApiResponse(responseCode = "503", description = "PostgreSQL Elasticsearch или Auth временно недоступны", content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemResponse::class))]),
    )
    @GetMapping
    fun listProducts(@Parameter(hidden = true) @RequestParam parameters: MultiValueMap<String, String>): ProductPageResponse {
        val query = productQueryMapper.toQuery(parameters)
        return productMapper.toResponse(catalogService.listProducts(query))
    }

    // Возвращает карточку публичного товара.
    @Operation(operationId = "getCatalogProduct", summary = "Получить карточку товара", description = "Скрытый, архивный, удалённый товар или товар неактивной ветки категорий возвращает 404")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Карточка товара", content = [Content(mediaType = "application/json", schema = Schema(implementation = ProductResponse::class))]),
        ApiResponse(responseCode = "400", description = "Некорректные параметры запроса или UUID", content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemResponse::class))]),
        ApiResponse(responseCode = "404", description = "Ресурс отсутствует или недоступен для публичной витрины", content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemResponse::class))]),
        ApiResponse(responseCode = "503", description = "PostgreSQL или Auth временно недоступны", content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemResponse::class))]),
    )
    @GetMapping("/{id}")
    fun getProduct(
        @Parameter(description = "UUID товара", required = true, example = "00000000-0000-0000-0000-000000000101", schema = Schema(type = "string", format = "uuid"))
        @PathVariable(name = "id") productId: UUID,
    ): ProductResponse {
        val product = catalogService.getProduct(productId)

        return productMapper.toResponse(product)
    }
}