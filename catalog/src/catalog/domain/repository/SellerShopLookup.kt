package catalog.domain.repository

import catalog.domain.model.SellerShop
import java.util.UUID

interface SellerShopLookup {
    fun findBySellerId(sellerId: UUID): SellerShop?
}
