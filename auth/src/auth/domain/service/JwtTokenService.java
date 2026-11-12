package auth.domain.service;

import auth.common.enums.Role;
import auth.domain.model.TokenClaims;
import auth.domain.model.TokenPair;
import auth.domain.model.TokenSettings;
import auth.domain.model.TokenUse;
import auth.domain.model.UserModel;
import auth.exception.InvalidTokenException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

// JWT с HMAC. Access и refresh отличаются claim и сроком жизни
public final class JwtTokenService implements TokenService {

    static final String CLAIM_ROLE = "role";
    static final String CLAIM_EMAIL = "email";
    static final String CLAIM_USE = "token_use";

    private final TokenSettings settings;
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final SecretKey key;
    private final io.jsonwebtoken.JwtParser parser;

    public JwtTokenService(TokenSettings settings, Clock clock, IdGenerator idGenerator) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
        this.key = io.jsonwebtoken.security.Keys.hmacShaKeyFor(settings.copySecret());
        this.parser = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(settings.getIssuer())
                .clock(() -> Date.from(clock.instant()))
                .build();
    }

    @Override
    public TokenPair issue(UserModel user) {
        Objects.requireNonNull(user, "user");
        Instant now = clock.instant();
        String access = compact(user, TokenUse.ACCESS, now, settings.getAccessTtl());
        String refresh = compact(user, TokenUse.REFRESH, now, settings.getRefreshTtl());
        return new TokenPair(access, refresh);
    }

    @Override
    public TokenClaims verify(String token, TokenUse expectedUse) {
        Objects.requireNonNull(expectedUse, "expectedUse");
        if (token == null || token.isBlank()) {
            throw new InvalidTokenException("Токен отсутствует");
        }
        try {
            Claims claims = parser.parseSignedClaims(token.trim()).getPayload();
            TokenUse actualUse = readUse(claims);
            if (actualUse != expectedUse) {
                throw new InvalidTokenException("Токен не подходит для этой операции");
            }
            String subject = claims.getSubject();
            String email = claims.get(CLAIM_EMAIL, String.class);
            String role = claims.get(CLAIM_ROLE, String.class);
            if (subject == null || email == null || role == null || claims.getExpiration() == null) {
                throw new InvalidTokenException("Сессия недействительна или истекла");
            }
            return new TokenClaims(
                    UUID.fromString(subject),
                    email,
                    Role.fromAuthority(role),
                    actualUse,
                    claims.getExpiration().toInstant()
            );
        } catch (InvalidTokenException exception) {
            throw exception;
        } catch (JwtException | IllegalArgumentException exception) {
            throw new InvalidTokenException("Сессия недействительна или истекла");
        }
    }

    private String compact(UserModel user, TokenUse use, Instant issuedAt, java.time.Duration ttl) {
        return Jwts.builder()
                .id(idGenerator.nextId().toString())
                .subject(user.getId().toString())
                .issuer(settings.getIssuer())
                .claim(CLAIM_ROLE, user.getRole().authority())
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_USE, use.name())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(ttl)))
                .signWith(key)
                .compact();
    }

    private static TokenUse readUse(Claims claims) {
        String rawUse = claims.get(CLAIM_USE, String.class);
        if (rawUse == null) {
            throw new InvalidTokenException("Токен не подходит для этой операции");
        }
        return TokenUse.valueOf(rawUse);
    }
}
