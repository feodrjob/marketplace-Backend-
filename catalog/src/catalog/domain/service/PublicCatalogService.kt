package catalog.domain.service

import catalog.domain.mapper.PublicProductMapper
import catalog.domain.model.CategoryListingQuery
import catalog.domain.model.CategoryModel
import catalog.domain.model.CategoryNode
import catalog.domain.model.CatalogTextScore
import catalog.domain.model.ProductListingQuery
import catalog.domain.model.ProductModel
import catalog.domain.model.ProductPage
import catalog.domain.model.PublicProductCard
import catalog.domain.repository.CategoryRepository
import catalog.domain.repository.ProductRepository
import catalog.domain.repository.ProductSearchRepository
import catalog.domain.repository.SellerShopLookup
import catalog.exception.CategoryNotFoundException
import catalog.exception.ProductNotFoundException
import java.util.UUID

/**
 * Публичная витрина.
 *
 * <p>
 * Отдаёт категории, список товаров [catalog.common.enums.ProductStatus.ACTIVE]
 * и карточку. Скрытый и архивный товар для гостя неотличим от отсутствующего.
 * Поиск, фильтры, сортировка и страница применяются после этого отбора.
 * </p>
 *
 * @param categories хранилище категорий
 * @param products хранилище товаров
 * @param sellerShops источник названия магазина
 * @param visibility правило активной ветки категорий
 * @param productCards сборщик карточки
 * @since 0.0.1
 * @author lllnightlll
 * @see CategoryVisibility
 */
