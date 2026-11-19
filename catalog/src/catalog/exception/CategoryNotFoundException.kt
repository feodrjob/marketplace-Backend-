package catalog.exception

/**
 * Категорию нельзя использовать как родителя витрины.
 *
 * <p>
 * Ответ HTTP — 404. Неизвестный и неактивный родитель для гостя не различаются.
 * </p>
 *
 * @since 0.0.1
 * @author lllnightlll
 * @see CatalogException
 */
class CategoryNotFoundException : CatalogException(MESSAGE) {
    companion object {
        const val MESSAGE = "Category is not available"
    }
}