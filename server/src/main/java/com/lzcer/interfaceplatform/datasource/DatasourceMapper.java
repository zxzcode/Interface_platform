package com.lzcer.interfaceplatform.datasource;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** Fixed datasource configuration persistence; SQL lives in the companion XML mapper. */
@Mapper
public interface DatasourceMapper {
    List<DatasourceService.DatasourceView> findAll();
    DatasourceService.DatasourceView findByCode(@Param("code") String code);
    DatasourceService.DatasourceView findById(@Param("id") long id);
    RuntimeConfigRow findRuntimeConfig(@Param("id") long id);
    CredentialsRow findCredentials(@Param("id") long id);
    int insert(@Param("code") String code, @Param("name") String name, @Param("dbType") String dbType, @Param("jdbcUrl") String jdbcUrl, @Param("driver") String driver, @Param("username") String username, @Param("password") String password, @Param("enabled") boolean enabled);
    int update(@Param("id") long id, @Param("code") String code, @Param("name") String name, @Param("dbType") String dbType, @Param("jdbcUrl") String jdbcUrl, @Param("driver") String driver, @Param("username") String username, @Param("password") String password, @Param("enabled") boolean enabled);
    int updateEnabled(@Param("id") long id, @Param("enabled") boolean enabled);
    int updateHealth(@Param("id") long id, @Param("status") String status);
    int delete(@Param("id") long id);

    record RuntimeConfigRow(long id, String jdbcUrl, String driverClassName, String encryptedUsername, String encryptedPassword, boolean enabled) {}
    record CredentialsRow(String encryptedUsername, String encryptedPassword) {}
}
