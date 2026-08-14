package com.lzcer.interfaceplatform.model.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.time.LocalDateTime;

/** User-management API request and response objects. */
public final class UserModels {
    private UserModels() { }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true, chain = true)
    public static class LoginCommand { @NotBlank private String username; @NotBlank private String password; }
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true, chain = true)
    public static class LoginView { private String accessToken; private String tokenType; private Instant expiresAt; private UserView user; }
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true, chain = true)
    public static class CreateUserCommand { @NotBlank @Size(min = 3, max = 80) private String username; @NotBlank @Size(min = 8, max = 72) private String password; @NotBlank @Size(max = 120) private String displayName; @NotBlank private String role; private boolean enabled; }
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true, chain = true)
    public static class UpdateUserCommand { @NotBlank @Size(max = 120) private String displayName; @NotBlank private String role; private boolean enabled; }
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true, chain = true)
    public static class PasswordCommand { @NotBlank @Size(min = 8, max = 72) private String password; }
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true, chain = true)
    public static class ChangePasswordCommand { @NotBlank private String currentPassword; @NotBlank @Size(min = 8, max = 72) private String newPassword; }
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true, chain = true)
    public static class UserView { private long id; private String username; private String displayName; private String role; private boolean enabled; private LocalDateTime lastLoginAt; private LocalDateTime createdAt; private LocalDateTime updatedAt; }
}
