package catalog.domain.support

import catalog.domain.model.SellerShop
import catalog.domain.repository.SellerShopLookup
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class InMemorySellerShopLookup : SellerShopLookup {
    private val shops = ConcurrentHashMap<UUID, SellerShop>()

    fun save(shop: SellerShop) {
        shops[shop.sellerId] = shop
    }

    override fun findBySellerId(sellerId: UUID): SellerShop? = shops[sellerId]
}
