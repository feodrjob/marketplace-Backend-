package auth.web.exception;

import auth.exception.EmailAlreadyUsedException;
import auth.exception.InvalidCredentialsException;
import auth.exception.InvalidInnException;
import auth.exception.InvalidTokenException;
import auth.exception.ProfileUpdateNotAllowedException;
import auth.exception.UserNotFoundException;
import auth.exception.ValidationException;
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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationProblemResponse> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        Map<String, String> details = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            details.putIfAbsent(error.getField(), error.getDefaultMessage());
        }

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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemResponse> handleMalformedJson(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        ProblemResponse body = ProblemResponse.builder()
                .type("about:blank")
                .title("Bad Request")
                .status(HttpStatus.BAD_REQUEST.value())
                .detail("Failed to read request JSON")
                .instance(request.getRequestURI())
                .timestamp(Instant.now())
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(PROBLEM_JSON)
                .body(body);
    }

    @ExceptionHandler(EmailAlreadyUsedException.class)
    public ResponseEntity<ProblemResponse> handleEmailAlreadyUsed(
            EmailAlreadyUsedException exception,
            HttpServletRequest request
    ) {
        ProblemResponse body = ProblemResponse.builder()
                .type("about:blank")
                .title("Conflict")
                .status(HttpStatus.CONFLICT.value())
                .detail("User with this email is already registered")
                .instance(request.getRequestURI())
                .timestamp(Instant.now())
                .build();

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .contentType(PROBLEM_JSON)
                .body(body);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ProblemResponse> handleInvalidCredentials(
            InvalidCredentialsException exception,
            HttpServletRequest request
    ) {
        ProblemResponse body = ProblemResponse.builder()
                .type("about:blank")
                .title("Unauthorized")
                .status(HttpStatus.UNAUTHORIZED.value())
                .detail("Invalid email or password")
                .instance(request.getRequestURI())
                .timestamp(Instant.now())
                .build();

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .contentType(PROBLEM_JSON)
                .body(body);
    }

    @ExceptionHandler({InvalidTokenException.class, UserNotFoundException.class})
    public ResponseEntity<ProblemResponse> handleInvalidToken(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        String msg = exception.getMessage();
        String detail = "Session is invalid or expired";
        if (msg != null && (msg.contains("Требуется") || msg.contains("Active") || msg.contains("отсутствует"))) {
            detail = "Active access token required";
        }

        ProblemResponse body = ProblemResponse.builder()
                .type("about:blank")
                .title("Unauthorized")
                .status(HttpStatus.UNAUTHORIZED.value())
                .detail(detail)
                .instance(request.getRequestURI())
                .timestamp(Instant.now())
                .build();

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .contentType(PROBLEM_JSON)
                .body(body);
    }

    @ExceptionHandler(InvalidInnException.class)
    public ResponseEntity<ValidationProblemResponse> handleInvalidInn(
            InvalidInnException exception,
            HttpServletRequest request
    ) {
        ValidationProblemResponse body = ValidationProblemResponse.builder()
                .type("ValidationError")
                .title("Unprocessable Entity")
                .status(HttpStatus.UNPROCESSABLE_CONTENT.value())
                .details(Map.of("inn", "INN must consist of 10 or 12 digits"))
                .instance(request.getRequestURI())
                .timestamp(Instant.now())
                .build();

        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_CONTENT)
                .contentType(PROBLEM_JSON)
                .body(body);
    }

    @ExceptionHandler(ProfileUpdateNotAllowedException.class)
    public ResponseEntity<ValidationProblemResponse> handleProfileUpdateNotAllowed(
            ProfileUpdateNotAllowedException exception,
            HttpServletRequest request
    ) {
        String rawMessage = exception.getMessage() != null ? exception.getMessage() : "";
        boolean isInn = rawMessage.contains("ИНН") || rawMessage.toLowerCase().contains("inn");
        String field = isInn ? "inn" : "profile";
        String message = isInn ? "INN cannot be changed after registration" : "Profile update is not allowed for this role";

        ValidationProblemResponse body = ValidationProblemResponse.builder()
                .type("ValidationError")
                .title("Unprocessable Entity")
                .status(HttpStatus.UNPROCESSABLE_CONTENT.value())
                .details(Map.of(field, message))
                .instance(request.getRequestURI())
                .timestamp(Instant.now())
                .build();

        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_CONTENT)
                .contentType(PROBLEM_JSON)
                .body(body);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ValidationProblemResponse> handleValidationException(
            ValidationException exception,
            HttpServletRequest request
    ) {
        String message = exception.getMessage() != null ? exception.getMessage() : "";
        String field = "field";
        String detailMessage = "Validation error";

        if (message.contains("компании") || message.toLowerCase().contains("company")) {
            field = "companyName";
            detailMessage = "Company name is required";
        } else if (message.contains("ИНН") || message.toLowerCase().contains("inn")) {
            field = "inn";
            detailMessage = "INN is required";
        } else if (message.contains("Email") || message.toLowerCase().contains("email")) {
            field = "email";
            detailMessage = "Email is required";
        } else if (message.contains("Пароль") || message.toLowerCase().contains("password")) {
            field = "password";
            detailMessage = "Password is required";
        }

        ValidationProblemResponse body = ValidationProblemResponse.builder()
                .type("ValidationError")
                .title("Unprocessable Entity")
                .status(HttpStatus.UNPROCESSABLE_CONTENT.value())
                .details(Map.of(field, detailMessage))
                .instance(request.getRequestURI())
                .timestamp(Instant.now())
                .build();

        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_CONTENT)
                .contentType(PROBLEM_JSON)
                .body(body);
    }
}
