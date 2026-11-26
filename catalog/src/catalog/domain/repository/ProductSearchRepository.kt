package catalog.domain.repository

import catalog.domain.model.ProductListingQuery
import catalog.domain.model.ProductSearchResult

/*
* Определяет поиск и пагинацию идентификаторов товаров
* */
fun interface ProductSearchRepository {
    fun search(query: ProductListingQuery): ProductSearchResult
}
