package auth.web;

import auth.common.enums.Role;
import auth.domain.model.TokenClaims;
import auth.domain.model.TokenUse;
import auth.domain.service.TokenService;
import auth.exception.InvalidTokenException;
import auth.web.security.JwtAuthenticationFilter;
import auth.web.security.RestAuthenticationEntryPoint;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityComponentsTest {

    @Mock
    private TokenService tokenService;

    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filter;
    private RestAuthenticationEntryPoint entryPoint;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        filter = new JwtAuthenticationFilter(tokenService);
        entryPoint = new RestAuthenticationEntryPoint(objectMapper);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("JwtAuthenticationFilter: при валидном токене устанавливает Authentication в SecurityContext")
    void shouldAuthenticateValidToken() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer valid.token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        TokenClaims claims = new TokenClaims(
                UUID.randomUUID(),
                "test@example.com",
                Role.BUYER,
                TokenUse.ACCESS,
                Instant.now().plusSeconds(900)
        );

        when(tokenService.verify("valid.token", TokenUse.ACCESS)).thenReturn(claims);

        filter.doFilter(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertEquals(claims, auth.getPrincipal());
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_BUYER")));
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("JwtAuthenticationFilter: при невалидном токене контекст остаётся пустым")
    void shouldClearContextOnInvalidToken() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer bad.token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(tokenService.verify("bad.token", TokenUse.ACCESS))
                .thenThrow(new InvalidTokenException("Invalid token"));

        filter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("RestAuthenticationEntryPoint: возвращает 401, WWW-Authenticate и Problem Details JSON")
    void shouldReturn401ProblemDetailsOnCommence() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/users/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, null);

        assertEquals(401, response.getStatus());
        assertEquals("Bearer", response.getHeader(HttpHeaders.WWW_AUTHENTICATE));
        assertTrue(response.getContentType().contains("application/problem+json"));
        assertTrue(response.getContentAsString().contains("Active access token required"));
    }
}
