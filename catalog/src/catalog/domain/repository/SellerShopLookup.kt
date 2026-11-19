package catalog.domain.repository

import catalog.domain.model.SellerShop
import java.util.UUID

/**
 * Порт к аккаунту продавца.
 *
 * <p>
 * Catalog хранит только идентификатор продавца. Название магазина
 * для карточки читается здесь и не лежит в схеме catalog.
 * </p>
 *
 * @since 0.0.1
 * @author lllnightlll
 * @see catalog.domain.model.SellerShop
 */
interface SellerShopLookup {
    fun findBySellerId(sellerId: UUID): SellerShop?
}
