package com.lzcer.interfaceplatform.datasource;

import com.lzcer.interfaceplatform.common.api.BusinessException;
import com.lzcer.interfaceplatform.common.security.CredentialCipher;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DatasourceService {

    private static final Map<String, String> DEFAULT_DRIVERS = Map.of(
            "MYSQL", "com.mysql.cj.jdbc.Driver",
            "POSTGRESQL", "org.postgresql.Driver",
            "SQL_SERVER", "com.microsoft.sqlserver.jdbc.SQLServerDriver",
            "H2", "org.h2.Driver"
    );

    private final JdbcClient jdbcClient;
    private final CredentialCipher cipher;
    private final DynamicDataSourceRegistry registry;

    public DatasourceService(JdbcClient jdbcClient, CredentialCipher cipher, DynamicDataSourceRegistry registry) {
        this.jdbcClient = jdbcClient;
        this.cipher = cipher;
        this.registry = registry;
    }

    public List<DatasourceView> list() {
        return jdbcClient.sql("""
                select id, datasource_code, datasource_name, db_type, jdbc_url, driver_class_name,
                       health_status, enabled, last_checked_at, updated_at
                  from ip_datasource order by updated_at desc
                """).query((rs, rowNum) -> new DatasourceView(rs.getLong("id"), rs.getString("datasource_code"),
                        rs.getString("datasource_name"), rs.getString("db_type"), rs.getString("jdbc_url"),
                        rs.getString("driver_class_name"), rs.getString("health_status"), rs.getBoolean("enabled"),
                        true, 0, rs.getObject("last_checked_at", LocalDateTime.class),
                        rs.getObject("updated_at", LocalDateTime.class))).list();
    }

    public DatasourceView get(long id) {
        return list().stream().filter(value -> value.id() == id).findFirst()
                .orElseThrow(() -> notFound(id));
    }

    @Transactional
    public DatasourceView create(DatasourceCommand command) {
        ValidatedCommand value = validate(command, true);
        jdbcClient.sql("""
                insert into ip_datasource(
                    datasource_code, datasource_name, db_type, jdbc_url, driver_class_name,
                    encrypted_username, encrypted_password, health_status, enabled
                ) values (:code, :name, :dbType, :jdbcUrl, :driver, :username, :password, 'UNKNOWN', :enabled)
                """).param("code", value.code()).param("name", value.name()).param("dbType", value.dbType())
                .param("jdbcUrl", value.jdbcUrl()).param("driver", value.driverClassName())
                .param("username", cipher.encrypt(value.username())).param("password", cipher.encrypt(value.password()))
                .param("enabled", value.enabled()).update();
        return findByCode(value.code());
    }

    @Transactional
    public DatasourceView update(long id, DatasourceCommand command) {
        StoredCredentials existing = stored(id);
        ValidatedCommand value = validate(command, false);
        // 编辑资料时用户名、密码为空表示“保持原凭证”，避免管理页回显不了密文而误清空连接信息。
        String username = value.username().isBlank() ? existing.encryptedUsername() : cipher.encrypt(value.username());
        String password = value.password().isBlank() ? existing.encryptedPassword() : cipher.encrypt(value.password());
        jdbcClient.sql("""
                update ip_datasource
                   set datasource_code = :code, datasource_name = :name, db_type = :dbType,
                       jdbc_url = :jdbcUrl, driver_class_name = :driver,
                       encrypted_username = :username, encrypted_password = :password,
                       health_status = 'UNKNOWN', enabled = :enabled, updated_at = current_timestamp
                 where id = :id
                """).param("code", value.code()).param("name", value.name()).param("dbType", value.dbType())
                .param("jdbcUrl", value.jdbcUrl()).param("driver", value.driverClassName())
                .param("username", username).param("password", password).param("enabled", value.enabled())
                .param("id", id).update();
        registry.invalidate(id);
        return get(id);
    }

    @Transactional
    public DatasourceView setEnabled(long id, boolean enabled) {
        int updated = jdbcClient.sql("update ip_datasource set enabled = :enabled, updated_at = current_timestamp where id = :id")
                .param("enabled", enabled).param("id", id).update();
        if (updated == 0) throw notFound(id);
        registry.invalidate(id);
        return get(id);
    }

    @Transactional
    public void delete(long id) {
        int deleted = jdbcClient.sql("delete from ip_datasource where id = :id").param("id", id).update();
        if (deleted == 0) throw notFound(id);
        registry.invalidate(id);
    }

    public ConnectionTestResult test(long id) {
        long started = System.nanoTime();
        try (Connection connection = dataSource(id, false).getConnection()) {
            connection.setReadOnly(true);
            if (!connection.isValid(3)) {
                throw new SQLException("Connection validation returned false");
            }
            long duration = elapsedMillis(started);
            updateHealth(id, "ONLINE");
            return new ConnectionTestResult(true, duration, "连接成功");
        } catch (SQLException | RuntimeException exception) {
            updateHealth(id, "OFFLINE");
            registry.invalidate(id);
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "IP-DS-TEST-001",
                    "数据源连接失败: " + rootMessage(exception));
        }
    }

    public DataSource dataSource(long id, boolean requireEnabled) {
        RuntimeConfig config = runtimeConfig(id);
        if (requireEnabled && !config.enabled()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "IP-DS-404", "数据源已停用");
        }
        return registry.get(config);
    }

    public RuntimeConfig runtimeConfig(long id) {
        // 凭证只在构建运行时连接池前解密，列表和管理接口绝不返回明文。
        return jdbcClient.sql("""
                select id, jdbc_url, driver_class_name, encrypted_username, encrypted_password, enabled
                  from ip_datasource where id = :id
                """).param("id", id).query((rs, rowNum) -> new RuntimeConfig(rs.getLong("id"),
                        rs.getString("jdbc_url"), rs.getString("driver_class_name"),
                        cipher.decrypt(rs.getString("encrypted_username")),
                        cipher.decrypt(rs.getString("encrypted_password")), rs.getBoolean("enabled")))
                .optional().orElseThrow(() -> notFound(id));
    }

    private ValidatedCommand validate(DatasourceCommand command, boolean creating) {
        String dbType = command.dbType().strip().toUpperCase(Locale.ROOT).replace(' ', '_');
        String driver = command.driverClassName() == null || command.driverClassName().isBlank()
                ? DEFAULT_DRIVERS.get(dbType) : command.driverClassName().strip();
        if (driver == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "IP-DS-001", "不支持的数据库类型: " + dbType);
        }
        String jdbcUrl = command.jdbcUrl().strip();
        if (!jdbcUrl.startsWith("jdbc:")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "IP-DS-002", "JDBC 地址必须以 jdbc: 开头");
        }
        String username = command.username() == null ? "" : command.username().strip();
        String password = command.password() == null ? "" : command.password();
        if (creating && (username.isBlank() || password.isBlank())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "IP-DS-003", "新建数据源必须填写用户名和密码");
        }
        return new ValidatedCommand(command.code().strip().toUpperCase(Locale.ROOT), command.name().strip(),
                dbType, jdbcUrl, driver, username, password, command.enabled());
    }

    private StoredCredentials stored(long id) {
        return jdbcClient.sql("select encrypted_username, encrypted_password from ip_datasource where id = :id")
                .param("id", id).query((rs, rowNum) -> new StoredCredentials(
                        rs.getString("encrypted_username"), rs.getString("encrypted_password")))
                .optional().orElseThrow(() -> notFound(id));
    }

    private DatasourceView findByCode(String code) {
        return list().stream().filter(item -> item.code().equals(code)).findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "IP-DS-004", "数据源保存后无法读取"));
    }

    private void updateHealth(long id, String status) {
        jdbcClient.sql("update ip_datasource set health_status = :status, last_checked_at = current_timestamp where id = :id")
                .param("status", status).param("id", id).update();
    }

    private long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private BusinessException notFound(long id) {
        return new BusinessException(HttpStatus.NOT_FOUND, "IP-DS-404", "数据源不存在: " + id);
    }

    public record DatasourceCommand(@NotBlank @Size(max = 80) String code,
                                    @NotBlank @Size(max = 160) String name,
                                    @NotBlank @Size(max = 40) String dbType,
                                    @NotBlank @Size(max = 1000) String jdbcUrl,
                                    @Size(max = 200) String driverClassName,
                                    @Size(max = 300) String username,
                                    @Size(max = 500) String password,
                                    boolean enabled) {}

    public record DatasourceView(long id, String code, String name, String dbType, String jdbcUrl,
                                 String driverClassName, String status, boolean enabled,
                                 boolean credentialConfigured, int poolUsage, LocalDateTime lastCheckedAt,
                                 LocalDateTime updatedAt) {}

    public record ConnectionTestResult(boolean success, long durationMs, String message) {}

    public record RuntimeConfig(long id, String jdbcUrl, String driverClassName,
                                String username, String password, boolean enabled) {}

    private record ValidatedCommand(String code, String name, String dbType, String jdbcUrl,
                                    String driverClassName, String username, String password, boolean enabled) {}

    private record StoredCredentials(String encryptedUsername, String encryptedPassword) {}
}
