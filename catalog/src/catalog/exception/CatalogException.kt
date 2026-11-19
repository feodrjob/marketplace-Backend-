package catalog.exception

/**
 * Базовое бизнес-исключение каталога.
 *
 * <p>
 * Текст [message] уходит клиенту без дополнительного перевода.
 * Скрытый товар и неактивная категория для витрины выглядят как отсутствие объекта.
 * </p>
 *
 * @param message текст для поля detail или details
 * @since 0.0.1
 * @author lllnightlll
 * @see ProductNotFoundException
 * @see CategoryNotFoundException
 */
abstract class CatalogException(message: String) : RuntimeException(message)