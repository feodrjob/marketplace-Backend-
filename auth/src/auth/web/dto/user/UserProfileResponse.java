package auth.web.dto.user;

import auth.common.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {

    private UUID id;
    private String email;
    private Role role;
    private String fullName;
    private String phone;
    private String companyName;
    private String inn;
    private String description;
    private String department;
    private Instant createdAt;
    private Instant updatedAt;
}
