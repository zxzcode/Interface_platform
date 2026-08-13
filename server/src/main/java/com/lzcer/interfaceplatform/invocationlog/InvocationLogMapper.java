package com.lzcer.interfaceplatform.invocationlog;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 调用审计日志的数据访问边界；日志写入失败由 Service 降级处理，不影响接口响应。 */
@Mapper
public interface InvocationLogMapper {
    int insert(@Param("value") InvocationLogService.InvocationRecord value);
    List<InvocationLogService.LogSummary> findRecent(@Param("limit") int limit);
    InvocationLogService.LogDetail findByTraceId(@Param("traceId") String traceId);
}
