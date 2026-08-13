package com.lzcer.interfaceplatform.accesscontrol;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理用户的持久化边界。固定 SQL 定义在同名 XML 中，Service 不直接拼写数据库语句。
 */
@Mapper
public interface UserMapper {

    long countUsers();

    int insert(@Param("username") String username, @Param("passwordHash") String passwordHash,
               @Param("displayName") String displayName, @Param("role") String role,
               @Param("enabled") boolean enabled);

    UserRecord findById(@Param("id") long id);

    UserRecord findByUsername(@Param("username") String username);

    List<UserRecord> findAll();

    int updateLastLogin(@Param("id") long id);

    int updateProfileAndInvalidateTokens(@Param("id") long id, @Param("displayName") String displayName,
                                         @Param("role") String role, @Param("enabled") boolean enabled);

    int updatePasswordAndInvalidateTokens(@Param("id") long id, @Param("passwordHash") String passwordHash);

    int invalidateTokens(@Param("id") long id);

    int disableAndInvalidateTokens(@Param("id") long id);

    long countOtherEnabledAdministrators(@Param("excludedId") long excludedId);

    record UserRecord(Long id, String username, String passwordHash, String displayName, String role,
                      Boolean enabled, Long tokenVersion, LocalDateTime lastLoginAt,
                      LocalDateTime createdAt, LocalDateTime updatedAt) {
        UserPrincipal principal() {
            return new UserPrincipal(id, username, displayName, role, tokenVersion);
        }
    }
}
