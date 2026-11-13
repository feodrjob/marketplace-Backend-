package auth.web.controller;

import auth.domain.model.ProfilePatch;
import auth.domain.model.UpdateProfileCommand;
import auth.domain.model.UserProfile;
import auth.domain.service.UserService;
import auth.exception.InvalidTokenException;
import auth.web.dto.user.UpdateProfileRequest;
import auth.web.dto.user.UserProfileResponse;
import auth.web.mapper.AuthDtoMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Собственный профиль аккаунта")
public class UserController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final UserService userService;
    private final AuthDtoMapper mapper;

    @GetMapping("/me")
    @Operation(summary = "Получить собственный профиль", description = "Доступен аккаунтам BUYER, SELLER и ADMIN с действующим access JWT.")
    @ApiResponse(responseCode = "200", description = "Текущий профиль пользователя.")
    @ApiResponse(responseCode = "401", description = "Access JWT отсутствует или недействителен.")
    public ResponseEntity<UserProfileResponse> getCurrentUser(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authHeader
    ) {
        String token = extractBearerToken(authHeader);
        UserProfile profile = userService.getProfile(token);
        return ResponseEntity.ok(mapper.toResponse(profile));
    }

    @PatchMapping("/me")
    @Operation(summary = "Обновить собственный профиль", description = "BUYER может изменить fullName и phone. SELLER может изменить companyName и description. ADMIN не имеет редактируемых полей.")
    @ApiResponse(responseCode = "200", description = "Обновлённый профиль пользователя.")
    @ApiResponse(responseCode = "400", description = "Некорректный JSON.")
    @ApiResponse(responseCode = "401", description = "Access JWT отсутствует или недействителен.")
    @ApiResponse(responseCode = "422", description = "Поля недопустимы для текущей роли или содержат невалидные значения.")
    public ResponseEntity<UserProfileResponse> updateCurrentUser(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @RequestBody(required = false) UpdateProfileRequest request
    ) {
        String token = extractBearerToken(authHeader);
        ProfilePatch patch = mapper.toPatch(request);
        UserProfile updated = userService.updateProfile(new UpdateProfileCommand(token, patch));
        return ResponseEntity.ok(mapper.toResponse(updated));
    }

    private String extractBearerToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            throw new InvalidTokenException("Active access token required");
        }
        return authHeader.substring(BEARER_PREFIX.length()).trim();
    }
}
