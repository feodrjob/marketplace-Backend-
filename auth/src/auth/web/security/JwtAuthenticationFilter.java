package auth.web.security;

import auth.domain.model.TokenClaims;
import auth.domain.model.TokenUse;
import auth.domain.service.TokenService;
import auth.exception.InvalidTokenException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String BEARER_PREFIX = "Bearer ";

    private final TokenService tokenService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length()).trim();
            try {
                TokenClaims claims = tokenService.verify(token, TokenUse.ACCESS);
                var authorities = List.of(new SimpleGrantedAuthority(claims.getRole().authority()));
                var authentication = new UsernamePasswordAuthenticationToken(claims, token, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (InvalidTokenException ignored) {
                // SecurityContext remains empty, which triggers RestAuthenticationEntryPoint on secured routes
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
