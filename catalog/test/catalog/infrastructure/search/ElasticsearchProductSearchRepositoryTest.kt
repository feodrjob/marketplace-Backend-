package catalog.infrastructure.search

import catalog.common.enums.ProductStatus
import catalog.domain.model.ProductFilterClause
import catalog.domain.model.ProductListingQuery
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.util.UUID

class ElasticsearchProductSearchRepositoryTest : ElasticsearchUnitTestConfiguration() {

    @Autowired
    private lateinit var searchRepository: ElasticsearchProductSearchRepository

    private val booksId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val autoId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val hiddenCategoryId = UUID.fromString("00000000-0000-0000-0000-000000000003")
    private val sellerA = UUID.fromString("00000000-0000-0000-0000-000000000010")
    private val sellerB = UUID.fromString("00000000-0000-0000-0000-000000000011")

    @Test
    @DisplayName("US-2.2 ES: русская морфология, вес title > description, опечатки и сокрытие неактивных")
    fun searchesWithRussianMorphologyTypoAndVisibility() {
        val bookTitle = doc("00000000-0000-0000-0000-000000000101", "Новая книга", "учебник", 30L, booksId, "books", true, sellerA)
        val bookDesc = doc("00000000-0000-0000-0000-000000000102", "Фильтр", "сборник книг", 10L, autoId, "auto", true, sellerA)
        val hidden = doc("00000000-0000-0000-0000-000000000103", "Черновик книга", "книга", 5L, booksId, "books", true, sellerA, ProductStatus.HIDDEN)
        val closedCat = doc("00000000-0000-0000-0000-000000000104", "Секретная книга", "книга", 5L, hiddenCategoryId, "closed", false, sellerA)

        indexRepository.saveAll(listOf(bookTitle, bookDesc, hidden, closedCat))

        val found = searchRepository.search(ProductListingQuery.parse(text = "книги"))
        assertEquals(listOf(UUID.fromString(bookTitle.id), UUID.fromString(bookDesc.id)), found.productIds)
        assertEquals(2, found.total)

        val typo = searchRepository.search(ProductListingQuery.parse(text = "кнгиа"))
        assertEquals(UUID.fromString(bookTitle.id), typo.productIds.first())
        assertTrue(UUID.fromString(hidden.id) !in typo.productIds)
        assertTrue(UUID.fromString(closedCat.id) !in typo.productIds)

        val animals = doc("00000000-0000-0000-0000-000000000105", "Большая книга", "энциклопедия диких животных", 40L, booksId, "books", true, sellerA)
        indexRepository.save(animals)

        val withStopWord = searchRepository.search(ProductListingQuery.parse(text = "книги о животных"))
        assertEquals(listOf(UUID.fromString(animals.id)), withStopWord.productIds)
    }

    @Test
    @DisplayName("US-2.2 ES: фильтры цены, продавца, категорий (включая !=), сортировка и пагинация")
    fun filtersSortsAndPaginates() {
        val cheap = doc("00000000-0000-0000-0000-000000000201", "Дешёвая книга", null, 10L, booksId, "books", true, sellerA, createdAt = Instant.parse("2026-01-01T00:00:00Z"))
        val expensive = doc("00000000-0000-0000-0000-000000000202", "Дорогая книга", null, 50L, booksId, "books", true, sellerB, createdAt = Instant.parse("2026-01-02T00:00:00Z"))
        val wheel = doc("00000000-0000-0000-0000-000000000203", "Колесо", null, 15L, autoId, "auto", true, sellerA, createdAt = Instant.parse("2026-01-03T00:00:00Z"))

        indexRepository.saveAll(listOf(cheap, expensive, wheel))

        val priced = searchRepository.search(
            ProductListingQuery.parse(
                filters = listOf(ProductFilterClause("price", "10,20", false)),
                sort = "price,asc",
            ),
        )
        assertEquals(listOf(UUID.fromString(cheap.id), UUID.fromString(wheel.id)), priced.productIds)
        assertEquals(2, priced.total)

        val excludedSeller = searchRepository.search(
            ProductListingQuery.parse(
                filters = listOf(ProductFilterClause("sellerId", sellerA.toString(), true)),
            ),
        )
        assertEquals(listOf(UUID.fromString(expensive.id)), excludedSeller.productIds)

        val paged = searchRepository.search(
            ProductListingQuery.parse(
                filters = listOf(ProductFilterClause("category", "books,auto", false)),
                sort = "createdAt,desc",
                page = "1",
                size = "1",
            ),
        )
        assertEquals(listOf(UUID.fromString(expensive.id)), paged.productIds)
        assertEquals(3, paged.total)
    }

    private fun doc(
        id: String,
        title: String,
        description: String?,
        price: Long,
        categoryId: UUID,
        categorySlug: String,
        categoryActive: Boolean,
        sellerId: UUID,
        status: ProductStatus = ProductStatus.ACTIVE,
        createdAt: Instant = Instant.parse("2026-01-01T00:00:00Z"),
    ): ProductDocument = ProductDocument(
        id = id,
        productId = id,
        sellerId = sellerId.toString(),
        categoryId = categoryId.toString(),
        categorySlug = categorySlug,
        categoryActive = categoryActive,
        status = status.name,
        title = title,
        description = description,
        price = price,
        createdAt = createdAt,
    )
}
