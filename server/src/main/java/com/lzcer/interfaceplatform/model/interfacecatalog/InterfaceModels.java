package com.lzcer.interfaceplatform.model.interfacecatalog;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.net.URI;
import java.time.LocalDateTime;

/** HTTP interface catalog request, response, and route-runtime objects. */
public final class InterfaceModels {
    private InterfaceModels() { }
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true, chain = true)
    public static class InterfaceCommand { @NotBlank @Size(max = 80) private String code; @NotBlank @Size(max = 160) private String name; @Size(max = 500) private String description; @NotNull private Long sourceSystemId; @NotNull private Long targetSystemId; @NotBlank private String method; @NotBlank @Pattern(regexp = "/.*") @Size(max = 300) private String path; @NotBlank @Size(max = 1000) private String targetUrl; @Min(500) @Max(30000) private int connectTimeoutMs; @Min(500) @Max(120000) private int readTimeoutMs; private boolean enabled; }
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true, chain = true)
    public static class InterfaceView { private Long id; private String code; private String name; private String description; private Long sourceSystemId; private String sourceSystem; private Long targetSystemId; private String targetSystem; private String method; private String path; private String targetUrl; private Integer connectTimeoutMs; private Integer readTimeoutMs; private Boolean enabled; private Long todayCalls; private java.math.BigDecimal successRate; private Long averageDurationMs; private LocalDateTime updatedAt; }
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true, chain = true)
    public static class SystemOption { private Long id; private String code; private String name; private String baseUrl; private String status; }
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true, chain = true)
    public static class EnabledCommand { private boolean enabled; }
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true, chain = true)
    public static class RouteConfig { private long id; private String code; private String name; private String targetSystem; private String targetStatus; private URI targetUrl; private int connectTimeoutMs; private int readTimeoutMs; private String method; private String path; public boolean targetAvailable() { return !"OFFLINE".equalsIgnoreCase(targetStatus); } }
}
