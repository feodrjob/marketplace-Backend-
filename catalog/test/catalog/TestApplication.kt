package catalog

import org.springframework.boot.autoconfigure.SpringBootApplication

/**
 * Настраивает контекст тестов репозиториев.
 */
// Ограничиваем сканирование: тесты БД не зависят от контроллеров.
@SpringBootApplication(
    scanBasePackages = ["catalog.infrastructure.persistence"]
)
class TestApplication