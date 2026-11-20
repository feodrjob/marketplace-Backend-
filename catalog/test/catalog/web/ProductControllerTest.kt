package catalog.web

import catalog.common.enums.ProductStatus
import catalog.domain.model.CategoryModel
import catalog.domain.model.ProductImageRef
import catalog.domain.model.ProductModel
import catalog.domain.model.SellerShop
import catalog.domain.service.PublicCatalogService
import catalog.domain.support.InMemoryCategoryRepository
import catalog.domain.support.InMemoryProductRepository
import catalog.domain.support.InMemorySellerShopLookup
import catalog.web.controller.ProductController
import catalog.web.exception.GlobalExceptionHandler
import catalog.web.mapper.ProductDtoMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.UUID

/**
 * Проверяет HTTP-контракт публичных товаров.
 */
class ProductControllerTest {

    private lateinit var mockMvc: MockMvc
    private lateinit var products: InMemoryProductRepository

    private val categoryId = UUID.randomUUID()
    private val sellerId = UUID.randomUUID()
    private val activeId = UUID.randomUUID()
    private val hiddenId = UUID.randomUUID()
    private val archivedId = UUID.randomUUID()
    private val mediaId = UUID.randomUUID()

    // Подготавливает контроллер и тестовые товары.
    @BeforeEach
    fun setUp() {
        val categories = InMemoryCategoryRepository()
        val shops = InMemorySellerShopLookup()
        products = InMemoryProductRepository()

        categories.save(
            CategoryModel(
                id = categoryId,
                parentId = null,
                name = "Электроника",
                slug = "electronics",
                sortOrder = 0,
                active = true,
            )
        )

        shops.save(
            SellerShop(
                sellerId = sellerId,
                companyName = "Тестовый магазин",
                description = "Магазин электроники",
            )
        )

        saveProduct(activeId, ProductStatus.ACTIVE)
        saveProduct(hiddenId, ProductStatus.HIDDEN)
        saveProduct(archivedId, ProductStatus.ARCHIVED)

        val service = PublicCatalogService(
            categories = categories,
            products = products,
            sellerShops = shops,
        )

        val controller = ProductController(
            catalogService = service,
            productMapper = ProductDtoMapper(),
        )

        mockMvc = MockMvcBuilders
            .standaloneSetup(controller)
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    // Проверяет публичный список товаров.
    @Test
    fun returnsOnlyActiveProducts() {
        mockMvc.perform(get("/api/v1/catalog/products"))
            .andExpect(status().isOk)
            .andExpect(
                content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
            )
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(activeId.toString()))
    }

    // Проверяет содержимое карточки товара.
    @Test
    fun returnsActiveProductCard() {
        mockMvc.perform(
            get("/api/v1/catalog/products/{id}", activeId)
        )
            .andExpect(status().isOk)
            .andExpect(
                content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
            )
            .andExpect(jsonPath("$.id").value(activeId.toString()))
            .andExpect(jsonPath("$.title").value("Смартфон"))
            .andExpect(jsonPath("$.description").value("Описание товара"))
            .andExpect(jsonPath("$.price").value(1500))
            .andExpect(jsonPath("$.categoryId").value(categoryId.toString()))
            .andExpect(jsonPath("$.categoryName").value("Электроника"))
            .andExpect(jsonPath("$.categorySlug").value("electronics"))
            .andExpect(jsonPath("$.sellerId").value(sellerId.toString()))
            .andExpect(jsonPath("$.companyName").value("Тестовый магазин"))
            .andExpect(
                jsonPath("$.sellerDescription").value("Магазин электроники")
            )
            .andExpect(jsonPath("$.images.length()").value(1))
            .andExpect(
                jsonPath("$.images[0].mediaId").value(mediaId.toString())
            )
            .andExpect(jsonPath("$.images[0].sortOrder").value(0))
    }

    // Проверяет недоступность скрытого товара.
    @Test
    fun returns404ForHiddenProduct() {
        mockMvc.perform(
            get("/api/v1/catalog/products/{id}", hiddenId)
        )
            .andExpect(status().isNotFound)
            .andExpect(
                content().contentTypeCompatibleWith(
                    MediaType.APPLICATION_PROBLEM_JSON
                )
            )
            .andExpect(jsonPath("$.status").value(404))
    }

    // Проверяет недоступность архивного товара.
    @Test
    fun returns404ForArchivedProduct() {
        mockMvc.perform(
            get("/api/v1/catalog/products/{id}", archivedId)
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.status").value(404))
    }

    // Проверяет отсутствие неизвестного товара.
    @Test
    fun returns404ForUnknownProduct() {
        val unknownId = UUID.randomUUID()

        mockMvc.perform(
            get("/api/v1/catalog/products/{id}", unknownId)
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(
                jsonPath("$.instance")
                    .value("/api/v1/catalog/products/$unknownId")
            )
    }

    // Проверяет неверный UUID товара.
    @Test
    fun returns400ForInvalidProductId() {
        mockMvc.perform(
            get("/api/v1/catalog/products/invalid-uuid")
        )
            .andExpect(status().isBadRequest)
            .andExpect(
                content().contentTypeCompatibleWith(
                    MediaType.APPLICATION_PROBLEM_JSON
                )
            )
            .andExpect(jsonPath("$.status").value(400))
    }

    // Сохраняет товар заданного статуса.
    private fun saveProduct(id: UUID, status: ProductStatus) {
        val timestamp = Instant.parse("2026-11-20T10:00:00Z")

        products.save(
            ProductModel(
                id = id,
                sellerId = sellerId,
                categoryId = categoryId,
                title = "Смартфон",
                description = "Описание товара",
                price = 1500L,
                status = status,
                images = listOf(
                    ProductImageRef(
                        productId = id,
                        mediaId = mediaId,
                        sortOrder = 0,
                    )
                ),
                createdAt = timestamp,
                updatedAt = timestamp,
            )
        )
    }
}