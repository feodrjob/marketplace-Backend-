package auth.domain.model;

import auth.common.enums.Role;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@RequiredArgsConstructor
public final class TokenClaims {

    private final UUID userId;
    private final String email;
    private final Role role;
    private final TokenUse use;
    private final Instant expiresAt;
}
