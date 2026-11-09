package auth.domain.service;

import auth.exception.ValidationException;

import java.util.Locale;

public final class EmailPolicy {

    public String normalize(String rawEmail) {
        if (rawEmail == null || rawEmail.isBlank()) {
            throw new ValidationException("Email обязателен");
        }
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        if (email.chars().anyMatch(Character::isWhitespace)) {
            throw new ValidationException("Некорректный формат email");
        }
        int at = email.indexOf('@');
        if (at <= 0 || at != email.lastIndexOf('@') || at >= email.length() - 1) {
            throw new ValidationException("Некорректный формат email");
        }
        String domain = email.substring(at + 1);
        int dot = domain.lastIndexOf('.');
        if (dot <= 0 || dot == domain.length() - 1) {
            throw new ValidationException("Некорректный формат email");
        }
        return email;
    }
}
