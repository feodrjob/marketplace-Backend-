package catalog.domain.model

import catalog.exception.InvalidCatalogQueryException
import java.util.UUID

/**
 * Условие одной скобки `f[поле]`.
 *
 * @param field имя поля без скобок
 * @param value сырое значение до разбора
 * @param negated `true` для формы `f[поле]!=`
 * @since 0.0.1
 * @author lllnightlll
 * @see ProductListingQuery
 */
class ProductFilterClause(
    val field: String,
    val value: String,
    val negated: Boolean,
)

/**
 * Явный порядок списка.
 *
 * @param field `price` или `createdAt`
 * @param ascending `true` для `asc`
 * @since 0.0.1
 * @author lllnightlll
 * @see ProductListingQuery
 */
class ProductSort(
    val field: String,
    val ascending: Boolean,
)

private class CollectedFilters(
    val priceMin: Long?,
    val priceMax: Long?,
    val sellerIds: Set<UUID>,
    val excludedSellerIds: Set<UUID>,
    val categorySlugs: Set<String>,
    val excludedCategorySlugs: Set<String>,
    val categoryIds: Set<UUID>,
    val excludedCategoryIds: Set<UUID>,
)

/**
 * Запрос публичного списка товаров US-2.2.
 *
 * <p>
 * Пустой текст не ищет. Явная сортировка заменяет порядок по дате.
 * Без сортировки и без текста список идёт от новых к старым.
 * С текстом и без сортировки порядок задаёт вес совпадения.
 * [unpaged] оставляет всю выдачу: так работает прежний список витрины.
 * </p>
 *
 * @param text слова поиска или `null`
 * @param priceMin нижняя граница цены включительно
 * @param priceMax верхняя граница цены включительно
 * @param sellerIds продавцы, которых нужно оставить
 * @param excludedSellerIds продавцы, которых нужно убрать
 * @param categorySlugs slug категорий, достаточно одного
 * @param excludedCategorySlugs slug категорий, которые нужно убрать
 * @param categoryIds идентификаторы категорий, достаточно одного
 * @param excludedCategoryIds идентификаторы категорий, которые нужно убрать
 * @param sort явный порядок или `null`
 * @param page номер страницы с нуля
 * @param size размер страницы
 * @param unpaged `true`, если страницу резать не нужно
 * @since 0.0.1
 * @author lllnightlll
 * @see ProductPage
 */
