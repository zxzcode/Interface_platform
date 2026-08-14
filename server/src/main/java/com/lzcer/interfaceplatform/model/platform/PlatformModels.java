package com.lzcer.interfaceplatform.model.platform;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/** Dashboard response objects. */
public final class PlatformModels {
    private PlatformModels() { }
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true, chain = true)
    public static class DashboardSummary { private long todayCalls; private BigDecimal successRate; private long failedCalls; private long averageDurationMs; private long activeInterfaces; private long activeDatasources; private List<TrendPoint> trend; private List<SystemStatus> systems; }
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true, chain = true)
    public static class TrendPoint { private String time; private long total; private long success; }
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true, chain = true)
    public static class SystemStatus { private String code; private String name; private String status; }
}
