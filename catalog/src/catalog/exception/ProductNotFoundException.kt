package catalog.exception

/**
 * Товар нельзя показать на витрине.
 *
 * <p>
 * Ответ HTTP — 404. Так же завершается запрос скрытого, архивного
 * и лежащего в неактивной категории товара.
 * </p>
 *
 * @since 0.0.1
 * @author lllnightlll
 * @see CatalogException
 */
class ProductNotFoundException : CatalogException(MESSAGE) {
    companion object {
        const val MESSAGE = "Product is not available"
    }
}