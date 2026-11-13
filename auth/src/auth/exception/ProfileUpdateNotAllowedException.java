package auth.exception;

public class ProfileUpdateNotAllowedException extends AuthException {

    public ProfileUpdateNotAllowedException(String message) {
        super(message);
    }
}
