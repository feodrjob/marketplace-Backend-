package catalog.infrastructure.search

import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.Document
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType
import java.time.Instant

/**
 * Индексный документ товара в Elasticsearch для витрины US-2.2.
 *
 * `createIndex = false`, чтобы тесты без поднятого Elasticsearch не падали на старте контекста;
 * создание индекса и маппинга контролируется через [ProductIndexSyncRepository].
 */
@Document(indexName = ProductDocument.INDEX_NAME, createIndex = false)
data class ProductDocument(
    @Id
    val id: String,

    @Field(type = FieldType.Keyword)
    val productId: String = id,

    @Field(type = FieldType.Keyword)
    val sellerId: String,

    @Field(type = FieldType.Keyword)
    val categoryId: String,

    @Field(type = FieldType.Keyword)
    val categorySlug: String,

    @Field(type = FieldType.Boolean)
    val categoryActive: Boolean,

    @Field(type = FieldType.Keyword)
    val status: String,

    @Field(type = FieldType.Text, analyzer = "russian", searchAnalyzer = "russian")
    val title: String,

    @Field(type = FieldType.Text, analyzer = "russian", searchAnalyzer = "russian")
    val description: String? = null,

    @Field(type = FieldType.Long)
    val price: Long,

    @Field(type = FieldType.Date)
    val createdAt: Instant,
) {
    companion object {
        const val INDEX_NAME = "products"
    }
}
