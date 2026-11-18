package auth.domain.model;

import lombok.Getter;

import java.time.Duration;
import java.util.Arrays;

public final class TokenSettings {

    public static final Duration ACCESS_TTL = Duration.ofMinutes(15);
    public static final Duration REFRESH_TTL = Duration.ofDays(30);
    public static final String ISSUER = "marketplace_auth";

    public static final int HMAC_MIN_BYTES = 32;

    private final byte[] hmacSecret;
    @Getter
    private final Duration accessTtl;
    @Getter
    private final Duration refreshTtl;
    @Getter
    private final String issuer;

    public TokenSettings(byte[] hmacSecret, Duration accessTtl, Duration refreshTtl, String issuer) {
        if (hmacSecret == null || hmacSecret.length < HMAC_MIN_BYTES) {
            throw new IllegalArgumentException(
                    "JWT signing secret must be at least " + HMAC_MIN_BYTES + " bytes"
            );
        }
        if (accessTtl == null || accessTtl.isZero() || accessTtl.isNegative()) {
            throw new IllegalArgumentException("Access token lifetime must be positive");
        }
        if (refreshTtl == null || refreshTtl.compareTo(accessTtl) <= 0) {
            throw new IllegalArgumentException("Refresh token must live longer than the access token");
        }
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("Token issuer is required");
        }
        this.hmacSecret = Arrays.copyOf(hmacSecret, hmacSecret.length);
        this.accessTtl = accessTtl;
        this.refreshTtl = refreshTtl;
        this.issuer = issuer;
    }

    public static TokenSettings marketplaceDefaults(byte[] hmacSecret) {
        return new TokenSettings(hmacSecret, ACCESS_TTL, REFRESH_TTL, ISSUER);
    }

    public byte[] copySecret() {
        return Arrays.copyOf(hmacSecret, hmacSecret.length);
    }
}
