package catalog.domain.service

import catalog.domain.mapper.PublicProductMapper
import catalog.domain.model.CategoryListingQuery
import catalog.domain.model.CategoryModel
import catalog.domain.model.CategoryNode
import catalog.domain.model.ProductModel
import catalog.domain.model.PublicProductCard
import catalog.domain.repository.CategoryRepository
import catalog.domain.repository.ProductRepository
import catalog.domain.repository.SellerShopLookup
import catalog.exception.CategoryNotFoundException
import catalog.exception.ProductNotFoundException
import java.util.UUID

// Публичная витрина US-2.1: категории, список ACTIVE-товаров и карточка.
class PublicCatalogService(
    private val categories: CategoryRepository,
    private val products: ProductRepository,
    private val sellerShops: SellerShopLookup,
    private val visibility: CategoryVisibility = CategoryVisibility(),
    private val productCards: PublicProductMapper = PublicProductMapper(),
) {
    fun listCategories(query: CategoryListingQuery): List<CategoryNode> {
        val visible = visibility.visible(categories.findAll())
        val scope = scope(query.parentId, visible).sortedWith(CATEGORY_ORDER)
        if (!query.tree) {
            return scope.map { leaf(it) }
        }
        return tree(scope, query.parentId)
    }

    fun listProducts(): List<PublicProductCard> {
        val categoriesById = visibility.index(categories.findAll())
        return products.findAll()
            .filter { product -> isOnStorefront(product, categoriesById) }
            .sortedWith(PRODUCT_ORDER)
            .map { product -> toCard(product, categoriesById.getValue(product.categoryId)) }
    }

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

    private fun isOnStorefront(product: ProductModel, categoriesById: Map<UUID, CategoryModel>): Boolean {
        if (!product.isActive()) {
            return false
        }
        return visibility.isVisible(categoriesById[product.categoryId], categoriesById)
    }

    private fun toCard(product: ProductModel, category: CategoryModel): PublicProductCard {
        return productCards.toCard(product, category, sellerShops.findBySellerId(product.sellerId))
    }

    private companion object {
        val CATEGORY_ORDER = compareBy<CategoryModel> { it.sortOrder }.thenBy { it.name }.thenBy { it.id }
        val PRODUCT_ORDER = compareByDescending<ProductModel> { it.createdAt }.thenBy { it.id }
    }
}
