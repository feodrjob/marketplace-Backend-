package catalog.domain.model

import java.util.UUID

/**
 * Магазин продавца для карточки товара.
 *
 * <p>
 * Название и описание читаются из Auth через [catalog.domain.repository.SellerShopLookup].
 * </p>
 *
 * @param sellerId идентификатор аккаунта продавца
 * @param companyName название магазина, не длиннее [COMPANY_NAME_MAX_LENGTH]
 * @param description описание магазина, может отсутствовать
 * @since 0.0.1
 * @author lllnightlll
 * @see catalog.domain.model.PublicProductCard
 */
class SellerShop(
    val sellerId: UUID,
    companyName: String?,
    description: String?,
) {
    val companyName: String = requireCompanyName(companyName)
    val description: String? = TextValues.trimToNull(description)

    companion object {
        const val COMPANY_NAME_MAX_LENGTH = 255

        private fun requireCompanyName(companyName: String?): String {
            val normalized = TextValues.trimToNull(companyName)
                ?: throw IllegalArgumentException("Field companyName is required")
            if (normalized.length > COMPANY_NAME_MAX_LENGTH) {
                throw IllegalArgumentException("Field companyName is longer than $COMPANY_NAME_MAX_LENGTH")
            }
            return normalized
        }
    }
}
