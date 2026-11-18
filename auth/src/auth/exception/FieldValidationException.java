package auth.exception;

import java.util.Map;
import java.util.Objects;

public abstract class FieldValidationException extends AuthException {

    private final Map<String, String> details;

    protected FieldValidationException(String field, String message) {
        super(message);
        this.details = Map.of(
                Objects.requireNonNull(field, "field"),
                Objects.requireNonNull(message, "message")
        );
    }

    protected FieldValidationException(Map<String, String> details) {
        super(firstMessage(details));
        this.details = Map.copyOf(details);
    }

    public Map<String, String> getDetails() {
        return details;
    }

    private static String firstMessage(Map<String, String> details) {
        if (details == null || details.isEmpty()) {
            throw new IllegalArgumentException("At least one field error is required");
        }
        return details.values().iterator().next();
    }
}
