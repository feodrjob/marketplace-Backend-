package auth.domain.model;

public final class RefreshSessionCommand {

    private final String refreshToken;

    public RefreshSessionCommand(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }
}
