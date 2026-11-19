package catalog.infrastructure.persistence.entity

import catalog.common.enums.ProductStatus
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "products", schema = "catalog")
class ProductEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID? = null,

    @Column(name = "seller_id", nullable = false)
    var sellerId: UUID = UUID.randomUUID(),

    @Column(name = "category_id", nullable = false)
    var categoryId: UUID = UUID.randomUUID(),

    @Column(name = "title", nullable = false, length = 255)
    var title: String = "",

    @Column(name = "description", columnDefinition = "TEXT")
    var description: String? = null,

    @Column(name = "price", nullable = false)
    var price: Long = 0L,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    var status: ProductStatus = ProductStatus.ACTIVE
) {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", insertable = false, updatable = false)
    var category: CategoryEntity? = null
        set(value) {
            field = value
            if (value != null) {
                this.categoryId = value.id ?: this.categoryId
            }
        }

    @OneToMany(mappedBy = "product", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC")
    var images: MutableList<ProductImageEntity> = mutableListOf()

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null

    fun addImage(mediaId: UUID, sortOrder: Int) {
        val imageEntity = ProductImageEntity(
            id = ProductImageId(productId = this.id, mediaId = mediaId),
            product = this,
            sortOrder = sortOrder
        )
        this.images.add(imageEntity)
    }

    fun removeImage(mediaId: UUID) {
        this.images.removeIf { it.id?.mediaId == mediaId }
    }

    fun clearImages() {
        this.images.clear()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProductEntity) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0

    override fun toString(): String =
        "ProductEntity(id=$id, sellerId=$sellerId, categoryId=$categoryId, title='$title', price=$price, status=$status)"
}
