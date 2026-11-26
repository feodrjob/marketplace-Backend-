package auth.web.dto.seller

import java.util.UUID

/*
* Описывает публичный HTTP ответ магазина продавца
* */
data class SellerResponse(
    val sellerId: UUID,
    val companyName: String,
    val description: String?,
)
