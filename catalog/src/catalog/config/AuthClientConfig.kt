package catalog.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration

/**
 * Настраивает HTTP-соединение каталога с Auth.
 */
@Configuration
class AuthClientConfig {

    // Создаёт клиент с ограничением ожидания.
    @Bean
    fun authRestClient(
        @Value("\${catalog.auth.base-url}") baseUrl: String,
        @Value("\${catalog.auth.connect-timeout}") connectTimeout: Duration,
        @Value("\${catalog.auth.read-timeout}") readTimeout: Duration,
    ): RestClient {
        val httpClient = HttpClient.newBuilder()
            .connectTimeout(connectTimeout)
            .build()
        val requestFactory = JdkClientHttpRequestFactory(httpClient)
        requestFactory.setReadTimeout(readTimeout)

        return RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(requestFactory)
            .build()
    }
}
