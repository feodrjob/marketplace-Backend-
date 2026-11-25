package catalog.domain.service

import catalog.common.enums.ProductStatus
import catalog.domain.model.CategoryModel
import catalog.domain.model.ProductFilterClause
import catalog.domain.model.ProductListingQuery
import catalog.domain.model.ProductModel
import catalog.domain.support.InMemoryCategoryRepository
import catalog.domain.support.InMemoryProductRepository
import catalog.domain.support.InMemorySellerShopLookup
import catalog.exception.InvalidCatalogQueryException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * Проверки поиска, фильтров, сортировки и страницы US-2.2 без базы.
 *
 * @since 0.0.1
 * @author lllnightlll
 * @see PublicCatalogService
 */
class PublicCatalogSearchTest {
    private lateinit var categories: InMemoryCategoryRepository
    private lateinit var products: InMemoryProductRepository
    private lateinit var catalog: PublicCatalogService

    private lateinit var booksId: UUID
    private lateinit var autoId: UUID
    private lateinit var sellerId: UUID
    private lateinit var otherSellerId: UUID

    @BeforeEach
    fun setUp() {
        categories = InMemoryCategoryRepository()
        products = InMemoryProductRepository()
        catalog = PublicCatalogService(categories, products, InMemorySellerShopLookup())
        booksId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        autoId = UUID.fromString("00000000-0000-0000-0000-000000000003")
        sellerId = UUID.fromString("00000000-0000-0000-0000-000000000010")
        otherSellerId = UUID.fromString("00000000-0000-0000-0000-000000000011")
        categories.save(CategoryModel(booksId, null, "Books", "books", 1, true))
        categories.save(CategoryModel(autoId, null, "Auto", "auto", 2, true))
    }

    @Test
    @DisplayName("US-2.2: поиск по названию и описанию ставит название выше и принимает опечатку")
    fun ranksTitleAboveDescriptionAndAcceptsTrigramTypo() {
        save("Новая книга", "учебник", 30L, booksId, sellerId, CREATED)
        save("Фильтр", "сборник книг", 10L, autoId, sellerId, LATEST)
        save("Черновик", "книга", 5L, booksId, sellerId, LATER, ProductStatus.HIDDEN)

        val found = catalog.listProducts(ProductListingQuery.parse(text = "книг"))

        assertEquals(listOf("Новая книга", "Фильтр"), found.items.map { it.title })
        assertEquals(2, found.total)

        val typo = catalog.listProducts(ProductListingQuery.parse(text = "кнгиа"))
        assertEquals("Новая книга", typo.items.first().title)
        assertTrue(typo.items.none { card -> card.title == "Черновик" })
    }

    @Test
    @DisplayName("US-2.2: цена, продавец и несколько категорий сужают витрину")
    fun filtersByPriceSellerAndCategories() {
        save("Дешёвая книга", null, 10L, booksId, sellerId, CREATED)
        save("Дорогая книга", null, 50L, booksId, otherSellerId, LATER)
        save("Колесо", null, 15L, autoId, sellerId, LATER)

        val priced = catalog.listProducts(
            ProductListingQuery.parse(filters = listOf(ProductFilterClause("price", "10,20", false))),
        )
        assertEquals(listOf("Колесо", "Дешёвая книга"), priced.items.map { it.title })

        val seller = catalog.listProducts(
            ProductListingQuery.parse(filters = listOf(ProductFilterClause("sellerId", otherSellerId.toString(), false))),
        )
        assertEquals(listOf("Дорогая книга"), seller.items.map { it.title })

        val categories = catalog.listProducts(
            ProductListingQuery.parse(filters = listOf(ProductFilterClause("category", "books,auto", false))),
        )
        assertEquals(3, categories.total)

        val excluded = catalog.listProducts(
            ProductListingQuery.parse(
                filters = listOf(ProductFilterClause("sellerId", sellerId.toString(), true)),
            ),
        )
        assertEquals(listOf("Дорогая книга"), excluded.items.map { it.title })
    }

    @Test
    @DisplayName("US-2.2: сортировка по цене и дате режет страницу, не теряя общее число")
    fun sortsAndPaginates() {
        save("Средняя", null, 20L, booksId, sellerId, CREATED)
        save("Дорогая", null, 40L, booksId, sellerId, LATER)
        save("Дешёвая", null, 5L, autoId, sellerId, LATEST)

        val byPrice = catalog.listProducts(ProductListingQuery.parse(sort = "price,asc", size = "2"))
        assertEquals(listOf("Дешёвая", "Средняя"), byPrice.items.map { it.title })
        assertEquals(0, byPrice.page)
        assertEquals(2, byPrice.size)
        assertEquals(3, byPrice.total)

        val next = catalog.listProducts(ProductListingQuery.parse(sort = "price,asc", page = "1", size = "2"))
        assertEquals(listOf("Дорогая"), next.items.map { it.title })

        val byDate = catalog.listProducts(ProductListingQuery.parse(sort = "createdAt,desc", size = "2"))
        assertEquals(listOf("Дешёвая", "Дорогая"), byDate.items.map { it.title })

        val pastEnd = catalog.listProducts(ProductListingQuery.parse(page = "5", size = "2"))
        assertTrue(pastEnd.items.isEmpty())
        assertEquals(3, pastEnd.total)
    }

    @Test
    @DisplayName("US-2.2: кривой фильтр, перевёрнутая цена и плохая страница дают ошибку")
    fun rejectsInvalidQueryInsteadOfIgnoringIt() {
        assertMessage("Unknown filter field") {
            ProductListingQuery.parse(filters = listOf(ProductFilterClause("color", "red", false)))
        }
        assertMessage("Price minimum is greater than maximum") {
            ProductListingQuery.parse(filters = listOf(ProductFilterClause("price", "20,10", false)))
        }
        assertMessage("Filter value is invalid") {
            ProductListingQuery.parse(filters = listOf(ProductFilterClause("sellerId", "not-a-uuid", false)))
        }
        assertMessage("Sort value is invalid") {
            ProductListingQuery.parse(sort = "title,asc")
        }
        assertMessage("Page is invalid") {
            ProductListingQuery.parse(page = "-1")
        }
        assertMessage("Page size is invalid") {
            ProductListingQuery.parse(size = "0")
        }
        assertMessage("Search text is invalid") {
            ProductListingQuery.parse(text = "???")
        }
    }

    private fun assertMessage(message: String, call: () -> Unit) {
        val error = assertThrows(InvalidCatalogQueryException::class.java) { call() }
        assertEquals(message, error.message)
    }

    private fun save(
        title: String,
        description: String?,
        price: Long,
        categoryId: UUID,
        productSellerId: UUID,
        createdAt: Instant,
        status: ProductStatus = ProductStatus.ACTIVE,
    ) {
        val id = UUID.randomUUID()
        products.save(
            ProductModel(
                id = id,
                sellerId = productSellerId,
                categoryId = categoryId,
                title = title,
                description = description,
                price = price,
                status = status,
                images = emptyList(),
                createdAt = createdAt,
                updatedAt = createdAt,
            ),
        )
    }

    private companion object {
        val CREATED: Instant = Instant.parse("2026-11-19T10:00:00Z")
        val LATER: Instant = Instant.parse("2026-11-19T12:00:00Z")
        val LATEST: Instant = Instant.parse("2026-11-19T14:00:00Z")
    }
}
