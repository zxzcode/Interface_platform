package com.lzcer.interfaceplatform.interfacecatalog;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 系统档案的固定数据访问操作，SQL 统一维护在 XML。 */
@Mapper
public interface SystemMapper {
    List<SystemRow> findAll();
    SystemRow findById(@Param("id") long id);
    int insert(@Param("code") String code, @Param("name") String name, @Param("baseUrl") String baseUrl, @Param("status") String status);
    int update(@Param("id") long id, @Param("code") String code, @Param("name") String name, @Param("baseUrl") String baseUrl, @Param("status") String status);
    int delete(@Param("id") long id);
    List<String> findReferencedTargetUrls(@Param("systemId") long systemId);

    record SystemRow(Long id, String code, String name, String baseUrl, String status,
                     LocalDateTime createdAt, LocalDateTime updatedAt) {}
}
