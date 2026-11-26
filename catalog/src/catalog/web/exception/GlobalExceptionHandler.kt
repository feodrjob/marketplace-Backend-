package catalog.web.exception

import catalog.exception.CategoryNotFoundException
import catalog.exception.InvalidCatalogQueryException
import catalog.exception.ProductNotFoundException
import catalog.web.dto.error.ProblemResponse
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.client.RestClientException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import java.time.Instant

/*
* Преобразует ошибки каталога в HTTP ответы с единым форматом
* */
@RestControllerAdvice
class GlobalExceptionHandler {
    private val logger = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(InvalidCatalogQueryException::class, MethodArgumentTypeMismatchException::class)
    fun handleBadRequest(exception: Exception, request: HttpServletRequest): ResponseEntity<ProblemResponse> {
        val detail = if (exception is InvalidCatalogQueryException) {
            exception.message ?: "Invalid catalog query"
        } else {
            "Invalid path or query parameter"
        }
        return problem(HttpStatus.BAD_REQUEST, detail, request)
    }

    @ExceptionHandler(ProductNotFoundException::class, CategoryNotFoundException::class)
    fun handleNotFound(exception: RuntimeException, request: HttpServletRequest): ResponseEntity<ProblemResponse> {
        return problem(HttpStatus.NOT_FOUND, exception.message ?: "Resource not found", request)
    }

    @ExceptionHandler(DataAccessException::class, RestClientException::class)
    fun handleUnavailable(exception: Exception, request: HttpServletRequest): ResponseEntity<ProblemResponse> {
        logger.error("Catalog dependency is unavailable", exception)
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Catalog dependency is temporarily unavailable", request)
    }

    private fun problem(status: HttpStatus, detail: String, request: HttpServletRequest): ResponseEntity<ProblemResponse> {
        val body = ProblemResponse(
            type = "about:blank",
            title = status.reasonPhrase,
            status = status.value(),
            detail = detail,
            instance = request.requestURI,
            timestamp = Instant.now(),
        )
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body)
    }
}
