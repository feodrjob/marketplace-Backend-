package auth.exception;

import java.util.Map;

public class ValidationException extends FieldValidationException {

    public ValidationException(String field, String message) {
        super(field, message);
    }

    public ValidationException(Map<String, String> details) {
        super(details);
    }
}
