package auth.exception;

public class InvalidInnException extends FieldValidationException {

    public static final String FIELD = "inn";

    public InvalidInnException(String message) {
        super(FIELD, message);
    }
}
