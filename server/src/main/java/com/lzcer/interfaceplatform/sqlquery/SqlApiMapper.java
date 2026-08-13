package com.lzcer.interfaceplatform.sqlquery;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface SqlApiMapper {
    List<SqlApiService.SqlApiView> findAll();
    SqlApiService.SqlApiView findById(@Param("id") long id);
    SqlApiService.SqlApiView findByCode(@Param("code") String code);
    SqlApiService.RuntimeConfig findEnabledByPathAndMethod(@Param("path") String path, @Param("method") String method);
    SqlApiService.RuntimeConfig findRuntimeConfig(@Param("id") long id, @Param("requireEnabled") boolean requireEnabled);
    int insert(@Param("code") String code, @Param("name") String name, @Param("description") String description, @Param("path") String path, @Param("method") String method, @Param("datasourceId") long datasourceId, @Param("sql") String sql, @Param("timeout") int timeout, @Param("maxRows") int maxRows, @Param("enabled") boolean enabled);
    int update(@Param("id") long id, @Param("code") String code, @Param("name") String name, @Param("description") String description, @Param("path") String path, @Param("method") String method, @Param("datasourceId") long datasourceId, @Param("sql") String sql, @Param("timeout") int timeout, @Param("maxRows") int maxRows, @Param("enabled") boolean enabled);
    int updateEnabled(@Param("id") long id, @Param("enabled") boolean enabled);
    int delete(@Param("id") long id);
}
