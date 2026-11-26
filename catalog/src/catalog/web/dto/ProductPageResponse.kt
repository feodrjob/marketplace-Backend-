package catalog.web.dto

import io.swagger.v3.oas.annotations.media.Schema
/*
* Хранит страницу товаров и сведения о пагинации
* */
@Schema(description = "Страница найденных товаров", requiredProperties = ["items", "page", "size", "total"])
data class ProductPageResponse(
    @field:Schema(description = "Карточки текущей страницы, пустой массив если совпадений нет")
    val items: List<ProductResponse>,
    @field:Schema(description = "Номер страницы с нуля", example = "0", minimum = "0")
    val page: Int,
    @field:Schema(description = "Запрошенный размер страницы, может превышать фактическое число карточек", example = "20", minimum = "1", maximum = "100")
    val size: Int,
    @field:Schema(description = "Число совпадений в Elasticsearch до выделения страницы, при устаревшем индексе может включать недоступные товары", example = "42", minimum = "0")
    val total: Int,
)
