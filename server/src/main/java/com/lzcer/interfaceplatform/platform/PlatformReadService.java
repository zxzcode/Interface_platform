package com.lzcer.interfaceplatform.platform;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class PlatformReadService {

    private final PlatformReadMapper platformReadMapper;

    public PlatformReadService(PlatformReadMapper platformReadMapper) {
        this.platformReadMapper = platformReadMapper;
    }

    public DashboardSummary dashboard() {
        long todayCalls = platformReadMapper.countTodayCalls();
        long failedCalls = platformReadMapper.countTodayFailedCalls();
        long successCalls = Math.max(0, todayCalls - failedCalls);
        BigDecimal successRate = todayCalls == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(successCalls * 100.0 / todayCalls).setScale(2, RoundingMode.HALF_UP);
        long averageDuration = platformReadMapper.averageTodayDuration();

        return new DashboardSummary(todayCalls, successRate, failedCalls, averageDuration,
                platformReadMapper.countEnabledHttpInterfaces() + platformReadMapper.countEnabledSqlApis(),
                platformReadMapper.countEnabledDatasources(), trend(), systems());
    }

    private List<TrendPoint> trend() {
        return platformReadMapper.findTodayTrend().stream().map(row ->
                new TrendPoint(String.format("%02d:00", row.hour()), row.total(), row.success())).toList();
    }

    private List<SystemStatus> systems() {
        return platformReadMapper.findSystems();
    }

    public record DashboardSummary(long todayCalls, BigDecimal successRate, long failedCalls,
                                   long averageDurationMs, long activeInterfaces, long activeDatasources,
                                   List<TrendPoint> trend, List<SystemStatus> systems) {}

    public record TrendPoint(String time, long total, long success) {}

    public record SystemStatus(String code, String name, String status) {}
}
