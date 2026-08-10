package com.lzcer.interfaceplatform.platform;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class PlatformReadService {

    private final JdbcClient jdbcClient;

    public PlatformReadService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public DashboardSummary dashboard() {
        long todayCalls = count("select count(*) from ip_invocation_log where call_time >= current_date");
        long failedCalls = count("select count(*) from ip_invocation_log where call_time >= current_date and call_status = 'FAILED'");
        long successCalls = Math.max(0, todayCalls - failedCalls);
        BigDecimal successRate = todayCalls == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(successCalls * 100.0 / todayCalls).setScale(2, RoundingMode.HALF_UP);
        long averageDuration = jdbcClient.sql(
                "select coalesce(avg(duration_ms), 0) from ip_invocation_log where call_time >= current_date")
                .query(Long.class).single();

        return new DashboardSummary(todayCalls, successRate, failedCalls, averageDuration,
                count("select count(*) from ip_interface where enabled = true")
                        + count("select count(*) from ip_sql_api where enabled = true"),
                count("select count(*) from ip_datasource where enabled = true"), trend(), systems());
    }

    private List<TrendPoint> trend() {
        return jdbcClient.sql("""
                select hour(call_time) as call_hour, count(*) as total,
                       sum(case when call_status = 'SUCCESS' then 1 else 0 end) as success_count
                  from ip_invocation_log
                 where call_time >= current_date
                 group by hour(call_time) order by call_hour
                """).query((rs, rowNum) -> new TrendPoint(
                        String.format("%02d:00", rs.getInt("call_hour")),
                        rs.getLong("total"), rs.getLong("success_count"))).list();
    }

    private List<SystemStatus> systems() {
        return jdbcClient.sql("select system_code, system_name, health_status from ip_system order by id")
                .query((rs, rowNum) -> new SystemStatus(rs.getString("system_code"),
                        rs.getString("system_name"), rs.getString("health_status"))).list();
    }

    private long count(String sql) {
        return jdbcClient.sql(sql).query(Long.class).single();
    }

    public record DashboardSummary(long todayCalls, BigDecimal successRate, long failedCalls,
                                   long averageDurationMs, long activeInterfaces, long activeDatasources,
                                   List<TrendPoint> trend, List<SystemStatus> systems) {}

    public record TrendPoint(String time, long total, long success) {}

    public record SystemStatus(String code, String name, String status) {}
}
