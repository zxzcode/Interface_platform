package com.lzcer.interfaceplatform.invocationlog;

import com.lzcer.interfaceplatform.common.api.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class InvocationLogService {

    private static final Logger log = LoggerFactory.getLogger(InvocationLogService.class);
    private final JdbcClient jdbcClient;

    public InvocationLogService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void save(InvocationRecord value) {
        try {
            jdbcClient.sql("""
                    insert into ip_invocation_log(
                        trace_id, route_type, interface_code, interface_name, caller, target_system,
                        request_method, request_path, target_address, call_status, platform_code,
                        http_status, duration_ms, request_headers, request_summary, response_headers,
                        response_summary, error_message, call_time, completed_at
                    ) values (
                        :traceId, :routeType, :interfaceCode, :interfaceName, :caller, :targetSystem,
                        :requestMethod, :requestPath, :targetAddress, :callStatus, :platformCode,
                        :httpStatus, :durationMs, :requestHeaders, :requestSummary, :responseHeaders,
                        :responseSummary, :errorMessage, :callTime, :completedAt
                    )
                    """).param("traceId", value.traceId()).param("routeType", value.routeType())
                    .param("interfaceCode", value.interfaceCode()).param("interfaceName", value.interfaceName())
                    .param("caller", value.caller()).param("targetSystem", value.targetSystem())
                    .param("requestMethod", value.requestMethod()).param("requestPath", value.requestPath())
                    .param("targetAddress", value.targetAddress()).param("callStatus", value.callStatus())
                    .param("platformCode", value.platformCode()).param("httpStatus", value.httpStatus())
                    .param("durationMs", value.durationMs()).param("requestHeaders", value.requestHeaders())
                    .param("requestSummary", value.requestSummary()).param("responseHeaders", value.responseHeaders())
                    .param("responseSummary", value.responseSummary()).param("errorMessage", value.errorMessage())
                    .param("callTime", value.callTime()).param("completedAt", value.completedAt()).update();
        } catch (RuntimeException exception) {
            log.error("Invocation log persistence failed, traceId={}", value.traceId(), exception);
        }
    }

    public List<LogSummary> list(int limit) {
        return jdbcClient.sql("""
                select trace_id, route_type, interface_code, interface_name, caller, target_system,
                       request_method, request_path, call_status, platform_code, http_status,
                       duration_ms, call_time
                  from ip_invocation_log order by call_time desc limit :limit
                """).param("limit", Math.min(Math.max(limit, 1), 200))
                .query((rs, rowNum) -> new LogSummary(rs.getString("trace_id"), rs.getString("route_type"),
                        rs.getString("interface_code"), rs.getString("interface_name"), rs.getString("caller"),
                        rs.getString("target_system"), rs.getString("request_method"), rs.getString("request_path"),
                        rs.getString("call_status"), rs.getString("platform_code"), rs.getInt("http_status"),
                        rs.getLong("duration_ms"), rs.getObject("call_time", LocalDateTime.class))).list();
    }

    public LogDetail detail(String traceId) {
        return jdbcClient.sql("select * from ip_invocation_log where trace_id = :traceId")
                .param("traceId", traceId).query(InvocationLogService::mapDetail).optional()
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "IP-LOG-404", "调用日志不存在: " + traceId));
    }

    private static LogDetail mapDetail(ResultSet rs, int rowNum) throws SQLException {
        return new LogDetail(rs.getString("trace_id"), rs.getString("route_type"),
                rs.getString("interface_code"), rs.getString("interface_name"), rs.getString("caller"),
                rs.getString("target_system"), rs.getString("request_method"), rs.getString("request_path"),
                rs.getString("target_address"), rs.getString("call_status"), rs.getString("platform_code"),
                rs.getInt("http_status"), rs.getLong("duration_ms"), rs.getString("request_headers"),
                rs.getString("request_summary"), rs.getString("response_headers"),
                rs.getString("response_summary"), rs.getString("error_message"),
                rs.getObject("call_time", LocalDateTime.class), rs.getObject("completed_at", LocalDateTime.class));
    }

    public record InvocationRecord(String traceId, String routeType, String interfaceCode, String interfaceName,
                                   String caller, String targetSystem, String requestMethod, String requestPath,
                                   String targetAddress, String callStatus, String platformCode, Integer httpStatus,
                                   long durationMs, String requestHeaders, String requestSummary,
                                   String responseHeaders, String responseSummary, String errorMessage,
                                   LocalDateTime callTime, LocalDateTime completedAt) {}

    public record LogSummary(String traceId, String routeType, String interfaceCode, String interfaceName,
                             String caller, String targetSystem, String requestMethod, String requestPath,
                             String status, String platformCode, int httpStatus, long durationMs,
                             LocalDateTime requestTime) {}

    public record LogDetail(String traceId, String routeType, String interfaceCode, String interfaceName,
                            String caller, String targetSystem, String requestMethod, String requestPath,
                            String targetAddress, String status, String platformCode, int httpStatus,
                            long durationMs, String requestHeaders, String requestSummary,
                            String responseHeaders, String responseSummary, String errorMessage,
                            LocalDateTime requestTime, LocalDateTime completedAt) {}
}
