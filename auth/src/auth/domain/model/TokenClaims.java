package auth.domain.model;

import auth.common.enums.Role;

import java.time.Instant;
import java.util.UUID;

public final class TokenClaims {

    private final UUID userId;
    private final String email;
    private final Role role;
    private final TokenUse use;
    private final Instant expiresAt;

    public TokenClaims(UUID userId, String email, Role role, TokenUse use, Instant expiresAt) {
        this.userId = userId;
        this.email = email;
        this.role = role;
        this.use = use;
        this.expiresAt = expiresAt;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public Role getRole() {
        return role;
    }

    public TokenUse getUse() {
        return use;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
