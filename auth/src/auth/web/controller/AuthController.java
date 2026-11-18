package auth.web.controller;

import auth.domain.model.AuthSession;
import auth.domain.model.LogoutCommand;
import auth.domain.model.RefreshSessionCommand;
import auth.domain.service.AuthenticationService;
import auth.exception.InvalidTokenException;
import auth.web.dto.auth.AccessTokenResponse;
import auth.web.dto.auth.AuthResponse;
import auth.web.dto.auth.LoginRequest;
import auth.web.dto.auth.RegisterBuyerRequest;
import auth.web.dto.auth.RegisterSellerRequest;
import auth.web.mapper.AuthDtoMapper;
import auth.web.security.CookieHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Регистрация и управление сессией")
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthenticationService authenticationService;
    private final AuthDtoMapper mapper;
    private final CookieHelper cookieHelper;

    @PostMapping("/register/buyer")
    @Operation(summary = "Зарегистрировать покупателя", description = "Публичная операция. Создаёт аккаунт BUYER и открывает сессию.")
    @ApiResponse(responseCode = "201", description = "Аккаунт покупателя создан.")
    @ApiResponse(responseCode = "400", description = "Некорректный JSON.")
    @ApiResponse(responseCode = "409", description = "Email уже занят.")
    @ApiResponse(responseCode = "422", description = "Ошибки валидации полей.")
    public ResponseEntity<AuthResponse> registerBuyer(
            @Valid @RequestBody RegisterBuyerRequest request
    ) {
        AuthSession session = authenticationService.registerBuyer(mapper.toCommand(request));
        ResponseCookie cookie = cookieHelper.createRefreshCookie(session.getRefreshToken());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(mapper.toResponse(session));
    }

    @PostMapping("/register/seller")
    @Operation(summary = "Зарегистрировать продавца", description = "Публичная операция. Создаёт активный аккаунт SELLER и открывает сессию.")
    @ApiResponse(responseCode = "201", description = "Аккаунт продавца создан и активен.")
    @ApiResponse(responseCode = "400", description = "Некорректный JSON.")
    @ApiResponse(responseCode = "409", description = "Email уже занят.")
    @ApiResponse(responseCode = "422", description = "Ошибки валидации полей.")
    public ResponseEntity<AuthResponse> registerSeller(
            @Valid @RequestBody RegisterSellerRequest request
    ) {
        AuthSession session = authenticationService.registerSeller(mapper.toCommand(request));
        ResponseCookie cookie = cookieHelper.createRefreshCookie(session.getRefreshToken());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(mapper.toResponse(session));
    }

    @PostMapping("/login")
    @Operation(summary = "Войти по email и паролю", description = "Публичная операция для аккаунтов BUYER, SELLER и ADMIN.")
    @ApiResponse(responseCode = "200", description = "Сессия открыта.")
    @ApiResponse(responseCode = "400", description = "Некорректный JSON.")
    @ApiResponse(responseCode = "401", description = "Неизвестный email или неверный пароль.")
    @ApiResponse(responseCode = "422", description = "Ошибки валидации полей.")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        AuthSession session = authenticationService.signIn(mapper.toCommand(request));
        ResponseCookie cookie = cookieHelper.createRefreshCookie(session.getRefreshToken());
        return ResponseEntity
                .ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(mapper.toResponse(session));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Обновить сессию по refresh-cookie", description = "Читает cookie refreshToken и выдаёт новую пару токенов.")
    @ApiResponse(responseCode = "200", description = "Выданы новый access JWT и новая refresh-cookie.")
    @ApiResponse(responseCode = "401", description = "Cookie отсутствует, токен недействителен, истёк или аккаунт не найден.")
    public ResponseEntity<AccessTokenResponse> refresh(
            @CookieValue(name = CookieHelper.REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshToken
    ) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidTokenException("Session is invalid or expired");
        }
        AuthSession session = authenticationService.refresh(new RefreshSessionCommand(refreshToken));
        ResponseCookie cookie = cookieHelper.createRefreshCookie(session.getRefreshToken());
        return ResponseEntity
                .ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new AccessTokenResponse(session.getAccessToken()));
    }

    @PostMapping("/logout")
    @Operation(summary = "Выйти из сессии", description = "Проверяет access JWT и сбрасывает refresh-cookie.")
    @ApiResponse(responseCode = "204", description = "Команда удалить refresh-cookie отправлена.")
    @ApiResponse(responseCode = "401", description = "Access JWT отсутствует или недействителен.")
    public ResponseEntity<Void> logout(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authHeader
    ) {
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            throw new InvalidTokenException("Active access token required");
        }
        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        authenticationService.logout(new LogoutCommand(token));
        ResponseCookie clearCookie = cookieHelper.clearRefreshCookie();
        return ResponseEntity
                .noContent()
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .build();
    }
}
