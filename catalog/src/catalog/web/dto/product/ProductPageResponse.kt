package catalog.web.dto.product

/*
* Хранит страницу товаров и сведения о пагинации
* */
data class ProductPageResponse(
    val items: List<ProductResponse>,
    val page: Int,
    val size: Int,
    val total: Int,
)
