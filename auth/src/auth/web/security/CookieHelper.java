package auth.web.security;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class CookieHelper {

    public static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";
    public static final String AUTH_COOKIE_PATH = "/api/v1/auth";
    public static final Duration REFRESH_COOKIE_MAX_AGE = Duration.ofDays(30);

    public ResponseCookie createRefreshCookie(String refreshToken) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(AUTH_COOKIE_PATH)
                .maxAge(REFRESH_COOKIE_MAX_AGE)
                .build();
    }

    public ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(AUTH_COOKIE_PATH)
                .maxAge(0)
                .build();
    }
}
