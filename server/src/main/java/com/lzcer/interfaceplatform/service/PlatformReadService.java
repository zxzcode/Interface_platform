package com.lzcer.interfaceplatform.service;

import com.lzcer.interfaceplatform.mapper.PlatformReadMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static com.lzcer.interfaceplatform.model.platform.PlatformModels.*;

@Service
@RequiredArgsConstructor
public class PlatformReadService {

    private final PlatformReadMapper platformReadMapper;

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

}
