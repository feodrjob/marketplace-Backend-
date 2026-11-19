package auth.web.dto.user

import java.util.UUID

/**
 * Публичные данные магазина для каталога.
 */
data class SellerShopResponse(
    val sellerId: UUID,
    val companyName: String,
    val description: String?,
)