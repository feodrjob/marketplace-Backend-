package catalog.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.util.UUID

/**
 * Товар в публичном ответе API.
 */
@Schema(description = "Карточка доступного товара", requiredProperties = ["id", "title", "description", "price", "categoryId", "categoryName", "categorySlug", "images", "sellerId", "companyName", "sellerDescription", "createdAt", "updatedAt"])
data class ProductResponse(
    @field:Schema(description = "Идентификатор товара", example = "00000000-0000-0000-0000-000000000101")
    val id: UUID,
    @field:Schema(description = "Название товара", example = "Новая книга")
    val title: String,
    @field:Schema(description = "Описание товара или null", example = "Книга о животных", types = ["string", "null"])
    val description: String?,
    @field:Schema(description = "Цена в целых бонусах", example = "1500", minimum = "1")
    val price: Long,
    @field:Schema(description = "Идентификатор категории", example = "00000000-0000-0000-0000-000000000001")
    val categoryId: UUID,
    @field:Schema(description = "Название категории", example = "Книги")
    val categoryName: String,
    @field:Schema(description = "Slug категории", example = "books")
    val categorySlug: String,
    @field:Schema(description = "Изображения в порядке показа, пустой массив если изображений нет")
    val images: List<ProductImageResponse>,
    @field:Schema(description = "Идентификатор аккаунта продавца, совпадает с sub его JWT", example = "00000000-0000-0000-0000-000000000010")
    val sellerId: UUID,
    @field:Schema(description = "Название магазина или null, если Auth не нашёл продавца", example = "Книжный магазин", types = ["string", "null"])
    val companyName: String?,
    @field:Schema(description = "Описание магазина или null", example = "Магазин книг", types = ["string", "null"])
    val sellerDescription: String?,
    @field:Schema(description = "Время создания в формате ISO 8601", example = "2026-11-27T00:00:00Z")
    val createdAt: Instant,
    @field:Schema(description = "Время последнего изменения в формате ISO 8601", example = "2026-11-27T01:00:00Z")
    val updatedAt: Instant,
)