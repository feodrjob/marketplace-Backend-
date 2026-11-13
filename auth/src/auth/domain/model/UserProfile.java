package auth.domain.model;

import auth.common.enums.Role;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.UUID;

//GET /users/me
@Getter
@RequiredArgsConstructor
public final class UserProfile {

    private final UUID id;
    private final String email;
    private final Role role;
    private final String fullName;
    private final String phone;
    private final String companyName;
    private final String inn;
    private final String description;
    private final String department;
    private final Instant createdAt;
    private final Instant updatedAt;
}
