package auth.common.enums;

public enum Role {
    BUYER,
    SELLER,
    ADMIN;

    private static final String AUTHORITY_PREFIX = "ROLE_";

    public String authority() {
        return AUTHORITY_PREFIX + name();
    }

    public static Role fromAuthority(String authority) {
        for (Role role : values()) {
            if (role.authority().equals(authority)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Неизвестная роль в токене");
    }
}
