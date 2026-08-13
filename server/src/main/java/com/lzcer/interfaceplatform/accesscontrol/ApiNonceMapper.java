package com.lzcer.interfaceplatform.accesscontrol;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.sql.Timestamp;

/**
 * HMAC 防重放 Nonce 的持久化操作。
 * 唯一键冲突由数据库返回，调用方据此识别重放请求。
 */
@Mapper
public interface ApiNonceMapper {

    int deleteExpired();

    int insert(@Param("clientId") long clientId, @Param("nonce") String nonce,
               @Param("expiresAt") Timestamp expiresAt);

    int deleteByClientId(@Param("clientId") long clientId);
}
