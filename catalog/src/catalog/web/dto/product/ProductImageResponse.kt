package catalog.web.dto.product

import java.util.UUID

/*
* Описывает изображение и порядок его показа в карточке товара
* */
data class ProductImageResponse(
    val mediaId: UUID,
    val sortOrder: Int,
)
