package com.lzcer.interfaceplatform.platform;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/** 仪表盘聚合查询的 Mapper；统计 SQL 集中维护在 XML。 */
@Mapper
public interface PlatformReadMapper {
    long countTodayCalls();
    long countTodayFailedCalls();
    long averageTodayDuration();
    long countEnabledHttpInterfaces();
    long countEnabledSqlApis();
    long countEnabledDatasources();
    List<TrendRow> findTodayTrend();
    List<PlatformReadService.SystemStatus> findSystems();

    record TrendRow(Integer hour, Long total, Long success) {}
}
