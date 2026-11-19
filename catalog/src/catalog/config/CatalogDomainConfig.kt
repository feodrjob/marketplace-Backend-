package catalog.config

import catalog.domain.repository.CategoryRepository
import catalog.domain.repository.ProductRepository
import catalog.domain.repository.SellerShopLookup
import catalog.domain.service.PublicCatalogService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Связывает компоненты публичного каталога.
 */
@Configuration
class CatalogDomainConfig {

    // Создаёт сервис публичного каталога.
    @Bean
    fun publicCatalogService(
        categories: CategoryRepository,
        products: ProductRepository,
        sellerShops: SellerShopLookup,
    ): PublicCatalogService {
        return PublicCatalogService(
            categories = categories,
            products = products,
            sellerShops = sellerShops,
        )
    }
}