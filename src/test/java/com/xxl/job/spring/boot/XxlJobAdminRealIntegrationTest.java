package com.xxl.job.spring.boot;

import com.xxl.job.spring.boot.model.ReturnT;
import com.xxl.job.spring.boot.model.XxlJobGroup;
import com.xxl.job.spring.boot.model.XxlJobGroupList;
import com.xxl.job.spring.boot.model.XxlJobInfo;
import com.xxl.job.spring.boot.model.XxlJobInfoList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 针对真实 xxl-job-admin 的集成测试。
 * <p>
 * 需要设置环境变量 XXL_JOB_ADMIN_URL 或使用默认地址。
 * 验证登录、执行器 CRUD、任务 CRUD、start/stop（v3 参数格式）全流程。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("xxl-job-admin 真实集成测试")
@EnabledIfEnvironmentVariable(named = "XXL_JOB_ADMIN_URL", matches = ".+")
class XxlJobAdminRealIntegrationTest {

    private static XxlJobTemplate template;
    private static Integer testGroupId;
    private static Integer testJobId;

    @BeforeAll
    static void setUp() {
        String adminUrl = System.getenv("XXL_JOB_ADMIN_URL");
        if (adminUrl == null || adminUrl.isBlank()) {
            adminUrl = System.getProperty("xxl.admin.url", "http://192.168.3.116:31505/xxl-job-admin");
        }
        String username = System.getProperty("xxl.admin.username", "admin");
        String password = System.getProperty("xxl.admin.password", "123456");

        System.out.println("=== 真实 Admin 集成测试 ===");
        System.out.println("Admin URL: " + adminUrl);

        XxlJobProperties props = new XxlJobProperties();
        props.setAccessToken("khQTDSfajbcJ88ts");

        XxlJobAdminProperties adminProps = new XxlJobAdminProperties();
        adminProps.setAddresses(adminUrl);
        adminProps.setUsername(username);
        adminProps.setPassword(password);
        adminProps.setVersion(AdminVersion.V3_X);

        XxlJobExecutorProperties execProps = new XxlJobExecutorProperties();
        execProps.setAppname("xxl-job-real-test-" + System.currentTimeMillis());
        execProps.setTitle("真实测试执行器");

        // 直接用 Unirest 创建 template（不走 Spring）
        kong.unirest.UnirestInstance unirest = kong.unirest.Unirest.spawnInstance();
        unirest.config()
                .connectTimeout(15000)
                .enableCookieManagement(true)
                .followRedirects(true);

        template = new XxlJobTemplate(unirest, props, adminProps, execProps);

        // 先登录
        ReturnT<String> loginResult = template.login(username, password, true);
        System.out.println("登录结果: " + (loginResult.getCode() == ReturnT.SUCCESS_CODE ? "成功" : "失败 - " + loginResult.getMsg()));
    }

    // ======================== 执行器 CRUD ========================

    @Test
    @Order(1)
    @DisplayName("1. 查询执行器列表")
    void shouldQueryJobGroupList() {
        ReturnT<XxlJobGroupList> result = template.jobInfoGroupList(0, 10);
        assertThat(result.getCode()).isEqualTo(ReturnT.SUCCESS_CODE);
        System.out.println("  执行器列表查询成功");
    }

    @Test
    @Order(2)
    @DisplayName("2. 创建执行器")
    void shouldCreateJobGroup() {
        XxlJobGroup group = new XxlJobGroup();
        group.setAppName("xxl-job-real-test-" + System.currentTimeMillis());
        group.setTitle("真实测试执行器");
        group.setAddressType(0);
        group.setOrder(1);

        ReturnT<String> result = template.addJobGroup(group);
        System.out.println("  创建执行器 result: code=" + result.getCode() + " content=" + result.getContent());
        // addJobGroup 在某些 admin 版本中不返回 content，通过回查获取 ID

        // 回查获取 ID
        ReturnT<XxlJobGroupList> listResult = template.jobInfoGroupList(0, 10, group.getAppName(), null);
        if (listResult.getCode() == ReturnT.SUCCESS_CODE
                && listResult.getContent() != null
                && listResult.getContent().getData() != null
                && !listResult.getContent().getData().isEmpty()) {
            testGroupId = listResult.getContent().getData().get(0).getId();
            System.out.println("  执行器 ID=" + testGroupId);
        }
    }

