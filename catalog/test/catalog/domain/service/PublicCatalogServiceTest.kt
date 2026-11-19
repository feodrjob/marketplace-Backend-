package catalog.domain.service

import catalog.common.enums.ProductStatus
import catalog.domain.model.CategoryListingQuery
import catalog.domain.model.CategoryModel
import catalog.domain.model.CategoryNode
import catalog.domain.model.ProductImageRef
import catalog.domain.model.ProductModel
import catalog.domain.model.PublicProductCard
import catalog.domain.model.SellerShop
import catalog.domain.support.InMemoryCategoryRepository
import catalog.domain.support.InMemoryProductRepository
import catalog.domain.support.InMemorySellerShopLookup
import catalog.exception.CategoryNotFoundException
import catalog.exception.ProductNotFoundException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * Проверки публичной витрины US-2.1 без базы.
 *
 * <p>
 * Дерево категорий, плоский список, скрытые товары и карточка
 * с ценой, фото и магазином.
 * </p>
 *
 * @since 0.0.1
 * @author lllnightlll
 * @see PublicCatalogService
 */
class PublicCatalogServiceTest {
    private lateinit var categories: InMemoryCategoryRepository
    private lateinit var products: InMemoryProductRepository
    private lateinit var shops: InMemorySellerShopLookup
    private lateinit var catalog: PublicCatalogService

    private lateinit var booksId: UUID
    private lateinit var comicsId: UUID
    private lateinit var autoId: UUID
    private lateinit var partsId: UUID
    private lateinit var closedId: UUID
    private lateinit var secretId: UUID
    private lateinit var sellerId: UUID

    @BeforeEach
    fun setUp() {
        categories = InMemoryCategoryRepository()
        products = InMemoryProductRepository()
        shops = InMemorySellerShopLookup()
        catalog = PublicCatalogService(categories, products, shops)

        booksId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        comicsId = UUID.fromString("00000000-0000-0000-0000-000000000002")
        autoId = UUID.fromString("00000000-0000-0000-0000-000000000003")
        partsId = UUID.fromString("00000000-0000-0000-0000-000000000004")
        closedId = UUID.fromString("00000000-0000-0000-0000-000000000005")
        secretId = UUID.fromString("00000000-0000-0000-0000-000000000006")
        sellerId = UUID.fromString("00000000-0000-0000-0000-000000000010")

        categories.save(category(booksId, null, "Books", "books", 1, true))
        categories.save(category(comicsId, booksId, "Comics", "comics", 0, true))
        categories.save(category(autoId, null, "Auto", "auto", 2, true))
        categories.save(category(partsId, autoId, "Parts", "parts", 0, true))
        categories.save(category(closedId, null, "Closed", "closed", 0, false))
        categories.save(category(secretId, closedId, "Secret", "secret", 0, true))
        shops.save(SellerShop(sellerId, "Romashka", "Магазин электроники"))
    }

    @Test
    @DisplayName("US-2.1: дерево витрины прячет неактивную ветку и сортирует sort_order, name")
    fun listsPublicCategoryTree() {
        val roots = catalog.listCategories(CategoryListingQuery.storefront())

        assertEquals(listOf("Books", "Auto"), roots.map(CategoryNode::name))
        assertEquals(listOf("Comics"), roots[0].children.map(CategoryNode::name))
        assertEquals(listOf("Parts"), roots[1].children.map(CategoryNode::name))
        assertTrue(roots.none { node -> node.name == "Closed" || node.name == "Secret" })
    }

    @Test
    @DisplayName("US-2.1: плоский список и поддерево родителя")
    fun listsFlatScopeAndSubtree() {
        val flat = catalog.listCategories(CategoryListingQuery(tree = false, parentId = null))

        assertEquals(listOf("Comics", "Parts", "Books", "Auto"), flat.map(CategoryNode::name))
        assertTrue(flat.all { node -> node.children.isEmpty() })

        val comicsOnly = catalog.listCategories(CategoryListingQuery(tree = true, parentId = booksId))
        assertEquals(listOf("Comics"), comicsOnly.map(CategoryNode::name))
        assertEquals(booksId, comicsOnly[0].parentId)
    }

    @Test
    @DisplayName("US-2.1: неизвестный или неактивный родитель категории недоступен")
    fun rejectsUnavailableCategoryParent() {
        assertThrows(CategoryNotFoundException::class.java) {
            catalog.listCategories(CategoryListingQuery(tree = true, parentId = closedId))
        }
        assertThrows(CategoryNotFoundException::class.java) {
            catalog.listCategories(CategoryListingQuery(tree = true, parentId = UUID.randomUUID()))
        }
    }

