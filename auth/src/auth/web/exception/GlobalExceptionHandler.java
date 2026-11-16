package auth.web.exception;

import auth.exception.EmailAlreadyUsedException;
import auth.exception.FieldValidationException;
import auth.exception.InvalidCredentialsException;
import auth.exception.InvalidTokenException;
import auth.exception.UserNotFoundException;
import auth.web.dto.error.ProblemResponse;
import auth.web.dto.error.ValidationProblemResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final MediaType PROBLEM_JSON = MediaType.parseMediaType("application/problem+json");
    private static final String MALFORMED_JSON = "Failed to read request JSON";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationProblemResponse> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        Map<String, String> details = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            details.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return unprocessable(details, request);
    }

    @ExceptionHandler(FieldValidationException.class)
    public ResponseEntity<ValidationProblemResponse> handleFieldValidation(
            FieldValidationException exception,
            HttpServletRequest request
    ) {
        return unprocessable(exception.getDetails(), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemResponse> handleMalformedJson(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        return problem(HttpStatus.BAD_REQUEST, "Bad Request", MALFORMED_JSON, request);
    }

    @ExceptionHandler(EmailAlreadyUsedException.class)
    public ResponseEntity<ProblemResponse> handleEmailAlreadyUsed(
            EmailAlreadyUsedException exception,
            HttpServletRequest request
    ) {
        return problem(HttpStatus.CONFLICT, "Conflict", exception.getMessage(), request);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ProblemResponse> handleInvalidCredentials(
            InvalidCredentialsException exception,
            HttpServletRequest request
    ) {
        return problem(HttpStatus.UNAUTHORIZED, "Unauthorized", exception.getMessage(), request);
    }

    @ExceptionHandler({InvalidTokenException.class, UserNotFoundException.class})
    public ResponseEntity<ProblemResponse> handleInvalidToken(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        return problem(HttpStatus.UNAUTHORIZED, "Unauthorized", exception.getMessage(), request);
    }

    private ResponseEntity<ValidationProblemResponse> unprocessable(
            Map<String, String> details,
            HttpServletRequest request
    ) {
        ValidationProblemResponse body = ValidationProblemResponse.builder()
                .type("ValidationError")
                .title("Unprocessable Entity")
                .status(HttpStatus.UNPROCESSABLE_CONTENT.value())
                .details(details)
                .instance(request.getRequestURI())
                .timestamp(Instant.now())
                .build();
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_CONTENT)
                .contentType(PROBLEM_JSON)
                .body(body);
    }

    private ResponseEntity<ProblemResponse> problem(
            HttpStatus status,
            String title,
            String detail,
            HttpServletRequest request
    ) {
        ProblemResponse body = ProblemResponse.builder()
                .type("about:blank")
                .title(title)
                .status(status.value())
                .detail(detail)
                .instance(request.getRequestURI())
                .timestamp(Instant.now())
                .build();
        return ResponseEntity
                .status(status)
                .contentType(PROBLEM_JSON)
                .body(body);
    }
}