    // ======================== 任务 CRUD ========================

    @Test
    @Order(3)
    @DisplayName("3. 创建任务（addJob）")
    void shouldCreateJob() {
        assertThat(testGroupId).as("需要先创建执行器").isNotNull();

        XxlJobInfo job = new XxlJobInfo();
        job.setJobGroup(testGroupId);
        job.setJobDesc("集成测试-验证任务");
        job.setAuthor("test");
        job.setScheduleType("CRON");
        job.setScheduleConf("0 0 3 * * ?");
        job.setJobCron("0 0 3 * * ?");
        job.setGlueType("BEAN");
        job.setExecutorHandler("demoJobHandler");
        job.setExecutorRouteStrategy("FIRST");
        job.setMisfireStrategy("DO_NOTHING");
        job.setExecutorBlockStrategy("SERIAL_EXECUTION");
        job.setExecutorTimeout(0);
        job.setExecutorFailRetryCount(0);
        job.setTriggerStatus(1);

        ReturnT<String> result = template.addJob(job);
        assertThat(result.getCode()).as("创建任务失败: " + result.getMsg()).isEqualTo(ReturnT.SUCCESS_CODE);
        System.out.println("  创建任务成功, content=" + result.getContent());

        // 记录 job id
        if (result.getContent() != null) {
            testJobId = Integer.valueOf(result.getContent());
            System.out.println("  任务 ID=" + testJobId);
        }
    }

    @Test
    @Order(4)
    @DisplayName("4. 查询任务列表")
    void shouldQueryJobInfoList() {
        assertThat(testGroupId).as("需要先创建执行器").isNotNull();
        ReturnT<XxlJobInfoList> result = template.jobInfoList(0, 10, testGroupId);
        assertThat(result.getCode()).as("查询任务列表失败: " + result.getMsg()).isEqualTo(ReturnT.SUCCESS_CODE);
        System.out.println("  任务列表查询成功");
    }

    // ======================== 核心 BUG 验证 ========================

    @Test
    @Order(5)
    @DisplayName("5. 【关键】startJob 使用 id 参数 — 验证修复后不再 HTTP 400")
    void shouldStartJobWithIdParam() {
        assertThat(testJobId).as("需要先创建任务").isNotNull();

        ReturnT<String> result = template.startJob(testJobId);
        assertThat(result.getCode())
                .as("startJob 应成功（修复后使用 'id' 参数而非 'ids[]'）: " + result.getMsg())
                .isEqualTo(ReturnT.SUCCESS_CODE);
        System.out.println("  startJob 成功! 使用 id=" + testJobId);
    }

    @Test
    @Order(6)
    @DisplayName("6. 【关键】stopJob 使用 id 参数 — 验证修复后不再 HTTP 400")
    void shouldStopJobWithIdParam() {
        assertThat(testJobId).as("需要先创建任务").isNotNull();

        ReturnT<String> result = template.stopJob(testJobId);
        assertThat(result.getCode())
                .as("stopJob 应成功（修复后使用 'id' 参数而非 'ids[]'）: " + result.getMsg())
                .isEqualTo(ReturnT.SUCCESS_CODE);
        System.out.println("  stopJob 成功! 使用 id=" + testJobId);
    }

    // ======================== 清理 ========================

    @Test
    @Order(7)
    @DisplayName("7. 删除任务")
    void shouldDeleteJob() {
        if (testJobId == null) {
            System.out.println("  跳过：无任务需要删除");
            return;
        }
        ReturnT<String> result = template.removeJob(testJobId);
        assertThat(result.getCode()).as("删除任务失败: " + result.getMsg()).isEqualTo(ReturnT.SUCCESS_CODE);
        System.out.println("  删除任务成功, id=" + testJobId);
    }

    @Test
    @Order(8)
    @DisplayName("8. 删除执行器")
    void shouldDeleteJobGroup() {
        if (testGroupId == null) {
            System.out.println("  跳过：无执行器需要删除");
            return;
        }
        ReturnT<String> result = template.removeJobGroup(testGroupId);
        assertThat(result.getCode()).as("删除执行器失败: " + result.getMsg()).isEqualTo(ReturnT.SUCCESS_CODE);
        System.out.println("  删除执行器成功, id=" + testGroupId);
    }
}