    @Test
    @DisplayName("US-2.1: в каталоге только ACTIVE в публичной категории, карточка собирает цену, фото и магазин")
    fun listsActiveProductsAndOpensCard() {
        val newer = UUID.fromString("00000000-0000-0000-0000-000000000021")
        val older = UUID.fromString("00000000-0000-0000-0000-000000000022")
        val secondImage = UUID.fromString("00000000-0000-0000-0000-000000000031")
        val firstImage = UUID.fromString("00000000-0000-0000-0000-000000000032")
        products.save(
            product(
                newer,
                booksId,
                "Новая книга",
                "описание",
                15L,
                ProductStatus.ACTIVE,
                LATER,
                listOf(ProductImageRef(newer, secondImage, 2), ProductImageRef(newer, firstImage, 0)),
            ),
        )
        products.save(product(older, partsId, "Фильтр", null, 40L, ProductStatus.ACTIVE, CREATED, emptyList()))
        products.save(product(UUID.randomUUID(), booksId, "Черновик", null, 10L, ProductStatus.HIDDEN, LATER, emptyList()))
        products.save(product(UUID.randomUUID(), booksId, "Архив", null, 10L, ProductStatus.ARCHIVED, LATER, emptyList()))
        products.save(product(UUID.randomUUID(), secretId, "Секрет", null, 10L, ProductStatus.ACTIVE, LATER, emptyList()))

        val listed = catalog.listProducts()

        assertEquals(listOf("Новая книга", "Фильтр"), listed.map(PublicProductCard::title))

        val card = catalog.getProduct(newer)
        assertEquals("описание", card.description)
        assertEquals(15L, card.price)
        assertEquals("Books", card.categoryName)
        assertEquals("books", card.categorySlug)
        assertEquals(listOf(firstImage, secondImage), card.images.map(ProductImageRef::mediaId))
        assertEquals(sellerId, card.sellerId)
        assertEquals("Romashka", card.companyName)
        assertEquals("Магазин электроники", card.sellerDescription)
    }

    @Test
    @DisplayName("US-2.1: скрытый товар и отсутствующий магазин")
    fun hidesUnavailableProductAndKeepsCardWithoutShop() {
        val hidden = UUID.fromString("00000000-0000-0000-0000-000000000041")
        val orphanSellerProduct = UUID.fromString("00000000-0000-0000-0000-000000000042")
        val missingSeller = UUID.fromString("00000000-0000-0000-0000-000000000099")
        products.save(product(hidden, booksId, "Скрытый", null, 5L, ProductStatus.HIDDEN, CREATED, emptyList()))
        products.save(
            product(
                orphanSellerProduct,
                booksId,
                "Без магазина",
                null,
                7L,
                ProductStatus.ACTIVE,
                CREATED,
                emptyList(),
                missingSeller,
            ),
        )

        assertThrows(ProductNotFoundException::class.java) { catalog.getProduct(hidden) }
        assertThrows(ProductNotFoundException::class.java) { catalog.getProduct(UUID.randomUUID()) }

        val card = catalog.getProduct(orphanSellerProduct)
        assertEquals(missingSeller, card.sellerId)
        assertNull(card.companyName)
        assertNull(card.sellerDescription)
    }

    private fun category(
        id: UUID,
        parentId: UUID?,
        name: String,
        slug: String,
        sortOrder: Int,
        active: Boolean,
    ): CategoryModel = CategoryModel(id, parentId, name, slug, sortOrder, active)

    private fun product(
        id: UUID,
        categoryId: UUID,
        title: String,
        description: String?,
        price: Long,
        status: ProductStatus,
        createdAt: Instant,
        images: List<ProductImageRef>,
        productSellerId: UUID = sellerId,
    ): ProductModel {
        return ProductModel(
            id = id,
            sellerId = productSellerId,
            categoryId = categoryId,
            title = title,
            description = description,
            price = price,
            status = status,
            images = images,
            createdAt = createdAt,
            updatedAt = createdAt,
        )
    }

    private companion object {
        val CREATED: Instant = Instant.parse("2026-11-19T10:00:00Z")
        val LATER: Instant = Instant.parse("2026-11-19T12:00:00Z")
    }
}
