package auth.domain.model;

public final class SignInCommand {

    private final String email;
    private final String password;

    public SignInCommand(String email, String password) {
        this.email = email;
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }
}
