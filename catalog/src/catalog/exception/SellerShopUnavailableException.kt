package catalog.exception

/**
 * Сообщает о недоступности данных магазина.
 */
class SellerShopUnavailableException(cause: Throwable) : CatalogException(
    "Не удалось получить данные магазина"
) {
    init {
        initCause(cause)
    }
}
