package auth.domain.model;

public final class AuthSession {

    private final String accessToken;
    private final String refreshToken;
    private final UserProfile profile;

    public AuthSession(String accessToken, String refreshToken, UserProfile profile) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.profile = profile;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public UserProfile getProfile() {
        return profile;
    }
}
