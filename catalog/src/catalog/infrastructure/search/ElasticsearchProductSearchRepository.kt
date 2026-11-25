package catalog.infrastructure.search

import catalog.common.enums.ProductStatus
import catalog.domain.model.ProductListingQuery
import co.elastic.clients.elasticsearch._types.FieldValue
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery
import co.elastic.clients.elasticsearch._types.query_dsl.Query
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType
import co.elastic.clients.elasticsearch._types.query_dsl.ZeroTermsQuery
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.elasticsearch.client.elc.NativeQuery
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * Результат поисковой выборки из Elasticsearch.
 */
data class ElasticsearchSearchResult(
    val productIds: List<UUID>,
    val total: Int,
)

/**
 * Инфраструктурный репозиторий поиска, фильтрации, сортировки и пагинации товаров в Elasticsearch (US-2.2).
 */
@Repository
class ElasticsearchProductSearchRepository(
    private val elasticsearchOperations: ElasticsearchOperations,
) {

    fun search(query: ProductListingQuery): ElasticsearchSearchResult {
        val esQuery = buildBoolQuery(query)
        val pageable = if (query.unpaged) {
            PageRequest.of(0, MAX_UNPAGED_SIZE)
        } else {
            PageRequest.of(query.page, query.size)
        }

        val nativeQuery = NativeQuery.builder()
            .withQuery(esQuery)
            .withPageable(pageable)
            .withSort(buildSort(query))
            .withTrackTotalHits(true)
            .build()

        val hits = elasticsearchOperations.search(nativeQuery, ProductDocument::class.java)
        val ids = hits.searchHits.map { UUID.fromString(it.content.id) }
        return ElasticsearchSearchResult(
            productIds = ids,
            total = hits.totalHits.toInt(),
        )
    }

    private fun buildBoolQuery(query: ProductListingQuery): Query {
        val bool = BoolQuery.Builder()

        // Витрина показывает только ACTIVE товары в активной ветке категорий
        bool.filter(termQuery("status", ProductStatus.ACTIVE.name))
        bool.filter(Query.of { q -> q.term { t -> t.field("categoryActive").value(true) } })

        // Полнотекстовый поиск: каждое слово обязательно, title весит больше description, опечатки через fuzziness
        val text = query.text
        if (!text.isNullOrBlank()) {
            val words = text.trim().split(WHITESPACE_REGEX).filter { it.isNotBlank() }
            for (word in words) {
                bool.must(
                    Query.of { q ->
                        q.multiMatch { m ->
                            m.query(word)
                                .fields("title^3", "description")
                                .type(TextQueryType.BestFields)
                                .fuzziness("AUTO")
                            if (words.size > 1) {
                                m.zeroTermsQuery(ZeroTermsQuery.All)
                            }
                            m
                        }
                    },
                )
            }
        }

        // Диапазон цены
        if (query.priceMin != null || query.priceMax != null) {
            bool.filter(
                Query.of { q ->
                    q.range { r ->
                        r.number { n ->
                            n.field("price")
                            query.priceMin?.let { n.gte(it.toDouble()) }
                            query.priceMax?.let { n.lte(it.toDouble()) }
                            n
                        }
                    }
                },
            )
        }

        // Позитивные фильтры
        if (query.sellerIds.isNotEmpty()) {
            bool.filter(termsQuery("sellerId", query.sellerIds.map { it.toString() }))
        }
        if (query.categorySlugs.isNotEmpty()) {
            bool.filter(termsQuery("categorySlug", query.categorySlugs))
        }
        if (query.categoryIds.isNotEmpty()) {
            bool.filter(termsQuery("categoryId", query.categoryIds.map { it.toString() }))
        }

        // Негативные фильтры f[field]!=
        if (query.excludedSellerIds.isNotEmpty()) {
            bool.mustNot(termsQuery("sellerId", query.excludedSellerIds.map { it.toString() }))
        }
        if (query.excludedCategorySlugs.isNotEmpty()) {
            bool.mustNot(termsQuery("categorySlug", query.excludedCategorySlugs))
        }
        if (query.excludedCategoryIds.isNotEmpty()) {
            bool.mustNot(termsQuery("categoryId", query.excludedCategoryIds.map { it.toString() }))
        }

        return Query.of { q -> q.bool(bool.build()) }
    }

    private fun buildSort(query: ProductListingQuery): Sort {
        val sort = query.sort
        if (sort == null) {
            return if (query.text != null) {
                Sort.by(
                    Sort.Order.desc("_score"),
                    Sort.Order.desc("createdAt"),
                    Sort.Order.asc("productId"),
                )
            } else {
                Sort.by(
                    Sort.Order.desc("createdAt"),
                    Sort.Order.asc("productId"),
                )
            }
        }
        val primary = if (sort.ascending) Sort.Order.asc(sort.field) else Sort.Order.desc(sort.field)
        return Sort.by(primary, Sort.Order.asc("productId"))
    }

    private fun termQuery(field: String, value: String): Query =
        Query.of { q -> q.term { t -> t.field(field).value(value) } }

    private fun termsQuery(field: String, values: Collection<String>): Query =
        Query.of { q ->
            q.terms { t ->
                t.field(field).terms { tv -> tv.value(values.map { FieldValue.of(it) }) }
            }
        }

    private companion object {
        const val MAX_UNPAGED_SIZE = 10_000
        val WHITESPACE_REGEX = Regex("\\s+")
    }
}
