package com.lzcer.interfaceplatform.invocationlog;

import com.lzcer.interfaceplatform.common.api.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class InvocationLogService {

    private static final Logger log = LoggerFactory.getLogger(InvocationLogService.class);
    private final InvocationLogMapper logMapper;

    public InvocationLogService(InvocationLogMapper logMapper) {
        this.logMapper = logMapper;
    }

    public void save(InvocationRecord value) {
        try {
            logMapper.insert(value);
        } catch (RuntimeException exception) {
            // 审计库短暂异常不应改变已完成调用的业务响应，保留错误日志供运维处理。
            log.error("Invocation log persistence failed, traceId={}", value.traceId(), exception);
        }
    }

    public List<LogSummary> list(int limit) {
        return logMapper.findRecent(Math.min(Math.max(limit, 1), 200));
    }

    public LogDetail detail(String traceId) {
        LogDetail detail = logMapper.findByTraceId(traceId);
        if (detail == null) throw new BusinessException(HttpStatus.NOT_FOUND,
                        "IP-LOG-404", "调用日志不存在: " + traceId);
        return detail;
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
