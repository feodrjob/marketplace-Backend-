package catalog.common.enums

/**
 * Жизненный цикл товара.
 *
 * <p>
 * На витрину попадает только [ACTIVE]. [HIDDEN] можно опубликовать снова,
 * [ARCHIVED] остаётся ради истории заказов и повторно не публикуется.
 * </p>
 *
 * @since 0.0.1
 * @author lllnightlll
 * @see catalog.domain.model.ProductModel
 */
enum class ProductStatus {
    /** Опубликован и доступен гостю. */
    ACTIVE,

    /** Временно снят с витрины. */
    HIDDEN,

    /** Сохранён для истории и больше не продаётся. */
    ARCHIVED,
}
