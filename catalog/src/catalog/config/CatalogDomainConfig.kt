package catalog.config

import catalog.domain.repository.CategoryRepository
import catalog.domain.repository.ProductRepository
import catalog.domain.repository.ProductSearchRepository
import catalog.domain.repository.SellerShopLookup
import catalog.domain.service.PublicCatalogService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/*
* Собирает доменные сервисы каталога из инфраструктурных зависимостей
* */
@Configuration
class CatalogDomainConfig {
    @Bean
    fun publicCatalogService(
        categoryRepository: CategoryRepository,
        productRepository: ProductRepository,
        sellerShopLookup: SellerShopLookup,
        productSearchRepository: ProductSearchRepository,
    ): PublicCatalogService = PublicCatalogService(
        categories = categoryRepository,
        products = productRepository,
        sellerShops = sellerShopLookup,
        search = productSearchRepository,
    )
}
