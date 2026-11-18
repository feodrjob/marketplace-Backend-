package auth.domain.support;

import org.springframework.security.crypto.password.PasswordEncoder;

// Кодировщик для тестов домена
public final class ReversiblePasswordEncoder implements PasswordEncoder {

    static final String PREFIX = "{test-hash}";

    @Override
    public String encode(CharSequence rawPassword) {
        return PREFIX + rawPassword;
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        return encode(rawPassword).equals(encodedPassword);
    }
}
