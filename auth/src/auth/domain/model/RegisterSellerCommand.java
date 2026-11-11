package auth.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public final class RegisterSellerCommand {

    private final String email;
    private final String password;
    private final String companyName;
    private final String inn;
    private final String description;
}
