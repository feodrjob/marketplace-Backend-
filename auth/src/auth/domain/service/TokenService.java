package auth.domain.service;

import auth.domain.model.TokenClaims;
import auth.domain.model.TokenPair;
import auth.domain.model.TokenUse;
import auth.domain.model.UserModel;

public interface TokenService {

    TokenPair issue(UserModel user);

    TokenClaims verify(String token, TokenUse expectedUse);
}