class ProductListingQuery(
    val text: String?,
    val priceMin: Long?,
    val priceMax: Long?,
    sellerIds: Set<UUID>,
    excludedSellerIds: Set<UUID>,
    categorySlugs: Set<String>,
    excludedCategorySlugs: Set<String>,
    categoryIds: Set<UUID>,
    excludedCategoryIds: Set<UUID>,
    val sort: ProductSort?,
    val page: Int,
    val size: Int,
    val unpaged: Boolean,
) {
    val sellerIds: Set<UUID> = sellerIds.toSet()
    val excludedSellerIds: Set<UUID> = excludedSellerIds.toSet()
    val categorySlugs: Set<String> = categorySlugs.toSet()
    val excludedCategorySlugs: Set<String> = excludedCategorySlugs.toSet()
    val categoryIds: Set<UUID> = categoryIds.toSet()
    val excludedCategoryIds: Set<UUID> = excludedCategoryIds.toSet()

    companion object {
        const val DEFAULT_PAGE = 0
        const val DEFAULT_SIZE = 20

        private val FIELDS = setOf("price", "sellerId", "category", "categoryId")
        private val SORT_FIELDS = setOf("price", "createdAt")
        private val SORT_DIRECTIONS = setOf("asc", "desc")

        /**
         * Вся витрина без страницы: прежний список US-2.1.
         * @return запрос без фильтров и без разрезания страницы
         */
        fun all(): ProductListingQuery {
            return ProductListingQuery(
                text = null,
                priceMin = null,
                priceMax = null,
                sellerIds = emptySet(),
                excludedSellerIds = emptySet(),
                categorySlugs = emptySet(),
                excludedCategorySlugs = emptySet(),
                categoryIds = emptySet(),
                excludedCategoryIds = emptySet(),
                sort = null,
                page = DEFAULT_PAGE,
                size = DEFAULT_SIZE,
                unpaged = true,
            )
        }

        /**
         * Разбирает параметры списка.
         *
         * @param text параметр `q`
         * @param filters скобки `f[поле]`
         * @param sort параметр `sort` в виде `поле,направление`
         * @param page параметр `page`, с нуля
         * @param size параметр `size`
         * @return проверенный запрос
         * @throws InvalidCatalogQueryException если поле, диапазон, сортировка или страница некорректны
         */
        fun parse(
            text: String? = null,
            filters: List<ProductFilterClause> = emptyList(),
            sort: String? = null,
            page: String? = null,
            size: String? = null,
        ): ProductListingQuery {
            val normalizedText = TextValues.trimToNull(text)
            if (normalizedText != null && CatalogTextScore.tokens(normalizedText).isEmpty()) {
                throw InvalidCatalogQueryException("Search text is invalid")
            }
            val collected = collect(filters)
            return ProductListingQuery(
                text = normalizedText,
                priceMin = collected.priceMin,
                priceMax = collected.priceMax,
                sellerIds = collected.sellerIds,
                excludedSellerIds = collected.excludedSellerIds,
                categorySlugs = collected.categorySlugs,
                excludedCategorySlugs = collected.excludedCategorySlugs,
                categoryIds = collected.categoryIds,
                excludedCategoryIds = collected.excludedCategoryIds,
                sort = parseSort(sort),
                page = parsePage(page),
                size = parseSize(size),
                unpaged = false,
            )
        }

        private fun collect(filters: List<ProductFilterClause>): CollectedFilters {
            val seen = mutableSetOf<String>()
            var priceMin: Long? = null
            var priceMax: Long? = null
            val sellerIds = linkedSetOf<UUID>()
            val excludedSellerIds = linkedSetOf<UUID>()
            val categorySlugs = linkedSetOf<String>()
            val excludedCategorySlugs = linkedSetOf<String>()
            val categoryIds = linkedSetOf<UUID>()
            val excludedCategoryIds = linkedSetOf<UUID>()
            for (filter in filters) {
                val field = filter.field.trim()
                if (field !in FIELDS) {
                    throw InvalidCatalogQueryException("Unknown filter field")
                }
                val polarity = if (filter.negated) "negated" else "match"
                if (!seen.add("$field:$polarity")) {
                    throw InvalidCatalogQueryException("Filter field is duplicated")
                }
                if (filter.negated && field == "price") {
                    throw InvalidCatalogQueryException("Filter negation is not supported for price")
                }
                val raw = TextValues.trimToNull(filter.value)
                    ?: throw InvalidCatalogQueryException("Filter value is invalid")
                when (field) {
                    "price" -> {
                        val range = parsePrice(raw)
                        priceMin = range.first
                        priceMax = range.second
                    }
                    "sellerId" -> {
                        val ids = parseUuids(raw)
                        if (filter.negated) excludedSellerIds.addAll(ids) else sellerIds.addAll(ids)
                    }
                    "category" -> {
                        val slugs = parseTokens(raw)
                        if (filter.negated) excludedCategorySlugs.addAll(slugs) else categorySlugs.addAll(slugs)
                    }
                    "categoryId" -> {
                        val ids = parseUuids(raw)
                        if (filter.negated) excludedCategoryIds.addAll(ids) else categoryIds.addAll(ids)
                    }
                }
            }
            return CollectedFilters(
                priceMin,
                priceMax,
                sellerIds,
                excludedSellerIds,
                categorySlugs,
                excludedCategorySlugs,
                categoryIds,
                excludedCategoryIds,
            )
        }

        private fun parsePrice(raw: String): Pair<Long, Long> {
            val parts = raw.split(',')
            if (parts.size != 2) {
                throw InvalidCatalogQueryException("Filter value is invalid")
            }
            val min = parts[0].trim().toLongOrNull()
            val max = parts[1].trim().toLongOrNull()
            if (min == null || max == null || min < 0 || max < 0) {
                throw InvalidCatalogQueryException("Filter value is invalid")
            }
            if (min > max) {
                throw InvalidCatalogQueryException("Price minimum is greater than maximum")
            }
            return min to max
        }

        private fun parseUuids(raw: String): Set<UUID> {
            val tokens = parseTokens(raw)
            val ids = linkedSetOf<UUID>()
            for (token in tokens) {
                try {
                    ids.add(UUID.fromString(token))
                } catch (exception: IllegalArgumentException) {
                    throw InvalidCatalogQueryException("Filter value is invalid")
                }
            }
            return ids
        }

        private fun parseTokens(raw: String): Set<String> {
            val tokens = raw.split(',').map { token -> token.trim() }
            if (tokens.isEmpty() || tokens.any { token -> token.isEmpty() }) {
                throw InvalidCatalogQueryException("Filter value is invalid")
            }
            return tokens.toCollection(linkedSetOf())
        }

        private fun parseSort(raw: String?): ProductSort? {
            val sort = TextValues.trimToNull(raw) ?: return null
            val parts = sort.split(',')
            if (parts.size != 2) {
                throw InvalidCatalogQueryException("Sort value is invalid")
            }
            val field = parts[0].trim()
            val direction = parts[1].trim()
            if (field !in SORT_FIELDS || direction !in SORT_DIRECTIONS) {
                throw InvalidCatalogQueryException("Sort value is invalid")
            }
            return ProductSort(field, direction == "asc")
        }

        private fun parsePage(raw: String?): Int {
            val page = TextValues.trimToNull(raw) ?: return DEFAULT_PAGE
            val value = page.toIntOrNull() ?: throw InvalidCatalogQueryException("Page is invalid")
            if (value < 0) {
                throw InvalidCatalogQueryException("Page is invalid")
            }
            return value
        }

        private fun parseSize(raw: String?): Int {
            val size = TextValues.trimToNull(raw) ?: return DEFAULT_SIZE
            val value = size.toIntOrNull() ?: throw InvalidCatalogQueryException("Page size is invalid")
            if (value < 1) {
                throw InvalidCatalogQueryException("Page size is invalid")
            }
            return value
        }
    }
}