package com.lzcer.interfaceplatform;

import com.lzcer.interfaceplatform.service.UserService;
import com.lzcer.interfaceplatform.service.InvocationLogService;
import com.lzcer.interfaceplatform.common.api.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:interface_platform_context;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "platform.security.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "platform.security.jwt-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "platform.security.bootstrap-admin-password=TestAdmin#2026"
})
class InterfacePlatformApplicationTests {

    @Autowired
    private UserService userService;

    @Autowired
    private InvocationLogService invocationLogService;

    @Test
    void contextLoads() {
    }

    @Test
    void logoutInvalidatesPreviouslyIssuedTokenThroughTokenVersion() {
        UserService.LoginView login = userService.login(new UserService.LoginCommand("admin", "TestAdmin#2026"));

        userService.logout(login.user().id());

        assertThatThrownBy(() -> userService.authenticateToken(login.accessToken()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo("IP-AUTH-004"));
    }

    @Test
    void invocationLogMapperMapsSummaryAndDetailRecords() {
        String traceId = "Tlogmap202608131656";
        invocationLogService.save(new InvocationLogService.InvocationRecord(traceId, "HTTP", "TEST_LOG",
                "日志映射测试", "test", "platform", "GET", "/open-api/test", "http://localhost/test",
                "SUCCESS", null, 200, 1, "{}", null, "{}", "{}", null,
                LocalDateTime.now(), LocalDateTime.now()));

        assertThat(invocationLogService.list(10)).extracting(InvocationLogService.LogSummary::traceId)
                .contains(traceId);
        assertThat(invocationLogService.detail(traceId).status()).isEqualTo("SUCCESS");
    }
}
