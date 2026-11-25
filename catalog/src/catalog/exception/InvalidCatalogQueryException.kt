package catalog.exception

/**
 * Параметры поиска, фильтра, сортировки или страницы нельзя применить.
 *
 * <p>
 * Ответ HTTP — 400. Неизвестное поле, перевёрнутый диапазон цены и некорректная страница не отбрасываются молча.
 * </p>
 *
 * @param message текст для поля detail
 * @since 0.0.1
 * @author lllnightlll
 * @see CatalogException
 */
class InvalidCatalogQueryException(message: String) : CatalogException(message)
