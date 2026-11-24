package catalog.config

import catalog.domain.repository.CategoryRepository
import catalog.domain.repository.ProductRepository
import catalog.domain.repository.SellerShopLookup
import catalog.domain.service.PublicCatalogService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class CatalogDomainConfig {
    @Bean
    fun publicCatalogService(
        categoryRepository: CategoryRepository,
        productRepository: ProductRepository,
        sellerShopLookup: SellerShopLookup
    ): PublicCatalogService {
        return PublicCatalogService(
            categories  = categoryRepository,
            products = productRepository,
            sellerShops = sellerShopLookup,
        )
    }
}