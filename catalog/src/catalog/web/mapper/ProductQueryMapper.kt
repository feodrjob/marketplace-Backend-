package catalog.web.mapper

import catalog.domain.model.ProductFilterClause
import catalog.domain.model.ProductListingQuery
import catalog.exception.InvalidCatalogQueryException
import org.springframework.stereotype.Component
import org.springframework.util.MultiValueMap

/*
* Преобразует HTTP параметры поиска в проверенный запрос каталога
* */
@Component
class ProductQueryMapper {
    fun toQuery(parameters: MultiValueMap<String, String>): ProductListingQuery {
        val filters = mutableListOf<ProductFilterClause>()
        for ((name, values) in parameters) {
            if (values.size != 1) {
                throw InvalidCatalogQueryException("Query parameter must occur exactly once: $name")
            }
            if (name in QUERY_PARAMETERS) continue
            val match = FILTER_PATTERN.matchEntire(name)
                ?: throw InvalidCatalogQueryException("Unknown query parameter: $name")
            filters.add(
                ProductFilterClause(
                    field = match.groupValues[1],
                    value = values.single(),
                    negated = match.groupValues[2] == "!",
                ),
            )
        }
        val query = ProductListingQuery.parse(
            text = parameters.getFirst("q"),
            filters = filters,
            sort = parameters.getFirst("sort"),
            page = parameters.getFirst("page"),
            size = parameters.getFirst("size"),
        )
        if (query.size > MAX_SIZE) {
            throw InvalidCatalogQueryException("Page size must not exceed $MAX_SIZE")
        }
        if ((query.page.toLong() + 1) * query.size > MAX_RESULT_WINDOW) {
            throw InvalidCatalogQueryException("Requested page exceeds the search result window of $MAX_RESULT_WINDOW")
        }
        return query
    }

    private companion object {
        const val MAX_SIZE = 100
        const val MAX_RESULT_WINDOW = 10_000
        val QUERY_PARAMETERS = setOf("q", "sort", "page", "size")
        val FILTER_PATTERN = Regex("""^f\[([A-Za-z][A-Za-z0-9]*)\](!?)$""")
    }
}
