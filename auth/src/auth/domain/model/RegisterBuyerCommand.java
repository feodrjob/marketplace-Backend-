package auth.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public final class RegisterBuyerCommand {

    private final String email;
    private final String password;
    private final String fullName;
    private final String phone;
}
