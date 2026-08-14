package com.lzcer.interfaceplatform.model.client;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/** External caller credential API objects. AppSecret is returned only when created or rotated. */
public final class ClientModels {
    private ClientModels() { }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true, chain = true)
    public static class Permission { @NotBlank private String routeType; @NotBlank private String resourceCode; }
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true, chain = true)
    public static class CreateClientCommand { @NotBlank @Size(max = 80) private String code; @NotBlank @Size(max = 160) private String name; private boolean enabled; @NotNull @Valid private List<Permission> permissions; }
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true, chain = true)
    public static class UpdateClientCommand { @NotBlank @Size(max = 160) private String name; private boolean enabled; @Valid private List<Permission> permissions; }
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true, chain = true)
    public static class PermissionCommand { @Valid private List<Permission> permissions; }
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true, chain = true)
    public static class ClientView {
        private long id; private String code; private String name; private String appKey; private boolean enabled;
        private List<Permission> permissions; private LocalDateTime createdAt; private LocalDateTime updatedAt;
        public ClientView withPermissions(List<Permission> value) { return permissions(value); }
    }
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true, chain = true)
    public static class ClientSecretView { private ClientView client; private String appSecret; }
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true, chain = true)
    public static class AuthenticatedClient { private long id; private String code; private String name; private String appKey; private String appSecret; }
}
