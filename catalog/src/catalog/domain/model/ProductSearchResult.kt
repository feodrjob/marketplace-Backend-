package catalog.domain.model

import java.util.UUID

/*
* Хранит идентификаторы и общее число найденных товаров
* */
data class ProductSearchResult(
    val productIds: List<UUID>,
    val total: Int,
)
