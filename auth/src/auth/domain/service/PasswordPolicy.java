package auth.domain.service;

import auth.exception.ValidationException;

public final class PasswordPolicy {

    public void check(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new ValidationException("Пароль обязателен");
        }
    }
}
