package com.lzcer.interfaceplatform.mapper;

import com.lzcer.interfaceplatform.model.sqlapi.SqlApiModels;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface SqlApiMapper {
    List<SqlApiModels.SqlApiView> findAll();
    SqlApiModels.SqlApiView findById(@Param("id") long id);
    SqlApiModels.SqlApiView findByCode(@Param("code") String code);
    SqlApiModels.RuntimeConfig findEnabledByPathAndMethod(@Param("path") String path, @Param("method") String method);
    SqlApiModels.RuntimeConfig findRuntimeConfig(@Param("id") long id, @Param("requireEnabled") boolean requireEnabled);
    int insert(@Param("code") String code, @Param("name") String name, @Param("description") String description, @Param("path") String path, @Param("method") String method, @Param("datasourceId") long datasourceId, @Param("sql") String sql, @Param("timeout") int timeout, @Param("maxRows") int maxRows, @Param("enabled") boolean enabled);
    int update(@Param("id") long id, @Param("code") String code, @Param("name") String name, @Param("description") String description, @Param("path") String path, @Param("method") String method, @Param("datasourceId") long datasourceId, @Param("sql") String sql, @Param("timeout") int timeout, @Param("maxRows") int maxRows, @Param("enabled") boolean enabled);
    int updateEnabled(@Param("id") long id, @Param("enabled") boolean enabled);
    int delete(@Param("id") long id);
}
