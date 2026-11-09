package auth.domain.model;

import auth.common.enums.Role;

import java.time.Instant;
import java.util.UUID;

//GET /users/me
public final class UserProfile {

    private final UUID id;
    private final String email;
    private final Role role;
    private final String fullName;
    private final String phone;
    private final String companyName;
    private final String inn;
    private final String description;
    private final String department;
    private final Instant createdAt;
    private final Instant updatedAt;

    public UserProfile(
            UUID id,
            String email,
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
        this.id = id;
        this.email = email;
        this.role = role;
        this.fullName = fullName;
        this.phone = phone;
        this.companyName = companyName;
        this.inn = inn;
        this.description = description;
        this.department = department;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public Role getRole() {
        return role;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPhone() {
        return phone;
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

    public String getDepartment() {
        return department;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
