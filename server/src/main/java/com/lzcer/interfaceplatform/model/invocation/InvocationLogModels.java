package com.lzcer.interfaceplatform.model.invocation;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/** Sanitized invocation audit-log persistence and read models. */
public final class InvocationLogModels {
    private InvocationLogModels() { }
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true, chain = true)
    public static class InvocationRecord { private String traceId; private String routeType; private String interfaceCode; private String interfaceName; private String caller; private String targetSystem; private String requestMethod; private String requestPath; private String targetAddress; private String callStatus; private String platformCode; private Integer httpStatus; private long durationMs; private String requestHeaders; private String requestSummary; private String responseHeaders; private String responseSummary; private String errorMessage; private LocalDateTime callTime; private LocalDateTime completedAt; }
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true, chain = true)
    public static class LogSummary { private String traceId; private String routeType; private String interfaceCode; private String interfaceName; private String caller; private String targetSystem; private String requestMethod; private String requestPath; private String status; private String platformCode; private Integer httpStatus; private Long durationMs; private LocalDateTime requestTime; }
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true, chain = true)
    public static class LogDetail { private String traceId; private String routeType; private String interfaceCode; private String interfaceName; private String caller; private String targetSystem; private String requestMethod; private String requestPath; private String targetAddress; private String status; private String platformCode; private Integer httpStatus; private Long durationMs; private String requestHeaders; private String requestSummary; private String responseHeaders; private String responseSummary; private String errorMessage; private LocalDateTime requestTime; private LocalDateTime completedAt; }
}
