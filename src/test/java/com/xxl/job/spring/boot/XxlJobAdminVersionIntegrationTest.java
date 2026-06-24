package com.xxl.job.spring.boot;

import com.xxl.job.spring.boot.model.ReturnT;
import com.xxl.job.spring.boot.MockXxlJobAdminServer.XxlJobRequestLog;
import com.xxl.job.spring.boot.model.XxlJobGroup;
import com.xxl.job.spring.boot.model.XxlJobGroupList;
import com.xxl.job.spring.boot.model.XxlJobInfo;
import com.xxl.job.spring.boot.model.XxlJobInfoList;
import kong.unirest.Unirest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 多版本 xxl-job-admin 协议集成测试（嵌入式 Mock 服务器）。
 * <p>
 * 覆盖 V2_X（3.0.0 生产）、V3_2_X（3.2.0 混合）、V3_X（3.3.0+ 完整 V3）登录 → CRUD → start/stop 全流程。
 */
@DisplayName("xxl-job-admin 多版本 Mock 集成测试")
class XxlJobAdminVersionIntegrationTest {

    private MockXxlJobAdminServer mockAdmin;
    private XxlJobTemplate template;

    @AfterEach
    void tearDown() {
        if (mockAdmin != null) {
            mockAdmin.close();
        }
        Unirest.shutDown();
    }

    /**
     * 按 AdminVersion 验证登录、分页、CRUD、start/stop 参数格式。
     */
    @ParameterizedTest(name = "AdminVersion={0}")
    @EnumSource(AdminVersion.class)
    @DisplayName("登录 → 执行器/任务 CRUD → start/stop 全流程")
    void shouldCompleteAdminFlowForVersion(AdminVersion version) throws Exception {
        mockAdmin = new MockXxlJobAdminServer(0, version);
        mockAdmin.start();

        XxlJobProperties props = new XxlJobProperties();
        props.setAccessToken("test-token");

        XxlJobAdminProperties adminProps = new XxlJobAdminProperties();
        adminProps.setAddresses(mockAdmin.getBaseUrl());
        adminProps.setUsername("admin");
        adminProps.setPassword("123456");
        adminProps.setVersion(version);

        XxlJobExecutorProperties execProps = new XxlJobExecutorProperties();
        execProps.setAppname("mock-test-executor-" + version.name());

        template = new XxlJobTemplate(Unirest.spawnInstance(), props, adminProps, execProps);

        // 1. 登录
        ReturnT<String> login = template.login("admin", "123456", false);
        assertThat(login.getCode()).as("登录应成功").isEqualTo(ReturnT.SUCCESS_CODE);
        XxlJobRequestLog loginReq = findFirst("/xxl-job-admin" + XxlJobConstants.loginPath(version));
        assertThat(loginReq).isNotNull();

        // 2. 执行器 pageList（校验分页参数）
        ReturnT<XxlJobGroupList> groupList = template.jobInfoGroupList(0, 10);
        assertThat(groupList.getCode()).isEqualTo(ReturnT.SUCCESS_CODE);
        XxlJobRequestLog groupPageReq = findFirst("/xxl-job-admin/jobgroup/pageList");
        if (version.usesV3FullApi()) {
            assertThat(groupPageReq.hasParam("offset")).isTrue();
            assertThat(groupPageReq.hasParam("pagesize")).isTrue();
        } else {
            assertThat(groupPageReq.hasParam("start")).isTrue();
            assertThat(groupPageReq.hasParam("length")).isTrue();
        }

        // 3. 创建执行器（路径因版本而异）
        String appName = "mock-group-" + version.name() + "-" + System.currentTimeMillis();
        XxlJobGroup group = new XxlJobGroup();
        group.setAppName(appName);
        group.setTitle("mock");
        group.setAddressType(0);
        ReturnT<String> addGroup = template.addJobGroup(group);
        assertThat(addGroup.getCode()).isEqualTo(ReturnT.SUCCESS_CODE);
        assertThat(findFirst("/xxl-job-admin" + XxlJobConstants.jobGroupSavePath(version))).isNotNull();

        ReturnT<XxlJobGroupList> afterAdd = template.jobInfoGroupList(0, 10, appName, null);
        assertThat(afterAdd.getContent().getData()).isNotEmpty();
        Integer groupId = afterAdd.getContent().getData().get(0).getId();

        // 4. 创建任务
        XxlJobInfo job = new XxlJobInfo();
        job.setJobGroup(groupId);
        job.setJobDesc("version-test-" + version.name());
        job.setAuthor("test");
        job.setScheduleType("CRON");
        job.setScheduleConf("0 0 3 * * ?");
        job.setGlueType("BEAN");
        job.setExecutorHandler("mockHandler-" + version.name());
        ReturnT<String> addJob = template.addJob(job);
        assertThat(addJob.getCode()).isEqualTo(ReturnT.SUCCESS_CODE);
        assertThat(findFirst("/xxl-job-admin" + XxlJobConstants.jobInfoAddPath(version))).isNotNull();

        ReturnT<XxlJobInfoList> jobList = template.jobInfoList(0, 10, groupId);
        assertThat(jobList.getCode()).isEqualTo(ReturnT.SUCCESS_CODE);

        Integer jobId = Integer.valueOf(addJob.getContent());

        // 5. start/stop（V3 用 ids[]，V2/V3_2 用 id）
        ReturnT<String> start = template.startJob(jobId);
        assertThat(start.getCode()).as("startJob: " + start.getMsg()).isEqualTo(ReturnT.SUCCESS_CODE);
        XxlJobRequestLog startReq = findFirst("/xxl-job-admin/jobinfo/start");
        if (version.usesV3FullApi()) {
            assertThat(startReq.hasParam("ids[]")).isTrue();
        } else {
            assertThat(startReq.hasParam("id")).isTrue();
        }

        ReturnT<String> stop = template.stopJob(jobId);
        assertThat(stop.getCode()).isEqualTo(ReturnT.SUCCESS_CODE);

        // 6. 删除任务与执行器
        ReturnT<String> removeJob = template.removeJob(jobId);
        assertThat(removeJob.getCode()).isEqualTo(ReturnT.SUCCESS_CODE);
        assertThat(findFirst("/xxl-job-admin" + XxlJobConstants.jobInfoRemovePath(version))).isNotNull();

        ReturnT<String> removeGroup = template.removeJobGroup(groupId);
        assertThat(removeGroup.getCode()).isEqualTo(ReturnT.SUCCESS_CODE);
        assertThat(findFirst("/xxl-job-admin" + XxlJobConstants.jobGroupRemovePath(version))).isNotNull();
    }

    private XxlJobRequestLog findFirst(String path) {
        return mockAdmin.getRequestLog().stream()
                .filter(r -> r.path.equals(path))
                .findFirst()
                .orElse(null);
    }
}
