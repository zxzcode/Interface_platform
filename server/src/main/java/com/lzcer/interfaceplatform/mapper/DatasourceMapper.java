package com.lzcer.interfaceplatform.mapper;

import com.lzcer.interfaceplatform.model.datasource.DatasourceModels;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** Fixed datasource configuration persistence; SQL lives in the companion XML mapper. */
@Mapper
public interface DatasourceMapper {
    List<DatasourceModels.DatasourceView> findAll();
    DatasourceModels.DatasourceView findByCode(@Param("code") String code);
    DatasourceModels.DatasourceView findById(@Param("id") long id);
    RuntimeConfigRow findRuntimeConfig(@Param("id") long id);
    CredentialsRow findCredentials(@Param("id") long id);
    int insert(@Param("code") String code, @Param("name") String name, @Param("dbType") String dbType, @Param("jdbcUrl") String jdbcUrl, @Param("driver") String driver, @Param("username") String username, @Param("password") String password, @Param("enabled") boolean enabled);
    int update(@Param("id") long id, @Param("code") String code, @Param("name") String name, @Param("dbType") String dbType, @Param("jdbcUrl") String jdbcUrl, @Param("driver") String driver, @Param("username") String username, @Param("password") String password, @Param("enabled") boolean enabled);
    int updateEnabled(@Param("id") long id, @Param("enabled") boolean enabled);
    int updateHealth(@Param("id") long id, @Param("status") String status);
    int delete(@Param("id") long id);

    record RuntimeConfigRow(Long id, String jdbcUrl, String driverClassName, String encryptedUsername, String encryptedPassword, Boolean enabled) {}
    record CredentialsRow(String encryptedUsername, String encryptedPassword) {}
}
