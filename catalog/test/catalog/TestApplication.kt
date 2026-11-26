package catalog

import org.springframework.boot.autoconfigure.SpringBootApplication

/**
 * Настраивает контекст тестов репозиториев.
 */
// Ограничиваем сканирование: тесты БД не зависят от контроллеров.
@SpringBootApplication(
    scanBasePackages = ["catalog.infrastructure.persistence", "catalog.infrastructure.search"]
)
class TestApplication