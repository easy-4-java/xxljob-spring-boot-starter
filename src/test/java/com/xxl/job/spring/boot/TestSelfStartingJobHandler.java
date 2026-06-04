package com.xxl.job.spring.boot;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.spring.boot.annotation.XxlJobCron;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 测试用：自启动任务 Handler，用于集成测试验证 start API。
 */
@Slf4j
@Component
public class TestSelfStartingJobHandler {

    @XxlJobCron(
        value = "testSelfStartingJob",
        cron = "0 0/5 * * * ?",
        desc = "集成测试-自启动任务",
        author = "test",
        selfStarting = true
    )
    public void testSelfStartingJob() {
        XxlJobHelper.handleSuccess("test ok");
    }
}
