package com.lzcer.interfaceplatform.datasource;

import com.lzcer.interfaceplatform.service.DatasourceService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DynamicDataSourceRegistry {

    private final Map<Long, HikariDataSource> pools = new ConcurrentHashMap<>();

    public DataSource get(DatasourceService.RuntimeConfig config) {
        // 同一数据源配置复用一个小型连接池；配置变更或停用时由 invalidate 主动关闭旧连接。
        return pools.computeIfAbsent(config.id(), ignored -> createPool(config));
    }

    public void invalidate(long datasourceId) {
        HikariDataSource pool = pools.remove(datasourceId);
        if (pool != null) {
            pool.close();
        }
    }

    private HikariDataSource createPool(DatasourceService.RuntimeConfig value) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("interface-platform-ds-" + value.id());
        config.setJdbcUrl(value.jdbcUrl());
        config.setDriverClassName(value.driverClassName());
        config.setUsername(value.username());
        config.setPassword(value.password());
        // SQL API 第一阶段只读；连接池级别再加一道保护，不能只依赖 SQL 文本校验。
        config.setReadOnly(true);
        config.setAutoCommit(true);
        config.setMaximumPoolSize(3);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(5000);
        config.setValidationTimeout(3000);
        config.setIdleTimeout(60000);
        config.setMaxLifetime(300000);
        config.setInitializationFailTimeout(-1);
        return new HikariDataSource(config);
    }

    @PreDestroy
    public void close() {
        pools.values().forEach(HikariDataSource::close);
        pools.clear();
    }
}
