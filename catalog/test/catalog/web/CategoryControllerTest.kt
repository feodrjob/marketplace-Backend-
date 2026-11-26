package catalog.web

import catalog.domain.model.CategoryModel
import catalog.domain.service.PublicCatalogService
import catalog.domain.support.InMemoryCategoryRepository
import catalog.domain.support.InMemoryProductRepository
import catalog.domain.support.InMemorySellerShopLookup
import catalog.web.controller.CategoryController
import catalog.web.exception.GlobalExceptionHandler
import catalog.web.mapper.CategoryDtoMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.UUID

/**
 * Проверяет HTTP-контракт публичных категорий.
 */
class CategoryControllerTest {

    private lateinit var mockMvc: MockMvc

    private val rootId = UUID.randomUUID()
    private val childId = UUID.randomUUID()

    // Подготавливает контроллер и тестовые категории.
    @BeforeEach
    fun setUp() {
        val categories = InMemoryCategoryRepository()

        categories.save(
            CategoryModel(
                id = rootId,
                parentId = null,
                name = "Электроника",
                slug = "electronics",
                sortOrder = 0,
                active = true,
            )
        )

        categories.save(
            CategoryModel(
                id = childId,
                parentId = rootId,
                name = "Телефоны",
                slug = "phones",
                sortOrder = 0,
                active = true,
            )
        )

        categories.save(
            CategoryModel(
                id = UUID.randomUUID(),
                parentId = null,
                name = "Скрытая категория",
                slug = "hidden",
                sortOrder = 1,
                active = false,
            )
        )

        val service = PublicCatalogService(
            categories = categories,
            products = InMemoryProductRepository(),
            sellerShops = InMemorySellerShopLookup(),
        )

        val controller = CategoryController(
            catalogService = service,
            categoryMapper = CategoryDtoMapper(),
        )

        mockMvc = MockMvcBuilders
            .standaloneSetup(controller)
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    // Проверяет дерево и скрытые категории.
    @Test
    fun returnsVisibleCategoryTree() {
        mockMvc.perform(get("/api/v1/catalog/categories"))
            .andExpect(status().isOk)
            .andExpect(
                content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
            )
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(rootId.toString()))
            .andExpect(jsonPath("$[0].name").value("Электроника"))
            .andExpect(jsonPath("$[0].children.length()").value(1))
            .andExpect(
                jsonPath("$[0].children[0].id").value(childId.toString())
            )
    }

    // Проверяет плоский список потомков.
    @Test
    fun returnsFlatListForParent() {
        mockMvc.perform(
            get("/api/v1/catalog/categories")
                .param("tree", "false")
                .param("parentId", rootId.toString())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(childId.toString()))
            .andExpect(jsonPath("$[0].children").isEmpty)
    }

    // Проверяет неизвестную родительскую категорию.
    @Test
    fun returns404ForUnknownParent() {
        mockMvc.perform(
            get("/api/v1/catalog/categories")
                .param("parentId", UUID.randomUUID().toString())
        )
            .andExpect(status().isNotFound)
            .andExpect(
                content().contentTypeCompatibleWith(
                    MediaType.APPLICATION_PROBLEM_JSON
                )
            )
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(
                jsonPath("$.instance").value("/api/v1/catalog/categories")
            )
    }

    // Проверяет неверный UUID родителя.
    @Test
    fun returns400ForInvalidParentId() {
        mockMvc.perform(
            get("/api/v1/catalog/categories")
                .param("parentId", "invalid-uuid")
        )
            .andExpect(status().isBadRequest)
            .andExpect(
                content().contentTypeCompatibleWith(
                    MediaType.APPLICATION_PROBLEM_JSON
                )
            )
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.detail").exists())
    }

    // Проверяет неверный флаг дерева.
    @Test
    fun returns400ForInvalidTreeFlag() {
        mockMvc.perform(
            get("/api/v1/catalog/categories")
                .param("tree", "invalid")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
    }
}