package auth.web.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterSellerRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    @Pattern(regexp = "^\\S+$", message = "Password must not contain whitespace")
    private String password;

    @NotBlank(message = "Company name is required")
    @Pattern(regexp = ".*\\S.*", message = "Company name is required")
    private String companyName;

    @NotBlank(message = "INN is required")
    @Pattern(regexp = "^(?:[0-9]{10}|[0-9]{12})$", message = "INN must consist of 10 or 12 digits")
    private String inn;

    private String description;
}
