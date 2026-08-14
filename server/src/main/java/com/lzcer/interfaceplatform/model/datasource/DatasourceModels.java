package com.lzcer.interfaceplatform.model.datasource;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/** Data-source management API objects. Credentials are write-only. */
public final class DatasourceModels {
    private DatasourceModels() { }
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true, chain = true)
    public static class DatasourceCommand { @NotBlank @Size(max = 80) private String code; @NotBlank @Size(max = 160) private String name; @NotBlank @Size(max = 40) private String dbType; @NotBlank @Size(max = 1000) private String jdbcUrl; @Size(max = 200) private String driverClassName; @Size(max = 300) private String username; @Size(max = 500) private String password; private boolean enabled; }
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true, chain = true)
    public static class DatasourceView { private Long id; private String code; private String name; private String dbType; private String jdbcUrl; private String driverClassName; private String status; private Boolean enabled; private Boolean credentialConfigured; private Integer poolUsage; private LocalDateTime lastCheckedAt; private LocalDateTime updatedAt; }
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true, chain = true)
    public static class ConnectionTestResult { private boolean success; private long durationMs; private String message; }
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true, chain = true)
    public static class EnabledCommand { private boolean enabled; }
}
