package catalog.web.controller

import catalog.domain.service.PublicCatalogService
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController


@RestController
@RequestMapping("api/v1/catalog")
class PublicCatalogController(
    private val publicCatalog: PublicCatalogService,
    ) {
}