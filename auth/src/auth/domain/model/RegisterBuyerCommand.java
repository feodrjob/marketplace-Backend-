package auth.domain.model;

public final class RegisterBuyerCommand {

    private final String email;
    private final String password;
    private final String fullName;
    private final String phone;

    public RegisterBuyerCommand(String email, String password, String fullName, String phone) {
        this.email = email;
        this.password = password;
        this.fullName = fullName;
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPhone() {
        return phone;
    }
}
