package auth.domain.model;

public final class RegisterSellerCommand {

    private final String email;
    private final String password;
    private final String companyName;
    private final String inn;
    private final String description;

    public RegisterSellerCommand(
            String email,
            String password,
            String companyName,
            String inn,
            String description
    ) {
        this.email = email;
        this.password = password;
        this.companyName = companyName;
        this.inn = inn;
        this.description = description;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public String getCompanyName() {
        return companyName;
    }

    public String getInn() {
        return inn;
    }

    public String getDescription() {
        return description;
    }
}
