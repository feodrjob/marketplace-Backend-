package auth.exception;

// 400, 401, 409 — одна фраза в detail
// 422 — карта details (имя поля JSON → текст)

//EmailAlreadyUsedException — 409 Conflict
//InvalidCredentialsException, InvalidTokenException, UserNotFoundException — 401 Unauthorized
//InvalidInnException — 422 Unprocessable Entity
//ValidationException, ProfileUpdateNotAllowedException — 400 Bad Request

public abstract class AuthException extends RuntimeException {

    protected AuthException(String message) {
        super(message);
    }
}
