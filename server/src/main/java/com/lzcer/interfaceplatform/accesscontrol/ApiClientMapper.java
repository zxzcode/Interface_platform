package com.lzcer.interfaceplatform.accesscontrol;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ApiClientMapper {
    List<ClientRow> findAll();
    ClientRow findById(@Param("id") long id);
    ClientRow findByAppKey(@Param("appKey") String appKey);
    SecretRow findEnabledSecretByAppKey(@Param("appKey") String appKey);
    List<ApiClientService.Permission> findPermissions(@Param("clientId") long clientId);
    int insert(@Param("code") String code, @Param("name") String name, @Param("appKey") String appKey, @Param("secret") String secret, @Param("enabled") boolean enabled);
    int update(@Param("id") long id, @Param("name") String name, @Param("enabled") boolean enabled);
    int updateSecret(@Param("id") long id, @Param("secret") String secret);
    int delete(@Param("id") long id);
    long countPermission(@Param("clientId") long clientId, @Param("routeType") String routeType, @Param("resourceCode") String resourceCode);
    long countResource(@Param("routeType") String routeType, @Param("code") String code);
    int deletePermissions(@Param("clientId") long clientId);
    int insertPermission(@Param("clientId") long clientId, @Param("routeType") String routeType, @Param("resourceCode") String resourceCode);
    record SecretRow(long id, String code, String name, String appKey, String encryptedSecret) {}
    record ClientRow(long id, String code, String name, String appKey, boolean enabled, java.time.LocalDateTime createdAt, java.time.LocalDateTime updatedAt) {}
}
