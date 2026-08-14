package com.lzcer.interfaceplatform.mapper;

import com.lzcer.interfaceplatform.model.invocation.InvocationLogModels;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 调用审计日志的数据访问边界；日志写入失败由 Service 降级处理，不影响接口响应。 */
@Mapper
public interface InvocationLogMapper {
    int insert(@Param("value") InvocationLogModels.InvocationRecord value);
    List<InvocationLogModels.LogSummary> findRecent(@Param("limit") int limit);
    InvocationLogModels.LogDetail findByTraceId(@Param("traceId") String traceId);
}
