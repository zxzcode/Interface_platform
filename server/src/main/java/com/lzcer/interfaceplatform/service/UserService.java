package com.lzcer.interfaceplatform.service;

import com.lzcer.interfaceplatform.common.api.BusinessException;
import com.lzcer.interfaceplatform.accesscontrol.UserPrincipal;
import com.lzcer.interfaceplatform.mapper.UserMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.lzcer.interfaceplatform.model.user.UserModels.*;

@Service
@Slf4j
public class UserService implements ApplicationRunner {

    private static final Set<String> ROLES = Set.of("ADMIN", "OPERATOR", "VIEWER");
    private static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;
    private static final Duration LOGIN_LOCK_DURATION = Duration.ofMinutes(15);
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokenService;
    private final String bootstrapUsername;
    private final String bootstrapPassword;
    private final String bootstrapName;
    private final ConcurrentHashMap<String, LoginAttempt> failedLogins = new ConcurrentHashMap<>();

    public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtTokenService tokenService,
                       @Value("${platform.security.bootstrap-admin-username:admin}") String bootstrapUsername,
                       @Value("${platform.security.bootstrap-admin-password:}") String bootstrapPassword,
                       @Value("${platform.security.bootstrap-admin-name:系统管理员}") String bootstrapName) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.bootstrapUsername = bootstrapUsername;
        this.bootstrapPassword = bootstrapPassword;
        this.bootstrapName = bootstrapName;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // 仅在用户表为空时创建首个管理员，后续重启绝不覆盖人工维护的账号或密码。
        if (userMapper.countUsers() > 0) return;
        if (bootstrapPassword == null || bootstrapPassword.isBlank()) {
            throw new IllegalStateException("No platform user exists. Set PLATFORM_ADMIN_PASSWORD to initialize the first administrator.");
        }
        validatePassword(bootstrapPassword);
        userMapper.insert(normalizeUsername(bootstrapUsername), passwordEncoder.encode(bootstrapPassword),
                bootstrapName == null || bootstrapName.isBlank() ? "系统管理员" : bootstrapName.strip(), "ADMIN", true);
        log.info("Initial platform administrator created: {}", normalizeUsername(bootstrapUsername));
    }

    public LoginView login(LoginCommand command) {
        String username = normalizeUsername(command.username());
        Instant now = Instant.now();
        if (isLocked(username, now)) throw invalidCredentials();
        UserMapper.UserRecord user = findRowByUsername(username);
        if (user == null || !user.enabled() || !passwordEncoder.matches(command.password(), user.passwordHash())) {
            if (user != null) recordFailedLogin(username, now);
            throw invalidCredentials();
        }
        failedLogins.remove(username);
        userMapper.updateLastLogin(user.id());
        UserPrincipal principal = user.principal();
        JwtTokenService.TokenResult token = tokenService.issue(principal);
        return new LoginView(token.accessToken(), "Bearer", token.expiresAt(), toView(user));
    }

    public UserPrincipal authenticateToken(String token) {
        JwtTokenService.JwtClaims claims = tokenService.verify(token);
        UserMapper.UserRecord user = findRow(claims.userId());
        // JWT 签名正确仍需回查用户当前状态，确保禁用、改角色和退出登录立即生效。
        if (user == null || !user.enabled() || user.tokenVersion() != claims.tokenVersion()
                || !user.username().equals(claims.username()) || !user.role().equals(claims.role())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "IP-AUTH-004", "登录状态已失效，请重新登录");
        }
        return user.principal();
    }

    public List<UserView> list() {
        return userMapper.findAll().stream().map(UserService::toView).toList();
    }

    public UserView get(long id) {
        UserMapper.UserRecord row = findRow(id);
        if (row == null) throw notFound(id);
        return toView(row);
    }

    @Transactional
    public UserView create(CreateUserCommand command) {
        String role = validateRole(command.role());
        validatePassword(command.password());
        // 更新用户资料也使其全部旧令牌失效，避免权限降级后旧令牌继续可用。
        userMapper.insert(normalizeUsername(command.username()), passwordEncoder.encode(command.password()),
                command.displayName().strip(), role, command.enabled());
        UserMapper.UserRecord row = findRowByUsername(normalizeUsername(command.username()));
        return toView(row);
    }

    @Transactional
    public UserView update(long id, UpdateUserCommand command, long operatorId) {
        UserMapper.UserRecord current = requireRow(id);
        String role = validateRole(command.role());
        if (id == operatorId && (!command.enabled() || !"ADMIN".equals(role))) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "IP-USER-002", "不能禁用当前账号或移除自己的管理员角色");
        }
        if ("ADMIN".equals(current.role()) && (!command.enabled() || !"ADMIN".equals(role))) requireAnotherAdmin(id);
        userMapper.updateProfileAndInvalidateTokens(id, command.displayName().strip(), role, command.enabled());
        return get(id);
    }

    @Transactional
    public void resetPassword(long id, PasswordCommand command) {
        requireRow(id);
        validatePassword(command.password());
        userMapper.updatePasswordAndInvalidateTokens(id, passwordEncoder.encode(command.password()));
    }

    @Transactional
    public void changePassword(long id, ChangePasswordCommand command) {
        UserMapper.UserRecord user = requireRow(id);
        if (!passwordEncoder.matches(command.currentPassword(), user.passwordHash())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "IP-USER-003", "当前密码错误");
        }
        resetPassword(id, new PasswordCommand(command.newPassword()));
    }

    @Transactional
    public void logout(long id) {
        // 无状态 JWT 没有服务端会话可删除，递增版本相当于撤销该用户的全部现有令牌。
        userMapper.invalidateTokens(id);
    }

    @Transactional
    public void delete(long id, long operatorId) {
        UserMapper.UserRecord user = requireRow(id);
        if (id == operatorId) throw new BusinessException(HttpStatus.BAD_REQUEST, "IP-USER-004", "不能删除当前登录账号");
        if ("ADMIN".equals(user.role())) requireAnotherAdmin(id);
        userMapper.disableAndInvalidateTokens(id);
    }

    private void requireAnotherAdmin(long excludedId) {
        long count = userMapper.countOtherEnabledAdministrators(excludedId);
        if (count == 0) throw new BusinessException(HttpStatus.BAD_REQUEST, "IP-USER-005", "必须至少保留一个启用的管理员");
    }

    private UserMapper.UserRecord requireRow(long id) {
        UserMapper.UserRecord row = findRow(id);
        if (row == null) throw notFound(id);
        return row;
    }

    private UserMapper.UserRecord findRow(long id) {
        return userMapper.findById(id);
    }

    private UserMapper.UserRecord findRowByUsername(String username) {
        return userMapper.findByUsername(username);
    }

    private static UserView toView(UserMapper.UserRecord row) {
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

    private record LoginAttempt(int failedCount, Instant lockedUntil) {}

}
