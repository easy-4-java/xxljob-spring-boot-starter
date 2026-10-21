package com.xxl.job.spring.boot;

import com.xxl.job.core.XxlJobTemplate;
import com.xxl.job.core.annotation.XxlJobCron;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.xxl.job.spring.boot.metrics.MetricMethodJobHandler;
import com.xxl.job.spring.boot.metrics.XxlJobMetrics;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/** 验证带指标执行器保留任务命名、生命周期和冲突检测契约。 */
class XxlJobMetricsExecutorTest {
    /** 提供独立的任务与生命周期调用状态。 */
    static class Jobs {
        boolean initialized;
        boolean executed;
        boolean destroyed;

        @XxlJobCron(value = "metrics-cron-lifecycle", cron = "0/10 * * * * ?", init = "initialize", destroy = "shutdown")
        @XxlJob("metrics-legacy-ignored")
        public void cron() { executed = true; }

        @XxlJob(value = "metrics-legacy-lifecycle", init = "initialize", destroy = "shutdown")
        public void legacy() { executed = true; }

        @XxlJob(value = "metrics-bad-init", init = "missing")
        public void badInit() { }

        @XxlJobCron(value = "metrics-bad-destroy", cron = "0/10 * * * * ?", destroy = "missing")
        public void badDestroy() { }

        public void plain() { }
        public void initialize() { initialized = true; }
        public void shutdown() { destroyed = true; }
    }

    @Test
    void registersCronAndLegacyHandlersWithLifecycleAndMetrics() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            XxlJobAutoBindingAndMetricsSpringExecutor executor = new XxlJobAutoBindingAndMetricsSpringExecutor(
                    registry, mock(XxlJobTemplate.class), Collections.singletonList(Tag.of("executor", "test")));
            for (String methodName : new String[]{"cron", "legacy"}) {
                Jobs jobs = new Jobs();
                Method method = Jobs.class.getMethod(methodName);
                executor.registerJobHandlerInternal(method.getAnnotation(XxlJob.class), jobs, method);
                String name = methodName.equals("cron") ? "metrics-cron-lifecycle" : "metrics-legacy-lifecycle";
                var handler = executor.loadJobHandler(name);
                assertThat(handler).isInstanceOf(MetricMethodJobHandler.class);
                handler.init();
                handler.execute();
                handler.destroy();
                assertThat(jobs.initialized && jobs.executed && jobs.destroyed).isTrue();
                assertThat(registry.get(XxlJobMetrics.METRIC_NAME_JOB_REQUESTS_COMPLETED)
                        .tag("job", name).counter().count()).isEqualTo(1);
                assertThatThrownBy(() -> executor.registerJobHandlerInternal(
                        method.getAnnotation(XxlJob.class), jobs, method)).hasMessageContaining("naming conflicts");
            }
        } finally {
            registry.close();
        }
    }

    @Test
    void ignoresUnannotatedMethodsAndRejectsMissingLifecycleMethods() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            var executor = new XxlJobAutoBindingAndMetricsSpringExecutor(registry, mock(XxlJobTemplate.class), null);
            Jobs jobs = new Jobs();
            executor.registerJobHandlerInternal(null, jobs, Jobs.class.getMethod("plain"));
            assertThat(registry.getMeters()).isEmpty();
            for (String name : new String[]{"badInit", "badDestroy"}) {
                Method method = Jobs.class.getMethod(name);
                assertThatThrownBy(() -> executor.registerJobHandlerInternal(
                        method.getAnnotation(XxlJob.class), jobs, method))
                        .isInstanceOf(IllegalStateException.class).hasMessageContaining("invalid");
            }
        } finally {
            registry.close();
        }
    }
}
