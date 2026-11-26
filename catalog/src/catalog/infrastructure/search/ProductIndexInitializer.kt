package catalog.infrastructure.search

import catalog.domain.repository.CategoryRepository
import catalog.domain.service.CategoryVisibility
import catalog.infrastructure.persistence.repository.ProductRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component

/*
* Загружает актуальные товары PostgreSQL в поисковый индекс при запуске каталога
* */
@Component
@ConditionalOnProperty(name = ["catalog.search.initialize-on-startup"], havingValue = "true")
class ProductIndexInitializer(
    private val products: ProductRepository,
    private val categories: CategoryRepository,
    private val index: ElasticsearchProductIndexRepository,
) {
    @EventListener(ApplicationReadyEvent::class)
    fun initialize() {
        val allCategories = categories.findAll()
        val categoriesById = allCategories.associateBy { it.id }
        val visibleIds = CategoryVisibility().visible(allCategories).map { it.id }.toSet()
        index.ensureIndexExists()
        var pageNumber = 0
        do {
            val page = products.findAll(PageRequest.of(pageNumber, 500))
            val documents = page.content.map { product ->
                val category = checkNotNull(categoriesById[product.categoryId])
                ProductDocument(
                    id = checkNotNull(product.id).toString(),
                    sellerId = product.sellerId.toString(),
                    categoryId = product.categoryId.toString(),
                    categorySlug = category.slug,
                    categoryActive = product.categoryId in visibleIds,
                    status = product.status.name,
                    title = product.title,
                    description = product.description,
                    price = product.price,
                    createdAt = checkNotNull(product.createdAt),
                )
            }
            index.saveAll(documents)
            pageNumber++
        } while (page.hasNext())
    }
}
