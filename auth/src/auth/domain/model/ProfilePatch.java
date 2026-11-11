package auth.domain.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

// PATCH /users/me
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
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
