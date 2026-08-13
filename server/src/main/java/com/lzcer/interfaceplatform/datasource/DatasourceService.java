package com.lzcer.interfaceplatform.datasource;

import com.lzcer.interfaceplatform.common.api.BusinessException;
import com.lzcer.interfaceplatform.common.security.CredentialCipher;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
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
            "ORACLE", "oracle.jdbc.OracleDriver",
            "SAP_HANA", "com.sap.db.jdbc.Driver",
            "H2", "org.h2.Driver"
    );

    private final DatasourceMapper datasourceMapper;
    private final CredentialCipher cipher;
    private final DynamicDataSourceRegistry registry;

    public DatasourceService(DatasourceMapper datasourceMapper, CredentialCipher cipher, DynamicDataSourceRegistry registry) {
        this.datasourceMapper = datasourceMapper;
        this.cipher = cipher;
        this.registry = registry;
    }

    public List<DatasourceView> list() {
        return datasourceMapper.findAll();
    }

    public DatasourceView get(long id) {
        DatasourceView value = datasourceMapper.findById(id);
        if (value == null) throw notFound(id);
        return value;
    }

    @Transactional
    public DatasourceView create(DatasourceCommand command) {
        ValidatedCommand value = validate(command, true);
        datasourceMapper.insert(value.code(), value.name(), value.dbType(), value.jdbcUrl(), value.driverClassName(),
                cipher.encrypt(value.username()), cipher.encrypt(value.password()), value.enabled());
        return findByCode(value.code());
    }

    @Transactional
    public DatasourceView update(long id, DatasourceCommand command) {
        StoredCredentials existing = stored(id);
        ValidatedCommand value = validate(command, false);
        // 编辑资料时用户名、密码为空表示“保持原凭证”，避免管理页回显不了密文而误清空连接信息。
        String username = value.username().isBlank() ? existing.encryptedUsername() : cipher.encrypt(value.username());
        String password = value.password().isBlank() ? existing.encryptedPassword() : cipher.encrypt(value.password());
        datasourceMapper.update(id, value.code(), value.name(), value.dbType(), value.jdbcUrl(), value.driverClassName(),
                username, password, value.enabled());
        registry.invalidate(id);
        return get(id);
    }

    @Transactional
    public DatasourceView setEnabled(long id, boolean enabled) {
        int updated = datasourceMapper.updateEnabled(id, enabled);
        if (updated == 0) throw notFound(id);
        registry.invalidate(id);
        return get(id);
    }

    @Transactional
    public void delete(long id) {
        int deleted = datasourceMapper.delete(id);
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
        DatasourceMapper.RuntimeConfigRow row = datasourceMapper.findRuntimeConfig(id);
        if (row == null) throw notFound(id);
        return new RuntimeConfig(row.id(), row.jdbcUrl(), row.driverClassName(),
                cipher.decrypt(row.encryptedUsername()), cipher.decrypt(row.encryptedPassword()), row.enabled());
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
        DatasourceMapper.CredentialsRow row = datasourceMapper.findCredentials(id);
        if (row == null) throw notFound(id);
        return new StoredCredentials(row.encryptedUsername(), row.encryptedPassword());
    }

    private DatasourceView findByCode(String code) {
        return list().stream().filter(item -> item.code().equals(code)).findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "IP-DS-004", "数据源保存后无法读取"));
    }

    private void updateHealth(long id, String status) {
        datasourceMapper.updateHealth(id, status);
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
