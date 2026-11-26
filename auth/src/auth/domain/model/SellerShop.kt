package auth.domain.model

import java.util.UUID

/*
* Хранит публичные данные магазина без реквизитов аккаунта
* */
data class SellerShop(
    val sellerId: UUID,
    val companyName: String,
    val description: String?,
)
