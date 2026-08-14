package com.lzcer.interfaceplatform.model.sqlapi;

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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Read-only SQL API request, response, and runtime objects. */
public final class SqlApiModels {
    private SqlApiModels() { }
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true, chain = true)
    public static class SqlApiCommand { @NotBlank @Size(max = 80) private String code; @NotBlank @Size(max = 160) private String name; @Size(max = 500) private String description; @NotBlank @Pattern(regexp = "/.*") @Size(max = 300) private String path; @NotBlank private String method; @NotNull private Long datasourceId; @NotBlank @Size(max = 20000) private String sql; @Min(1) @Max(60) private int timeoutSeconds; @Min(1) @Max(5000) private int maxRows; private boolean enabled; }
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true, chain = true)
    public static class SqlApiView { private Long id; private String code; private String name; private String description; private String path; private String method; private Long datasourceId; private String datasourceName; private String sql; private Integer timeoutSeconds; private Integer maxRows; private Boolean enabled; private LocalDateTime updatedAt; }
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true, chain = true)
    public static class RuntimeConfig { private Long id; private String code; private String name; private String path; private String method; private Long datasourceId; private String datasourceName; private String sql; private Integer timeoutSeconds; private Integer maxRows; }
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true, chain = true)
    public static class QueryResult { private int rowCount; private int maxRows; private List<Map<String, Object>> rows; }
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true, chain = true)
    public static class EnabledCommand { private boolean enabled; }
}
