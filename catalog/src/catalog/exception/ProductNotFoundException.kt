package catalog.exception

class ProductNotFoundException : CatalogException(MESSAGE) {
    companion object {
        const val MESSAGE = "Product is not available"
    }
}