class PublicCatalogService(
    private val categories: CategoryRepository,
    private val products: ProductRepository,
    private val sellerShops: SellerShopLookup,
    private val visibility: CategoryVisibility = CategoryVisibility(),
    private val productCards: PublicProductMapper = PublicProductMapper(),
    private val search: ProductSearchRepository? = null,
) {
    /**
     * Возвращает публичные категории.
     *
     * @param query дерево или плоский список и необязательный родитель
     * @return узлы в порядке sortOrder, затем name
     * @throws CategoryNotFoundException если родитель неизвестен или неактивен
     */
    fun listCategories(query: CategoryListingQuery): List<CategoryNode> {
        val visible = visibility.visible(categories.findAll())
        val scope = scope(query.parentId, visible).sortedWith(CATEGORY_ORDER)
        if (!query.tree) {
            return scope.map { leaf(it) }
        }
        return tree(scope, query.parentId)
    }

    /**
     * Возвращает товары витрины без страницы.
     *
     * <p>Только ACTIVE в публичной категории. Более новые идут первыми.</p>
     *
     * @return карточки без скрытых и архивных товаров
     */
    fun listProducts(): List<PublicProductCard> {
        return listProducts(ProductListingQuery.all()).items
    }

    /**
     * Ищет, фильтрует, сортирует и режет страницу витрины.
     *
     * <p>
     * Скрытые и архивные товары в выдачу не попадают.
     * Текст без явной сортировки упорядочивает карточки по весу совпадения.
     * </p>
     *
     * @param query проверенные параметры US-2.2
     * @return страница карточек и полное число совпадений
     */
    fun listProducts(query: ProductListingQuery): ProductPage {
        if (search != null) return searchProducts(query)
        val categoriesById = visibility.index(categories.findAll())
        val matched = products.findAll()
            .filter { product -> isOnStorefront(product, categoriesById) }
            .mapNotNull { product -> scored(product, query) }
            .filter { scored -> matches(scored.product, categoriesById.getValue(scored.product.categoryId), query) }
            .sortedWith(order(query))
        val pageItems = if (query.unpaged) matched else slice(matched, query.page, query.size)
        return ProductPage(
            items = pageItems.map { scored -> toCard(scored.product, categoriesById.getValue(scored.product.categoryId)) },
            page = query.page,
            size = if (query.unpaged) pageItems.size else query.size,
            total = matched.size,
        )
    }

    /**
     * Открывает карточку товара.
     *
     * @param productId идентификатор товара
     * @return карточка с категорией, фото и магазином
     * @throws ProductNotFoundException если товара нет, он скрыт, архивный или лежит в неактивной категории
     */
    fun getProduct(productId: UUID): PublicProductCard {
        val product = products.findById(productId) ?: throw ProductNotFoundException()
        val categoriesById = visibility.index(categories.findAll())
        if (!isOnStorefront(product, categoriesById)) {
            throw ProductNotFoundException()
        }
        return toCard(product, categoriesById.getValue(product.categoryId))
    }

    private fun scope(parentId: UUID?, visible: List<CategoryModel>): List<CategoryModel> {
        if (parentId == null) {
            return visible.toList()
        }
        if (visible.none { category -> category.id == parentId }) {
            throw CategoryNotFoundException()
        }
        val childrenByParent = childrenByParent(visible)
        val collected = mutableListOf<CategoryModel>()
        collectDescendants(parentId, childrenByParent, collected)
        return collected
    }

    private fun searchProducts(query: ProductListingQuery): ProductPage {
        val result = checkNotNull(search).search(query)
        val categoriesById = visibility.index(categories.findAll())
        val productsById = products.findAllByIds(result.productIds).associateBy { it.id }
        val visible = result.productIds.mapNotNull { productsById[it] }
            .filter { isOnStorefront(it, categoriesById) }
        val shops = visible.map { it.sellerId }.distinct().associateWith { sellerShops.findBySellerId(it) }
        return ProductPage(
            items = visible.map {
                productCards.toCard(it, categoriesById.getValue(it.categoryId), shops[it.sellerId])
            },
            page = query.page,
            size = if (query.unpaged) visible.size else query.size,
            total = result.total,
        )
    }

    private fun collectDescendants(
        parentId: UUID,
        childrenByParent: Map<UUID?, List<CategoryModel>>,
        collected: MutableList<CategoryModel>,
    ) {
        for (child in childrenByParent[parentId].orEmpty()) {
            collected.add(child)
            collectDescendants(child.id, childrenByParent, collected)
        }
    }

    private fun tree(scope: List<CategoryModel>, parentId: UUID?): List<CategoryNode> {
        val childrenByParent = childrenByParent(scope)
        val roots = scope.filter { category ->
            if (parentId == null) category.parentId == null else parentId == category.parentId
        }.sortedWith(CATEGORY_ORDER)
        return roots.map { category -> node(category, childrenByParent) }
    }

    private fun node(
        category: CategoryModel,
        childrenByParent: Map<UUID?, List<CategoryModel>>,
    ): CategoryNode {
        val nested = childrenByParent[category.id].orEmpty().map { child -> node(child, childrenByParent) }
        return CategoryNode(
            id = category.id,
            parentId = category.parentId,
            name = category.name,
            slug = category.slug,
            sortOrder = category.sortOrder,
            children = nested,
        )
    }

    private fun leaf(category: CategoryModel): CategoryNode {
        return CategoryNode(
            id = category.id,
            parentId = category.parentId,
            name = category.name,
            slug = category.slug,
            sortOrder = category.sortOrder,
            children = emptyList(),
        )
    }

    private fun childrenByParent(categories: List<CategoryModel>): Map<UUID?, List<CategoryModel>> {
        val grouped = mutableMapOf<UUID?, MutableList<CategoryModel>>()
        for (category in categories) {
            grouped.getOrPut(category.parentId) { mutableListOf() }.add(category)
        }
        grouped.values.forEach { children -> children.sortWith(CATEGORY_ORDER) }
        return grouped
    }

    private fun scored(product: ProductModel, query: ProductListingQuery): ScoredProduct? {
        val text = query.text ?: return ScoredProduct(product, 0.0)
        val score = CatalogTextScore.score(product.title, product.description, text)
        if (score == 0.0) {
            return null
        }
        return ScoredProduct(product, score)
    }

    private fun matches(product: ProductModel, category: CategoryModel, query: ProductListingQuery): Boolean {
        if (query.priceMin != null && product.price < query.priceMin) {
            return false
        }
        if (query.priceMax != null && product.price > query.priceMax) {
            return false
        }
        if (query.sellerIds.isNotEmpty() && product.sellerId !in query.sellerIds) {
            return false
        }
        if (product.sellerId in query.excludedSellerIds) {
            return false
        }
        if (query.categorySlugs.isNotEmpty() && category.slug !in query.categorySlugs) {
            return false
        }
        if (category.slug in query.excludedCategorySlugs) {
            return false
        }
        if (query.categoryIds.isNotEmpty() && product.categoryId !in query.categoryIds) {
            return false
        }
        if (product.categoryId in query.excludedCategoryIds) {
            return false
        }
        return true
    }

    private fun order(query: ProductListingQuery): Comparator<ScoredProduct> {
        val sort = query.sort
        if (sort == null) {
            if (query.text != null) {
                return compareByDescending<ScoredProduct> { it.score }
                    .thenByDescending { it.product.createdAt }
                    .thenBy { it.product.id }
            }
            return compareByDescending<ScoredProduct> { it.product.createdAt }.thenBy { it.product.id }
        }
        val byField = when (sort.field) {
            "price" -> if (sort.ascending) {
                compareBy<ScoredProduct> { it.product.price }
            } else {
                compareByDescending { it.product.price }
            }
            else -> if (sort.ascending) {
                compareBy<ScoredProduct> { it.product.createdAt }
            } else {
                compareByDescending { it.product.createdAt }
            }
        }
        return byField.thenBy { it.product.id }
    }

    private fun <T> slice(items: List<T>, page: Int, size: Int): List<T> {
        val start = page.toLong() * size.toLong()
        if (start >= items.size) {
            return emptyList()
        }
        val from = start.toInt()
        val to = minOf(items.size, from + size)
        return items.subList(from, to)
    }

    private fun isOnStorefront(product: ProductModel, categoriesById: Map<UUID, CategoryModel>): Boolean {
        if (!product.isActive()) {
            return false
        }
        return visibility.isVisible(categoriesById[product.categoryId], categoriesById)
    }

    private fun toCard(product: ProductModel, category: CategoryModel): PublicProductCard {
        return productCards.toCard(product, category, sellerShops.findBySellerId(product.sellerId))
    }

    private class ScoredProduct(
        val product: ProductModel,
        val score: Double,
    )

    private companion object {
        val CATEGORY_ORDER = compareBy<CategoryModel> { it.sortOrder }.thenBy { it.name }.thenBy { it.id }
    }
}
