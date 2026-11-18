package auth.exception;

import java.util.Map;

public class ProfileUpdateNotAllowedException extends FieldValidationException {

    public ProfileUpdateNotAllowedException(String field, String message) {
        super(field, message);
    }

    public ProfileUpdateNotAllowedException(Map<String, String> details) {
        super(details);
    }
}
