package auth.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public final class AuthSession {

    private final String accessToken;
    private final String refreshToken;
    private final UserProfile profile;
}
