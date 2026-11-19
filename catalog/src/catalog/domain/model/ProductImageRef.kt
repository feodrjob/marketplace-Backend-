package catalog.domain.model

import java.util.UUID

class ProductImageRef(
    val productId: UUID,
    val mediaId: UUID,
    val sortOrder: Int,
)
