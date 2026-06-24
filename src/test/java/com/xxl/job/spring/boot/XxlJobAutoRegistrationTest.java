package com.xxl.job.spring.boot;

import com.xxl.job.spring.boot.MockXxlJobAdminServer.XxlJobRequestLog;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * xxl-job 自动注册集成测试。
 * <p>
 * 使用嵌入式 MockXxlJobAdminServer 模拟 v3 管理端 API，
 * 验证启动时自动登录、自动创建执行器组、自动注册/启动定时任务的全流程。
 */
@SpringBootTest(classes = SampleXxlJobApplicationTests.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("xxl-job 自动注册集成测试")
class XxlJobAutoRegistrationTest {

    static MockXxlJobAdminServer mockAdmin;

    @DynamicPropertySource
    static void registerMockAdmin(DynamicPropertyRegistry registry) {
        registry.add("xxl.job.admin.addresses", () -> mockAdmin.getBaseUrl());
        registry.add("xxl.job.executor.appname", () -> "xxl-job-test-executor");
    }

    @BeforeAll
    static void startMockAdmin() throws IOException {
        mockAdmin = new MockXxlJobAdminServer();
        mockAdmin.start();
        System.out.println("Mock admin started at: " + mockAdmin.getBaseUrl());
    }

    @AfterAll
    static void stopMockAdmin() {
        if (mockAdmin != null) {
            mockAdmin.close();
        }
    }

    // ======================== 测试用例 ========================

    @Test
    @Order(1)
    @DisplayName("登录请求正确发送到 /auth/doLogin")
    void shouldLoginOnStartup() {
        XxlJobRequestLog loginReq = findFirstRequest("/xxl-job-admin/auth/doLogin");
        assertThat(loginReq).as("启动时应发送登录请求").isNotNull();
        assertThat(loginReq.hasParam("userName")).as("登录应包含 userName").isTrue();
        assertThat(loginReq.hasParam("password")).as("登录应包含 password").isTrue();
    }

    @Test
    @Order(2)
    @DisplayName("自动创建执行器组")
    void shouldCreateJobGroup() {
        XxlJobRequestLog insertReq = findFirstRequest("/xxl-job-admin/jobgroup/insert");
        assertThat(insertReq).as("应发送创建执行器组请求").isNotNull();
        assertThat(insertReq.param("appname")).isEqualTo("xxl-job-test-executor");
    }

    @Test
    @Order(3)
    @DisplayName("自动创建全部 6 个定时任务（使用 @XxlJobCron 注解信息）")
    void shouldCreateAllJobInfos() {
        List<XxlJobRequestLog> inserts = findAllRequests("/xxl-job-admin/jobinfo/insert");
        assertThat(inserts).as("应为每个 @XxlJobCron handler 创建任务").hasSize(6);

        // 收集所有 executorHandler
        Set<String> handlers = inserts.stream()
                .map(r -> r.param("executorHandler"))
                .collect(Collectors.toSet());

        assertThat(handlers).as("应包含 SampleXxlJob 的所有 handler")
                .contains("demoJobHandler", "shardingJobHandler", "commandJobHandler",
                        "httpJobHandler", "demoJobHandler2");
        assertThat(handlers).as("应包含测试用的自启动 handler")
                .contains("testSelfStartingJob");
    }

    @Test
    @Order(4)
    @DisplayName("startJob 请求使用 ids[] 参数（v3 admin 格式）")
    void shouldUseIdsParamForStartJob() {
        for (XxlJobRequestLog req : findAllRequests("/xxl-job-admin/jobinfo/start")) {
            assertThat(req.hasParam("ids[]"))
                    .as("startJob 必须使用 'ids[]' 参数: " + req.params)
                    .isTrue();
        }
    }

    @Test
    @Order(5)
    @DisplayName("selfStarting=true 任务自动调用 start API")
    void shouldAutoStartSelfStartingJobs() {
        List<XxlJobRequestLog> startReqs = findAllRequests("/xxl-job-admin/jobinfo/start");
        assertThat(startReqs).as("selfStarting 任务应自动调 start API").isNotEmpty();

        // 验证每个 start 请求都有有效的 ids[]
        for (XxlJobRequestLog req : startReqs) {
            assertThat(req.param("ids[]")).as("start ids[] 不应为空").isNotNull();
        }
    }

    @Test
    @Order(6)
    @DisplayName("所有 Admin API 请求记录")
    void printAllRequests() {
        System.out.println("=== All Admin API requests (" + mockAdmin.getRequestLog().size() + " total) ===");
        for (XxlJobRequestLog req : mockAdmin.getRequestLog()) {
            System.out.println("  " + req.path + " params=" + req.params.keySet());
        }
    }

    // ======================== 工具方法 ========================

    private XxlJobRequestLog findFirstRequest(String path) {
        return mockAdmin.getRequestLog().stream()
                .filter(r -> r.path.equals(path))
                .findFirst().orElse(null);
    }

    private List<XxlJobRequestLog> findAllRequests(String path) {
        return mockAdmin.getRequestLog().stream()
                .filter(r -> r.path.equals(path))
                .collect(Collectors.toList());
    }
}
