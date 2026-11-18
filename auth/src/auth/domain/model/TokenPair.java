package auth.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public final class TokenPair {

    private final String accessToken;
    private final String refreshToken;
}
