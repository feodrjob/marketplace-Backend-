package catalog.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.servers.Server
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/*
* Настраивает генерируемый OpenAPI контракт публичного каталога
* */
@Configuration
class OpenApiConfig {
    @Bean
    fun catalogOpenAPI(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("Т-Маркет — Catalog Service")
                .version("1.0.0")
                .description(
                    "Контракт реализованной публичной витрины US-2.1 и US-2.2. " +
                        "Все операции доступны без JWT. Цена указана в целых бонусах. " +
                        "Поиск выполняется в Elasticsearch, карточки читаются из PostgreSQL. " +
                        "Устаревший индекс может содержать уже недоступные товары: они исключаются из карточек, " +
                        "но могут оставаться в total. Управление ассортиментом и загрузка изображений в этот контракт не входят.",
                ),
        )
        .servers(listOf(Server().url("/").description("Текущий origin, порт Catalog по умолчанию 8083")))
}
