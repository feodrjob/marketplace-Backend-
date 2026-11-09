package auth.domain.model;

//PATCH /users/me
public final class ProfilePatch {

    private final String fullName;
    private final boolean fullNameSpecified;
    private final String phone;
    private final boolean phoneSpecified;
    private final String companyName;
    private final boolean companyNameSpecified;
    private final String description;
    private final boolean descriptionSpecified;
    private final String inn;
    private final boolean innSpecified;

    private ProfilePatch(
            String fullName,
            boolean fullNameSpecified,
            String phone,
            boolean phoneSpecified,
            String companyName,
            boolean companyNameSpecified,
            String description,
            boolean descriptionSpecified,
            String inn,
            boolean innSpecified
    ) {
        this.fullName = fullName;
        this.fullNameSpecified = fullNameSpecified;
        this.phone = phone;
        this.phoneSpecified = phoneSpecified;
        this.companyName = companyName;
        this.companyNameSpecified = companyNameSpecified;
        this.description = description;
        this.descriptionSpecified = descriptionSpecified;
        this.inn = inn;
        this.innSpecified = innSpecified;
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean hasAnyField() {
        return fullNameSpecified
                || phoneSpecified
                || companyNameSpecified
                || descriptionSpecified
                || innSpecified;
    }

    public String getFullName() {
        return fullName;
    }

    public boolean isFullNameSpecified() {
        return fullNameSpecified;
    }

    public String getPhone() {
        return phone;
    }

    public boolean isPhoneSpecified() {
        return phoneSpecified;
    }

    public String getCompanyName() {
        return companyName;
    }

    public boolean isCompanyNameSpecified() {
        return companyNameSpecified;
    }

    public String getDescription() {
        return description;
    }

    public boolean isDescriptionSpecified() {
        return descriptionSpecified;
    }

    public String getInn() {
        return inn;
    }

    public boolean isInnSpecified() {
        return innSpecified;
    }

    public static final class Builder {
        private String fullName;
        private boolean fullNameSpecified;
        private String phone;
        private boolean phoneSpecified;
        private String companyName;
        private boolean companyNameSpecified;
        private String description;
        private boolean descriptionSpecified;
        private String inn;
        private boolean innSpecified;

        public Builder fullName(String fullName) {
            this.fullName = fullName;
            this.fullNameSpecified = true;
            return this;
        }

        public Builder phone(String phone) {
            this.phone = phone;
            this.phoneSpecified = true;
            return this;
        }

        public Builder companyName(String companyName) {
            this.companyName = companyName;
            this.companyNameSpecified = true;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            this.descriptionSpecified = true;
            return this;
        }

        public Builder inn(String inn) {
            this.inn = inn;
            this.innSpecified = true;
            return this;
        }

        public ProfilePatch build() {
            return new ProfilePatch(
                    fullName,
                    fullNameSpecified,
                    phone,
                    phoneSpecified,
                    companyName,
                    companyNameSpecified,
                    description,
                    descriptionSpecified,
                    inn,
                    innSpecified
            );
        }
    }
}
