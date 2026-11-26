package catalog.web.exception

import catalog.exception.CatalogException
import catalog.exception.CategoryNotFoundException
import catalog.exception.ProductNotFoundException
import catalog.exception.SellerShopUnavailableException
import catalog.web.dto.error.ProblemResponse
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import java.time.Instant

/**
 * Преобразует ошибки каталога в HTTP.
 */
@RestControllerAdvice(basePackages = ["catalog.web.controller"])
class GlobalExceptionHandler {

    // Обрабатывает отсутствие публичного ресурса.
    @ExceptionHandler(
        ProductNotFoundException::class,
        CategoryNotFoundException::class,
    )
    fun handleNotFound(
        exception: CatalogException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemResponse> {
        return problem(
            status = HttpStatus.NOT_FOUND,
            detail = exception.message ?: "Ресурс каталога недоступен",
            request = request,
        )
    }

    // Обрабатывает неверный формат параметра.
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleInvalidParameter(
        exception: MethodArgumentTypeMismatchException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemResponse> {
        return problem(
            status = HttpStatus.BAD_REQUEST,
            detail = "Некорректное значение параметра '${exception.name}'",
            request = request,
        )
    }

    // Возвращаем 503 при недоступности Auth.
    @ExceptionHandler(SellerShopUnavailableException::class)
    fun handleShopUnavailable(
        exception: SellerShopUnavailableException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemResponse> {
        return problem(
            status = HttpStatus.SERVICE_UNAVAILABLE,
            detail = exception.message ?: "Данные магазина временно недоступны",
            request = request,
        )
    }

    // Формирует единый ответ ошибки.
    private fun problem(
        status: HttpStatus,
        detail: String,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemResponse> {
        val body = ProblemResponse(
            type = "about:blank",
            title = status.reasonPhrase,
            status = status.value(),
            detail = detail,
            instance = request.requestURI,
            timestamp = Instant.now(),
        )

        return ResponseEntity
            .status(status)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(body)
    }
}
