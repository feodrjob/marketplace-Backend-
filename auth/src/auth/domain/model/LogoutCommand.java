package auth.domain.model;

public final class LogoutCommand {

    private final String accessToken;

    public LogoutCommand(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getAccessToken() {
        return accessToken;
    }
}
