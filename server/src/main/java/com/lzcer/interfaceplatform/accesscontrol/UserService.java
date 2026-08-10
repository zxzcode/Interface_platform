package com.lzcer.interfaceplatform.accesscontrol;

import com.lzcer.interfaceplatform.common.api.BusinessException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final Set<String> ROLES = Set.of("ADMIN", "OPERATOR", "VIEWER");
    private static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;
    private static final Duration LOGIN_LOCK_DURATION = Duration.ofMinutes(15);
    private final JdbcClient jdbcClient;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokenService;
    private final String bootstrapUsername;
    private final String bootstrapPassword;
    private final String bootstrapName;
    private final ConcurrentHashMap<String, LoginAttempt> failedLogins = new ConcurrentHashMap<>();

    public UserService(JdbcClient jdbcClient, PasswordEncoder passwordEncoder, JwtTokenService tokenService,
                       @Value("${platform.security.bootstrap-admin-username:admin}") String bootstrapUsername,
                       @Value("${platform.security.bootstrap-admin-password:}") String bootstrapPassword,
                       @Value("${platform.security.bootstrap-admin-name:系统管理员}") String bootstrapName) {
        this.jdbcClient = jdbcClient;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.bootstrapUsername = bootstrapUsername;
        this.bootstrapPassword = bootstrapPassword;
        this.bootstrapName = bootstrapName;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (jdbcClient.sql("select count(*) from ip_user").query(Long.class).single() > 0) return;
        if (bootstrapPassword == null || bootstrapPassword.isBlank()) {
            throw new IllegalStateException("No platform user exists. Set PLATFORM_ADMIN_PASSWORD to initialize the first administrator.");
        }
        validatePassword(bootstrapPassword);
        jdbcClient.sql("""
                insert into ip_user(username, password_hash, display_name, role, enabled)
                values (:username, :passwordHash, :displayName, 'ADMIN', true)
                """).param("username", normalizeUsername(bootstrapUsername))
                .param("passwordHash", passwordEncoder.encode(bootstrapPassword))
                .param("displayName", bootstrapName == null || bootstrapName.isBlank() ? "系统管理员" : bootstrapName.strip())
                .update();
        log.info("Initial platform administrator created: {}", normalizeUsername(bootstrapUsername));
    }

    public LoginView login(LoginCommand command) {
        String username = normalizeUsername(command.username());
        Instant now = Instant.now();
        if (isLocked(username, now)) throw invalidCredentials();
        UserRow user = findRowByUsername(username);
        if (user == null || !user.enabled() || !passwordEncoder.matches(command.password(), user.passwordHash())) {
            if (user != null) recordFailedLogin(username, now);
            throw invalidCredentials();
        }
        failedLogins.remove(username);
        jdbcClient.sql("update ip_user set last_login_at = current_timestamp where id = :id")
                .param("id", user.id()).update();
        UserPrincipal principal = user.principal();
        JwtTokenService.TokenResult token = tokenService.issue(principal);
        return new LoginView(token.accessToken(), "Bearer", token.expiresAt(), toView(user));
    }

    public UserPrincipal authenticateToken(String token) {
        JwtTokenService.JwtClaims claims = tokenService.verify(token);
        UserRow user = findRow(claims.userId());
        if (user == null || !user.enabled() || user.tokenVersion() != claims.tokenVersion()
                || !user.username().equals(claims.username()) || !user.role().equals(claims.role())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "IP-AUTH-004", "登录状态已失效，请重新登录");
        }
        return user.principal();
    }

    public List<UserView> list() {
        return jdbcClient.sql("""
                select id, username, password_hash, display_name, role, enabled, token_version,
                       last_login_at, created_at, updated_at from ip_user order by id
                """).query(UserService::mapRow).list().stream().map(UserService::toView).toList();
    }

    public UserView get(long id) {
        UserRow row = findRow(id);
        if (row == null) throw notFound(id);
        return toView(row);
    }

    @Transactional
    public UserView create(CreateUserCommand command) {
        String role = validateRole(command.role());
        validatePassword(command.password());
        jdbcClient.sql("""
                insert into ip_user(username, password_hash, display_name, role, enabled)
                values (:username, :passwordHash, :displayName, :role, :enabled)
                """).param("username", normalizeUsername(command.username()))
                .param("passwordHash", passwordEncoder.encode(command.password()))
                .param("displayName", command.displayName().strip()).param("role", role)
                .param("enabled", command.enabled()).update();
        UserRow row = findRowByUsername(normalizeUsername(command.username()));
        return toView(row);
    }

    @Transactional
    public UserView update(long id, UpdateUserCommand command, long operatorId) {
        UserRow current = requireRow(id);
        String role = validateRole(command.role());
        if (id == operatorId && (!command.enabled() || !"ADMIN".equals(role))) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "IP-USER-002", "不能禁用当前账号或移除自己的管理员角色");
        }
        if ("ADMIN".equals(current.role()) && (!command.enabled() || !"ADMIN".equals(role))) requireAnotherAdmin(id);
        jdbcClient.sql("""
                update ip_user set display_name = :displayName, role = :role, enabled = :enabled,
                       token_version = token_version + 1, updated_at = current_timestamp where id = :id
                """).param("displayName", command.displayName().strip()).param("role", role)
                .param("enabled", command.enabled()).param("id", id).update();
        return get(id);
    }

    @Transactional
    public void resetPassword(long id, PasswordCommand command) {
        requireRow(id);
        validatePassword(command.password());
        jdbcClient.sql("""
                update ip_user set password_hash = :passwordHash, token_version = token_version + 1,
                       updated_at = current_timestamp where id = :id
                """).param("passwordHash", passwordEncoder.encode(command.password())).param("id", id).update();
    }

    @Transactional
    public void changePassword(long id, ChangePasswordCommand command) {
        UserRow user = requireRow(id);
        if (!passwordEncoder.matches(command.currentPassword(), user.passwordHash())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "IP-USER-003", "当前密码错误");
        }
        resetPassword(id, new PasswordCommand(command.newPassword()));
    }

    @Transactional
    public void logout(long id) {
        jdbcClient.sql("update ip_user set token_version = token_version + 1 where id = :id")
                .param("id", id).update();
    }

    @Transactional
    public void delete(long id, long operatorId) {
        UserRow user = requireRow(id);
        if (id == operatorId) throw new BusinessException(HttpStatus.BAD_REQUEST, "IP-USER-004", "不能删除当前登录账号");
        if ("ADMIN".equals(user.role())) requireAnotherAdmin(id);
        jdbcClient.sql("""
                update ip_user set enabled = false, token_version = token_version + 1,
                       updated_at = current_timestamp where id = :id
                """).param("id", id).update();
    }

    private void requireAnotherAdmin(long excludedId) {
        long count = jdbcClient.sql("select count(*) from ip_user where role = 'ADMIN' and enabled = true and id <> :id")
                .param("id", excludedId).query(Long.class).single();
        if (count == 0) throw new BusinessException(HttpStatus.BAD_REQUEST, "IP-USER-005", "必须至少保留一个启用的管理员");
    }

    private UserRow requireRow(long id) {
        UserRow row = findRow(id);
        if (row == null) throw notFound(id);
        return row;
    }

    private UserRow findRow(long id) {
        return jdbcClient.sql("""
                select id, username, password_hash, display_name, role, enabled, token_version,
                       last_login_at, created_at, updated_at from ip_user where id = :id
                """).param("id", id).query(UserService::mapRow).optional().orElse(null);
    }

    private UserRow findRowByUsername(String username) {
        return jdbcClient.sql("""
                select id, username, password_hash, display_name, role, enabled, token_version,
                       last_login_at, created_at, updated_at from ip_user where username = :username
                """).param("username", username).query(UserService::mapRow).optional().orElse(null);
    }

    private static UserRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new UserRow(rs.getLong("id"), rs.getString("username"), rs.getString("password_hash"),
                rs.getString("display_name"), rs.getString("role"), rs.getBoolean("enabled"),
                rs.getLong("token_version"), rs.getObject("last_login_at", LocalDateTime.class),
                rs.getObject("created_at", LocalDateTime.class), rs.getObject("updated_at", LocalDateTime.class));
    }

    private static UserView toView(UserRow row) {
        return new UserView(row.id(), row.username(), row.displayName(), row.role(), row.enabled(),
                row.lastLoginAt(), row.createdAt(), row.updatedAt());
    }

    private String validateRole(String rawRole) {
        String role = rawRole == null ? "" : rawRole.strip().toUpperCase(Locale.ROOT);
        if (!ROLES.contains(role)) throw new BusinessException(HttpStatus.BAD_REQUEST, "IP-USER-006", "用户角色无效");
        return role;
    }

    private String normalizeUsername(String username) {
        String value = username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
        if (!value.matches("[a-z0-9._-]{3,80}")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "IP-USER-007", "用户名只能包含小写字母、数字、点、下划线和短横线，长度3-80");
        }
        return value;
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 10 || password.length() > 72 || password.codePointCount(0, password.length()) != password.length()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "IP-USER-008", "密码长度需为10-72位，并至少包含三类字符");
        }
        int categories = 0;
        if (password.chars().anyMatch(Character::isUpperCase)) categories++;
        if (password.chars().anyMatch(Character::isLowerCase)) categories++;
        if (password.chars().anyMatch(Character::isDigit)) categories++;
        if (password.chars().anyMatch(value -> !Character.isLetterOrDigit(value))) categories++;
        if (categories < 3) throw new BusinessException(HttpStatus.BAD_REQUEST, "IP-USER-008", "密码长度需为10-72位，并至少包含三类字符");
    }

    private boolean isLocked(String username, Instant now) {
        LoginAttempt attempt = failedLogins.get(username);
        if (attempt == null) return false;
        if (attempt.lockedUntil() != null && attempt.lockedUntil().isAfter(now)) return true;
        if (attempt.lockedUntil() != null || attempt.failedCount() > 0) failedLogins.remove(username, attempt);
        return false;
    }

    private void recordFailedLogin(String username, Instant now) {
        failedLogins.compute(username, (key, previous) -> {
            int failures = previous == null || previous.lockedUntil() != null && !previous.lockedUntil().isAfter(now)
                    ? 1 : previous.failedCount() + 1;
            return failures >= MAX_FAILED_LOGIN_ATTEMPTS
                    ? new LoginAttempt(failures, now.plus(LOGIN_LOCK_DURATION))
                    : new LoginAttempt(failures, null);
        });
    }

    private BusinessException invalidCredentials() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "IP-AUTH-001", "用户名或密码错误");
    }

    private BusinessException notFound(long id) {
        return new BusinessException(HttpStatus.NOT_FOUND, "IP-USER-404", "用户不存在: " + id);
    }

    private record UserRow(long id, String username, String passwordHash, String displayName, String role,
                           boolean enabled, long tokenVersion, LocalDateTime lastLoginAt,
                           LocalDateTime createdAt, LocalDateTime updatedAt) {
        UserPrincipal principal() { return new UserPrincipal(id, username, displayName, role, tokenVersion); }
    }

    private record LoginAttempt(int failedCount, Instant lockedUntil) {}

    public record LoginCommand(@NotBlank String username, @NotBlank String password) {}
    public record LoginView(String accessToken, String tokenType, Instant expiresAt, UserView user) {}
    public record CreateUserCommand(
            @NotBlank @Size(min = 3, max = 80) @Pattern(regexp = "[A-Za-z0-9._-]+") String username,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 120) String displayName,
            @NotBlank String role, boolean enabled) {}
    public record UpdateUserCommand(@NotBlank @Size(max = 120) String displayName,
                                    @NotBlank String role, boolean enabled) {}
    public record PasswordCommand(@NotBlank @Size(min = 8, max = 72) String password) {}
    public record ChangePasswordCommand(@NotBlank String currentPassword,
                                        @NotBlank @Size(min = 8, max = 72) String newPassword) {}
    public record UserView(long id, String username, String displayName, String role, boolean enabled,
                           LocalDateTime lastLoginAt, LocalDateTime createdAt, LocalDateTime updatedAt) {}
}
