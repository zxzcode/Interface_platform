package com.lzcer.interfaceplatform.model.system;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/** Target-system management API objects. */
public final class SystemModels {
    private SystemModels() { }
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true, chain = true)
    public static class SystemCommand { @NotBlank @Size(max = 40) private String code; @NotBlank @Size(max = 120) private String name; @NotBlank @Size(max = 500) private String baseUrl; @NotBlank private String status; }
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true, chain = true)
    public static class SystemView { private long id; private String code; private String name; private String baseUrl; private String status; private LocalDateTime createdAt; private LocalDateTime updatedAt; }
}
