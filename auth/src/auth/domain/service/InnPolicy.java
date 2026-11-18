package auth.domain.service;

import auth.exception.InvalidInnException;
import auth.exception.ValidationException;

public final class InnPolicy {

    public static final int LEGAL_ENTITY_LENGTH = 10;
    public static final int INDIVIDUAL_LENGTH = 12;

    public String canonicalize(String rawInn) {
        if (rawInn == null || rawInn.isBlank()) {
            throw new ValidationException(InvalidInnException.FIELD, "INN is required");
        }
        String inn = rawInn.trim();
        if (!isAsciiDigits(inn) || (inn.length() != LEGAL_ENTITY_LENGTH && inn.length() != INDIVIDUAL_LENGTH)) {
            throw new InvalidInnException("INN must consist of 10 or 12 digits");
        }
        return inn;
    }

    private static boolean isAsciiDigits(String value) {
        for (int index = 0; index < value.length(); index++) {
            char symbol = value.charAt(index);
            if (symbol < '0' || symbol > '9') {
                return false;
            }
        }
        return true;
    }
}
