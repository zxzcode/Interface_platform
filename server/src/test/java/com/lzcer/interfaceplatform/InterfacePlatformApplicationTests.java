package com.lzcer.interfaceplatform;

import com.lzcer.interfaceplatform.service.UserService;
import com.lzcer.interfaceplatform.service.InvocationLogService;
import com.lzcer.interfaceplatform.model.invocation.InvocationLogModels;
import com.lzcer.interfaceplatform.model.user.UserModels;
import com.lzcer.interfaceplatform.common.api.BusinessException;
import com.lzcer.interfaceplatform.mapper.ApiClientMapper;
import com.lzcer.interfaceplatform.mapper.DatasourceMapper;
import com.lzcer.interfaceplatform.mapper.InterfaceMapper;
import com.lzcer.interfaceplatform.mapper.PlatformReadMapper;
import com.lzcer.interfaceplatform.mapper.SqlApiMapper;
import com.lzcer.interfaceplatform.mapper.SystemMapper;
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

    @Autowired private ApiClientMapper apiClientMapper;
    @Autowired private DatasourceMapper datasourceMapper;
    @Autowired private InterfaceMapper interfaceMapper;
    @Autowired private PlatformReadMapper platformReadMapper;
    @Autowired private SqlApiMapper sqlApiMapper;
    @Autowired private SystemMapper systemMapper;

    @Test
    void contextLoads() {
    }

    @Test
    void logoutInvalidatesPreviouslyIssuedTokenThroughTokenVersion() {
        UserModels.LoginView login = userService.login(new UserModels.LoginCommand("admin", "TestAdmin#2026"));

        userService.logout(login.user().id());

        assertThatThrownBy(() -> userService.authenticateToken(login.accessToken()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo("IP-AUTH-004"));
    }

    @Test
    void invocationLogMapperMapsSummaryAndDetailRecords() {
        String traceId = "Tlogmap202608131656";
        invocationLogService.save(new InvocationLogModels.InvocationRecord(traceId, "HTTP", "TEST_LOG",
                "日志映射测试", "test", "platform", "GET", "/open-api/test", "http://localhost/test",
                "SUCCESS", null, 200, 1, "{}", null, "{}", "{}", null,
                LocalDateTime.now(), LocalDateTime.now()));

        assertThat(invocationLogService.list(10)).extracting(InvocationLogModels.LogSummary::traceId)
                .contains(traceId);
        assertThat(invocationLogService.detail(traceId).status()).isEqualTo("SUCCESS");
    }

    @Test
    void fixedPlatformMappersMapConfiguredRecords() {
        assertThat(apiClientMapper.findAll()).isNotNull();
        // H2 测试库不依赖演示数据是否存在；调用本身即可验证 XML 到 record 的映射。
        assertThat(datasourceMapper.findAll()).isNotNull();
        assertThat(interfaceMapper.findAll()).isNotNull();
        assertThat(interfaceMapper.findSystemOptions()).isNotNull();
        assertThat(platformReadMapper.findSystems()).isNotNull();
        assertThat(sqlApiMapper.findAll()).isNotNull();
        assertThat(systemMapper.findAll()).isNotNull();
    }
}
