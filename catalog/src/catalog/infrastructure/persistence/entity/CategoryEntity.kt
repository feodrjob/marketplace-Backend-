package catalog.infrastructure.persistence.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "categories", schema = "catalog")
class CategoryEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID? = null,

    @Column(name = "parent_id")
    var parentId: UUID? = null,

    @Column(name = "name", nullable = false, length = 100)
    var name: String = "",

    @Column(name = "slug", nullable = false, unique = true, length = 100)
    var slug: String = "",

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true
) {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", insertable = false, updatable = false)
    var parent: CategoryEntity? = null
        set(value) {
            field = value
            this.parentId = value?.id
        }

    @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC, name ASC")
    var subcategories: MutableList<CategoryEntity> = mutableListOf()

    fun addSubcategory(child: CategoryEntity) {
        child.parent = this
        this.subcategories.add(child)
    }

    fun removeSubcategory(child: CategoryEntity) {
        this.subcategories.remove(child)
        child.parent = null
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CategoryEntity) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0

    override fun toString(): String =
        "CategoryEntity(id=$id, parentId=$parentId, name='$name', slug='$slug', sortOrder=$sortOrder, isActive=$isActive)"
}
