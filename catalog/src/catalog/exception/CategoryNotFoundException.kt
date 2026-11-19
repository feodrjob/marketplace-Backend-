package catalog.exception

class CategoryNotFoundException : CatalogException(MESSAGE) {
    companion object {
        const val MESSAGE = "Category is not available"
    }
}