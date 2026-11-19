package catalog

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Точка входа сервиса каталога.
 *
 * <p>
 * Поднимает Spring Boot на порту 8083 и сканирует пакет [catalog].
 * Публичная витрина US-2.1 не требует JWT.
 * </p>
 *
 * @since 0.0.1
 * @author lllnightlll
 * @see catalog.domain.service.PublicCatalogService
 */
@SpringBootApplication
class CatalogApplication {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<CatalogApplication>(*args)
        }
    }
}