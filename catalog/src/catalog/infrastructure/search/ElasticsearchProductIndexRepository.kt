package catalog.infrastructure.search

import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * Инфраструктурный репозиторий записи и управления индексом товаров в Elasticsearch.
 */
@Repository
class ElasticsearchProductIndexRepository(
    private val elasticsearchOperations: ElasticsearchOperations,
) {
    private val indexCoordinates = IndexCoordinates.of(ProductDocument.INDEX_NAME)

    fun ensureIndexExists() {
        val indexOps = elasticsearchOperations.indexOps(ProductDocument::class.java)
        if (!indexOps.exists()) {
            indexOps.create()
            indexOps.putMapping(indexOps.createMapping())
        }
    }

    fun save(document: ProductDocument) {
        ensureIndexExists()
        val query = IndexQueryBuilder()
            .withId(document.id)
            .withObject(document)
            .build()
        elasticsearchOperations.index(query, indexCoordinates)
        elasticsearchOperations.indexOps(ProductDocument::class.java).refresh()
    }

    fun saveAll(documents: List<ProductDocument>) {
        ensureIndexExists()
        if (documents.isEmpty()) return
        val queries = documents.map { doc ->
            IndexQueryBuilder()
                .withId(doc.id)
                .withObject(doc)
                .build()
        }
        elasticsearchOperations.bulkIndex(queries, indexCoordinates)
        elasticsearchOperations.indexOps(ProductDocument::class.java).refresh()
    }

    fun deleteById(productId: UUID) {
        val indexOps = elasticsearchOperations.indexOps(ProductDocument::class.java)
        if (!indexOps.exists()) return
        elasticsearchOperations.delete(productId.toString(), indexCoordinates)
        indexOps.refresh()
    }
}
