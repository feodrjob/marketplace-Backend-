package catalog.domain.support

import catalog.domain.model.SellerShop
import catalog.domain.repository.SellerShopLookup
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Память вместо чтения магазина из Auth.
 *
 * <p>Если продавца нет в карте, карточка открывается без названия магазина.</p>
 *
 * @since 0.0.1
 * @author lllnightlll
 * @see SellerShopLookup
 */
class InMemorySellerShopLookup : SellerShopLookup {
    private val shops = ConcurrentHashMap<UUID, SellerShop>()

    fun save(shop: SellerShop) {
        shops[shop.sellerId] = shop
    }

    override fun findBySellerId(sellerId: UUID): SellerShop? = shops[sellerId]
}
