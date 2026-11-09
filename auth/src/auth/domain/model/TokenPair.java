package auth.domain.model;

public final class TokenPair {

    private final String accessToken;
    private final String refreshToken;

    public TokenPair(String accessToken, String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }
}
