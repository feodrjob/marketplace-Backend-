package auth.domain.model;

import lombok.Getter;

import java.util.Objects;

@Getter
public final class UpdateProfileCommand {

    private final String accessToken;
    private final ProfilePatch patch;

    public UpdateProfileCommand(String accessToken, ProfilePatch patch) {
        this.accessToken = accessToken;
        this.patch = Objects.requireNonNull(patch, "patch");
    }
}
