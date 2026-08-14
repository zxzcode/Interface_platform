package com.lzcer.interfaceplatform.service;

import com.lzcer.interfaceplatform.common.api.BusinessException;
import com.lzcer.interfaceplatform.mapper.InvocationLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import static com.lzcer.interfaceplatform.model.invocation.InvocationLogModels.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class InvocationLogService {

    private final InvocationLogMapper logMapper;

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

}
