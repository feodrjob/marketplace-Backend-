package catalog

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class CatalogApplication {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<CatalogApplication>(*args)
        }
    }
}