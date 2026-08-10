package com.lzcer.interfaceplatform.sqlquery;

import com.lzcer.interfaceplatform.common.api.BusinessException;
import com.lzcer.interfaceplatform.datasource.DatasourceService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class SqlApiService {

    private static final Set<String> METHODS = Set.of("GET", "POST");
    private final JdbcClient jdbcClient;
    private final DatasourceService datasourceService;
    private final ReadOnlySqlValidator validator;
    private final int maxRowsCeiling;
    private final int timeoutCeiling;

    public SqlApiService(JdbcClient jdbcClient, DatasourceService datasourceService,
                         ReadOnlySqlValidator validator,
                         @Value("${platform.sql.max-rows-ceiling:5000}") int maxRowsCeiling,
                         @Value("${platform.sql.timeout-seconds-ceiling:60}") int timeoutCeiling) {
        this.jdbcClient = jdbcClient;
        this.datasourceService = datasourceService;
        this.validator = validator;
        this.maxRowsCeiling = maxRowsCeiling;
        this.timeoutCeiling = timeoutCeiling;
    }

    public List<SqlApiView> list() {
        return jdbcClient.sql("""
                select a.id, a.api_code, a.api_name, a.description, a.api_path, a.http_method,
                       a.datasource_id, d.datasource_name, a.select_sql, a.timeout_seconds,
                       a.max_rows, a.enabled, a.updated_at
                  from ip_sql_api a
                  join ip_datasource d on d.id = a.datasource_id
                 order by a.updated_at desc
                """).query(SqlApiService::mapView).list();
    }

    public SqlApiView get(long id) {
        return list().stream().filter(value -> value.id() == id).findFirst()
                .orElseThrow(() -> notFound(id));
    }

    @Transactional
    public SqlApiView create(SqlApiCommand command) {
        ValidatedCommand value = validate(command);
        jdbcClient.sql("""
                insert into ip_sql_api(
                    api_code, api_name, description, api_path, http_method, datasource_id,
                    select_sql, timeout_seconds, max_rows, enabled
                ) values (:code, :name, :description, :path, :method, :datasourceId,
                          :sql, :timeout, :maxRows, :enabled)
                """).param("code", value.code()).param("name", value.name())
                .param("description", value.description()).param("path", value.path())
                .param("method", value.method()).param("datasourceId", value.datasourceId())
                .param("sql", value.sql()).param("timeout", value.timeoutSeconds())
                .param("maxRows", value.maxRows()).param("enabled", value.enabled()).update();
        return findByCode(value.code());
    }

    @Transactional
    public SqlApiView update(long id, SqlApiCommand command) {
        requireExists(id);
        ValidatedCommand value = validate(command);
        jdbcClient.sql("""
                update ip_sql_api
                   set api_code = :code, api_name = :name, description = :description,
                       api_path = :path, http_method = :method, datasource_id = :datasourceId,
                       select_sql = :sql, timeout_seconds = :timeout, max_rows = :maxRows,
                       enabled = :enabled, updated_at = current_timestamp
                 where id = :id
                """).param("code", value.code()).param("name", value.name())
                .param("description", value.description()).param("path", value.path())
                .param("method", value.method()).param("datasourceId", value.datasourceId())
                .param("sql", value.sql()).param("timeout", value.timeoutSeconds())
                .param("maxRows", value.maxRows()).param("enabled", value.enabled()).param("id", id).update();
        return get(id);
    }

    @Transactional
    public SqlApiView setEnabled(long id, boolean enabled) {
        int updated = jdbcClient.sql("update ip_sql_api set enabled = :enabled, updated_at = current_timestamp where id = :id")
                .param("enabled", enabled).param("id", id).update();
        if (updated == 0) throw notFound(id);
        return get(id);
    }

    @Transactional
    public void delete(long id) {
        int deleted = jdbcClient.sql("delete from ip_sql_api where id = :id").param("id", id).update();
        if (deleted == 0) throw notFound(id);
    }

    public QueryResult test(long id, Map<String, Object> parameters) {
        return execute(runtimeConfig(id, false), parameters);
    }

    public Optional<RuntimeConfig> resolve(String path, String method) {
        return jdbcClient.sql("""
                select a.id, a.api_code, a.api_name, a.api_path, a.http_method, a.datasource_id,
                       d.datasource_name, a.select_sql, a.timeout_seconds, a.max_rows
                  from ip_sql_api a join ip_datasource d on d.id = a.datasource_id
                 where a.api_path = :path and a.http_method = :method and a.enabled = true and d.enabled = true
                """).param("path", path).param("method", method.toUpperCase(Locale.ROOT))
                .query(SqlApiService::mapRuntime).optional();
    }

    public QueryResult execute(RuntimeConfig config, Map<String, Object> rawParameters) {
        Map<String, Object> parameters = rawParameters == null ? Collections.emptyMap() : rawParameters;
        ReadOnlySqlValidator.ValidatedSql validated = validator.validate(config.sql());
        List<String> missing = validated.parameters().stream().filter(name -> !parameters.containsKey(name)).toList();
        if (!missing.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "IP-SQL-002", "缺少查询参数: " + missing);
        }
        JdbcTemplate template = new JdbcTemplate(datasourceService.dataSource(config.datasourceId(), true));
        template.setQueryTimeout(Math.min(config.timeoutSeconds(), timeoutCeiling));
        template.setMaxRows(Math.min(config.maxRows(), maxRowsCeiling));
        try {
            List<Map<String, Object>> rows = new NamedParameterJdbcTemplate(template)
                    .queryForList(validated.sql(), parameters);
            return new QueryResult(rows.size(), Math.min(config.maxRows(), maxRowsCeiling), rows);
        } catch (DataAccessException exception) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "IP-SQL-003",
                    "SQL 查询执行失败: " + rootMessage(exception));
        }
    }

    private RuntimeConfig runtimeConfig(long id, boolean requireEnabled) {
        String enabledClause = requireEnabled ? " and a.enabled = true and d.enabled = true" : "";
        return jdbcClient.sql("""
                select a.id, a.api_code, a.api_name, a.api_path, a.http_method, a.datasource_id,
                       d.datasource_name, a.select_sql, a.timeout_seconds, a.max_rows
                  from ip_sql_api a join ip_datasource d on d.id = a.datasource_id
                 where a.id = :id
                """ + enabledClause).param("id", id).query(SqlApiService::mapRuntime)
                .optional().orElseThrow(() -> notFound(id));
    }

    private ValidatedCommand validate(SqlApiCommand command) {
        String method = command.method().strip().toUpperCase(Locale.ROOT);
        if (!METHODS.contains(method)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "IP-SQL-004", "SQL API 只支持 GET 或 POST");
        }
        String path = normalizePath(command.path());
        ReadOnlySqlValidator.ValidatedSql sql = validator.validate(command.sql());
        datasourceService.get(command.datasourceId());
        if (command.maxRows() > maxRowsCeiling || command.timeoutSeconds() > timeoutCeiling) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "IP-SQL-005",
                    "最大行数或超时时间超过平台安全上限");
        }
        return new ValidatedCommand(command.code().strip().toUpperCase(Locale.ROOT), command.name().strip(),
                blankToNull(command.description()), path, method, command.datasourceId(), sql.sql(),
                command.timeoutSeconds(), command.maxRows(), command.enabled());
    }

    private String normalizePath(String rawPath) {
        String path = rawPath.strip();
        if (!path.startsWith("/open-api/sql/") || path.contains("?") || path.contains("#") || path.contains("..")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "IP-SQL-006",
                    "SQL API 路径必须以 /open-api/sql/ 开头");
        }
        return path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    private void requireExists(long id) {
        if (jdbcClient.sql("select count(*) from ip_sql_api where id = :id").param("id", id)
                .query(Long.class).single() == 0) throw notFound(id);
    }

    private SqlApiView findByCode(String code) {
        return list().stream().filter(value -> value.code().equals(code)).findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "IP-SQL-007", "SQL API 保存后无法读取"));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private BusinessException notFound(long id) {
        return new BusinessException(HttpStatus.NOT_FOUND, "IP-SQL-404", "SQL API 不存在或已停用: " + id);
    }

    private static SqlApiView mapView(ResultSet rs, int rowNum) throws SQLException {
        return new SqlApiView(rs.getLong("id"), rs.getString("api_code"), rs.getString("api_name"),
                rs.getString("description"), rs.getString("api_path"), rs.getString("http_method"),
                rs.getLong("datasource_id"), rs.getString("datasource_name"), rs.getString("select_sql"),
                rs.getInt("timeout_seconds"), rs.getInt("max_rows"), rs.getBoolean("enabled"),
                rs.getObject("updated_at", LocalDateTime.class));
    }

    private static RuntimeConfig mapRuntime(ResultSet rs, int rowNum) throws SQLException {
        return new RuntimeConfig(rs.getLong("id"), rs.getString("api_code"), rs.getString("api_name"),
                rs.getString("api_path"), rs.getString("http_method"), rs.getLong("datasource_id"),
                rs.getString("datasource_name"), rs.getString("select_sql"),
                rs.getInt("timeout_seconds"), rs.getInt("max_rows"));
    }

    public record SqlApiCommand(
            @NotBlank @Size(max = 80) @Pattern(regexp = "[A-Za-z0-9_-]+") String code,
            @NotBlank @Size(max = 160) String name,
            @Size(max = 500) String description,
            @NotBlank @Size(max = 300) String path,
            @NotBlank String method,
            @NotNull Long datasourceId,
            @NotBlank String sql,
            @Min(1) @Max(60) int timeoutSeconds,
            @Min(1) @Max(5000) int maxRows,
            boolean enabled) {}

    public record SqlApiView(long id, String code, String name, String description, String path, String method,
                             long datasourceId, String datasourceName, String sql, int timeoutSeconds,
                             int maxRows, boolean enabled, LocalDateTime updatedAt) {}

    public record RuntimeConfig(long id, String code, String name, String path, String method,
                                long datasourceId, String datasourceName, String sql,
                                int timeoutSeconds, int maxRows) {}

    public record QueryResult(int rowCount, int maxRows, List<Map<String, Object>> rows) {}

    private record ValidatedCommand(String code, String name, String description, String path, String method,
                                    long datasourceId, String sql, int timeoutSeconds, int maxRows,
                                    boolean enabled) {}
}
