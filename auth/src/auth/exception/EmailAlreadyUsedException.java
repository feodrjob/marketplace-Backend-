package auth.exception;

public class EmailAlreadyUsedException extends AuthException {

    public EmailAlreadyUsedException(String message) {
        super(message);
    }
}
