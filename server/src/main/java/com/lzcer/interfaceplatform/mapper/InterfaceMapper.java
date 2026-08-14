package com.lzcer.interfaceplatform.mapper;
import com.lzcer.interfaceplatform.model.interfacecatalog.InterfaceModels;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface InterfaceMapper {
    List<InterfaceModels.InterfaceView> findAll();
    InterfaceModels.InterfaceView findById(@Param("id") long id);
    InterfaceModels.InterfaceView findByCode(@Param("code") String code);
    List<InterfaceModels.SystemOption> findSystemOptions();
    String findSystemBaseUrl(@Param("id") long id);
    RouteRow findEnabledByPathAndMethod(@Param("path") String path, @Param("method") String method);
    RouteRow findRuntimeConfig(@Param("id") long id);
    int insert(@Param("code") String code, @Param("name") String name, @Param("description") String description, @Param("sourceId") long sourceId, @Param("targetId") long targetId, @Param("method") String method, @Param("path") String path, @Param("targetUrl") String targetUrl, @Param("connectTimeout") int connectTimeout, @Param("readTimeout") int readTimeout, @Param("enabled") boolean enabled);
    int update(@Param("id") long id, @Param("code") String code, @Param("name") String name, @Param("description") String description, @Param("sourceId") long sourceId, @Param("targetId") long targetId, @Param("method") String method, @Param("path") String path, @Param("targetUrl") String targetUrl, @Param("connectTimeout") int connectTimeout, @Param("readTimeout") int readTimeout, @Param("enabled") boolean enabled);
    int updateEnabled(@Param("id") long id, @Param("enabled") boolean enabled);
    int delete(@Param("id") long id);
    int deleteHttpPermissions(@Param("code") String code);

    record RouteRow(Long id, String code, String name, String targetSystem, String targetStatus, String targetUrl,
                    Integer connectTimeoutMs, Integer readTimeoutMs, String method, String path) {}
}
