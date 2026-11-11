package auth.domain.model;

import auth.common.enums.Role;
import auth.exception.ProfileUpdateNotAllowedException;
import auth.exception.ValidationException;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Getter
@EqualsAndHashCode(of = "id")
@ToString(of = {"id", "email", "role"})
public final class UserModel {

    private final UUID id;
    private final String email;
    private final String passwordHash;
    private final Role role;
    private final String fullName;
    private final String phone;
    private final String companyName;
    private final String inn;
    private final String description;
    private final String department;
    private final Instant createdAt;
    private final Instant updatedAt;

    private UserModel(
            UUID id,
            String email,
            String passwordHash,
            Role role,
            String fullName,
            String phone,
            String companyName,
            String inn,
            String description,
            String department,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.email = requireText(email, "email");
        this.passwordHash = requireText(passwordHash, "passwordHash");
        this.role = Objects.requireNonNull(role, "role");
        this.fullName = fullName;
        this.phone = phone;
        this.companyName = companyName;
        this.inn = inn;
        this.description = description;
        this.department = department;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        checkRoleFields();
    }

    public static UserModel registerBuyer(
            UUID id,
            String email,
            String passwordHash,
            String fullName,
            String phone,
            Instant now
    ) {
        return new UserModel(
                id,
                email,
                passwordHash,
                Role.BUYER,
                fullName,
                phone,
                null,
                null,
                null,
                null,
                now,
                now
        );
    }

    public static UserModel registerSeller(
            UUID id,
            String email,
            String passwordHash,
            String companyName,
            String inn,
            String description,
            Instant now
    ) {
        return new UserModel(
                id,
                email,
                passwordHash,
                Role.SELLER,
                null,
                null,
                companyName,
                inn,
                description,
                null,
                now,
                now
        );
    }

    // Сборка записи, которая уже прошла регистрацию или служебную миграцию.
    public static UserModel restore(
            UUID id,
            String email,
            String passwordHash,
            Role role,
            String fullName,
            String phone,
            String companyName,
            String inn,
            String description,
            String department,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new UserModel(
                id,
                email,
                passwordHash,
                role,
                fullName,
                phone,
                companyName,
                inn,
                description,
                department,
                createdAt,
                updatedAt
        );
    }

    public UserModel apply(ProfilePatch patch, Instant now) {
        Objects.requireNonNull(patch, "patch");
        Objects.requireNonNull(now, "now");
        return switch (role) {
            case BUYER -> applyBuyer(patch, now);
            case SELLER -> applySeller(patch, now);
            case ADMIN -> applyAdmin(patch);
        };
    }

    private UserModel applyBuyer(ProfilePatch patch, Instant now) {
        rejectIfSpecified(patch.isCompanyNameSpecified() || patch.isDescriptionSpecified() || patch.isInnSpecified(),
                "Покупатель может изменять только имя и телефон");
        String newName = patch.isFullNameSpecified() ? TextValues.trimToNull(patch.getFullName()) : fullName;
        String newPhone = patch.isPhoneSpecified() ? TextValues.trimToNull(patch.getPhone()) : phone;
        if (Objects.equals(newName, fullName) && Objects.equals(newPhone, phone)) {
            return this;
        }
        return copy(newName, newPhone, companyName, inn, description, now);
    }

    private UserModel applySeller(ProfilePatch patch, Instant now) {
        rejectIfSpecified(patch.isFullNameSpecified() || patch.isPhoneSpecified(),
                "Продавец может изменять только название компании и описание");
        if (patch.isInnSpecified()) {
            String requested = TextValues.trimToNull(patch.getInn());
            if (!inn.equals(requested)) {
                throw new ProfileUpdateNotAllowedException("ИНН нельзя изменить после регистрации");
            }
        }
        String newCompany = companyName;
        if (patch.isCompanyNameSpecified()) {
            newCompany = TextValues.trimToNull(patch.getCompanyName());
            if (newCompany == null) {
                throw new ValidationException("Название компании не может быть пустым");
            }
        }
        String newDescription = description;
        if (patch.isDescriptionSpecified()) {
            newDescription = TextValues.trimToNull(patch.getDescription());
        }
        if (Objects.equals(newCompany, companyName) && Objects.equals(newDescription, description)) {
            return this;
        }
        return copy(fullName, phone, newCompany, inn, newDescription, now);
    }

    private UserModel applyAdmin(ProfilePatch patch) {
        rejectIfSpecified(patch.hasAnyField(), "Профиль администратора нельзя изменить через этот сценарий");
        return this;
    }

    private UserModel copy(
            String newFullName,
            String newPhone,
            String newCompanyName,
            String newInn,
            String newDescription,
            Instant now
    ) {
        return new UserModel(
                id,
                email,
                passwordHash,
                role,
                newFullName,
                newPhone,
                newCompanyName,
                newInn,
                newDescription,
                department,
                createdAt,
                now
        );
    }

    private void checkRoleFields() {
        switch (role) {
            case BUYER -> {
                requireAbsent(companyName, "companyName");
                requireAbsent(inn, "inn");
                requireAbsent(description, "description");
                requireAbsent(department, "department");
            }
            case SELLER -> {
                requirePresent(companyName, "companyName");
                requirePresent(inn, "inn");
                requireAbsent(fullName, "fullName");
                requireAbsent(phone, "phone");
                requireAbsent(department, "department");
            }
            case ADMIN -> {
                requireAbsent(fullName, "fullName");
                requireAbsent(phone, "phone");
                requireAbsent(companyName, "companyName");
                requireAbsent(inn, "inn");
                requireAbsent(description, "description");
            }
            default -> throw new IllegalArgumentException("Неизвестная роль");
        }
    }

    private static void rejectIfSpecified(boolean specified, String message) {
        if (specified) {
            throw new ProfileUpdateNotAllowedException(message);
        }
    }

    private static void requireAbsent(String value, String field) {
        if (value != null) {
            throw new IllegalArgumentException("Поле " + field + " не относится к этой роли");
        }
    }

    private static void requirePresent(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Поле " + field + " обязательно для этой роли");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Поле " + field + " обязательно");
        }
        return value;
    }
}
