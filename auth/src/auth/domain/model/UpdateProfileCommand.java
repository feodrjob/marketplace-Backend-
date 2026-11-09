package auth.domain.model;

import java.util.Objects;

public final class UpdateProfileCommand {

    private final String accessToken;
    private final ProfilePatch patch;

    public UpdateProfileCommand(String accessToken, ProfilePatch patch) {
        this.accessToken = accessToken;
        this.patch = Objects.requireNonNull(patch, "patch");
    }

    public String getAccessToken() {
        return accessToken;
    }

    public ProfilePatch getPatch() {
        return patch;
    }
}
