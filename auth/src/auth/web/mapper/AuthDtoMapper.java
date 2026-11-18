package auth.web.mapper;

import auth.domain.model.AuthSession;
import auth.domain.model.ProfilePatch;
import auth.domain.model.RegisterBuyerCommand;
import auth.domain.model.RegisterSellerCommand;
import auth.domain.model.SignInCommand;
import auth.domain.model.UserProfile;
import auth.web.dto.auth.AuthResponse;
import auth.web.dto.auth.LoginRequest;
import auth.web.dto.auth.RegisterBuyerRequest;
import auth.web.dto.auth.RegisterSellerRequest;
import auth.web.dto.user.UpdateProfileRequest;
import auth.web.dto.user.UserProfileResponse;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class AuthDtoMapper {

    public RegisterBuyerCommand toCommand(RegisterBuyerRequest request) {
        Objects.requireNonNull(request, "request");
        return new RegisterBuyerCommand(
                request.getEmail(),
                request.getPassword(),
                request.getFullName(),
                request.getPhone()
        );
    }

    public RegisterSellerCommand toCommand(RegisterSellerRequest request) {
        Objects.requireNonNull(request, "request");
        return new RegisterSellerCommand(
                request.getEmail(),
                request.getPassword(),
                request.getCompanyName(),
                request.getInn(),
                request.getDescription()
        );
    }

    public SignInCommand toCommand(LoginRequest request) {
        Objects.requireNonNull(request, "request");
        return new SignInCommand(
                request.getEmail(),
                request.getPassword()
        );
    }

    public ProfilePatch toPatch(UpdateProfileRequest request) {
        if (request == null) {
            return ProfilePatch.builder().build();
        }
        ProfilePatch.Builder builder = ProfilePatch.builder();
        if (request.isFullNameSpecified()) {
            builder.fullName(request.getFullName());
        }
        if (request.isPhoneSpecified()) {
            builder.phone(request.getPhone());
        }
        if (request.isCompanyNameSpecified()) {
            builder.companyName(request.getCompanyName());
        }
        if (request.isDescriptionSpecified()) {
            builder.description(request.getDescription());
        }
        if (request.isInnSpecified()) {
            builder.inn(request.getInn());
        }
        return builder.build();
    }

    public AuthResponse toResponse(AuthSession session) {
        Objects.requireNonNull(session, "session");
        return AuthResponse.builder()
                .accessToken(session.getAccessToken())
                .user(toResponse(session.getProfile()))
                .build();
    }

    public UserProfileResponse toResponse(UserProfile profile) {
        Objects.requireNonNull(profile, "profile");
        return UserProfileResponse.builder()
                .id(profile.getId())
                .email(profile.getEmail())
                .role(profile.getRole())
                .fullName(profile.getFullName())
                .phone(profile.getPhone())
                .companyName(profile.getCompanyName())
                .inn(profile.getInn())
                .description(profile.getDescription())
                .department(profile.getDepartment())
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }
}
