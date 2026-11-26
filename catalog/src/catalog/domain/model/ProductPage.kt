package catalog.domain.model

/**
 * Страница публичных карточек.
 *
 * <p>
 * [total] считает все товары, прошедшие поиск и фильтры,
 * а не только строки текущей страницы.
 * </p>
 *
 * @param items карточки текущей страницы
 * @param page номер страницы с нуля
 * @param size запрошенный размер страницы
 * @param total число подходящих товаров
 * @since 0.0.1
 * @author lllnightlll
 * @see PublicProductCard
 */
class ProductPage(
    items: List<PublicProductCard>,
    val page: Int,
    val size: Int,
    val total: Int,
) {
    val items: List<PublicProductCard> = items.toList()
}
