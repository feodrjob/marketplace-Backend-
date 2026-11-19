package catalog.infrastructure.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.io.Serializable
import java.util.UUID

@Embeddable
data class ProductImageId(
    @Column(name = "product_id")
    var productId: UUID? = null,

    @Column(name = "media_id", nullable = false)
    var mediaId: UUID? = null
) : Serializable
