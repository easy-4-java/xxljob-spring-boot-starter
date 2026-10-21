package com.xxl.job.spring.boot;

import com.xxl.job.core.XxlJobTemplate;
import com.xxl.job.core.admin.XxlJobAdminClient;
import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import com.xxl.job.spring.XxlJobAutoBindingSpringExecutor;
import com.xxl.job.spring.boot.metrics.XxlJobMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import kong.unirest.UnirestInstance;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.concurrent.LinkedBlockingQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 验证指标启停、端口绑定和跨 core 版本的指标降级契约。 */
class XxlJobConfigurationVariantsTest {
    /** 模拟仍暴露回调队列的旧版 core。 */
    static class LegacyCallbackQueue {
        private final LinkedBlockingQueue<String> callBackQueue = new LinkedBlockingQueue<>();
    }

    @Test
    void selectsMetricExecutorAndPreservesConfiguredPort() {
        var configuration = new XxlJobAutoConfiguration();
        var beans = new StaticListableBeanFactory();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            beans.addBean("registry", registry);
            beans.addBean("template", mock(XxlJobTemplate.class));
            var properties = configuration.xxlJobExecutorProperties();
            properties.setAppname("metrics-test");
            var metrics = new XxlJobMetricsProperties();
            metrics.setEnabled(true);
            metrics.setExtraTags(Collections.singletonMap("region", "test"));
            for (String port : new String[]{null, "", "  ", " 9123 "}) {
                properties.setPort(port);
                var executor = configuration.xxlJobExecutor(beans.getBeanProvider(MeterRegistry.class),
                        beans.getBeanProvider(XxlJobTemplate.class), configuration.xxlJobProperties(),
                        configuration.xxlJobAdminProperties(), properties, metrics);
                assertThat(executor).isInstanceOf(XxlJobAutoBindingAndMetricsSpringExecutor.class);
                assertThat(ReflectionTestUtils.getField(executor, "port"))
                        .isEqualTo(" 9123 ".equals(port) ? 9123 : -1);
            }
            metrics.setExtraTags(Collections.emptyMap());
            var executor = configuration.xxlJobExecutor(beans.getBeanProvider(MeterRegistry.class),
                    beans.getBeanProvider(XxlJobTemplate.class), configuration.xxlJobProperties(),
                    configuration.xxlJobAdminProperties(), properties, metrics);
            assertThat(new XxlJobMetricsAutoConfiguration().xxlJobMetrics(executor)).isNotNull();
        } finally {
            registry.close();
        }
    }

    @Test
    void fallsBackWithoutRegistryAndRejectsMalformedPorts() {
        var configuration = new XxlJobAutoConfiguration();
        var beans = new StaticListableBeanFactory();
        beans.addBean("template", mock(XxlJobTemplate.class));
        var properties = configuration.xxlJobExecutorProperties();
        var metrics = new XxlJobMetricsProperties();
        metrics.setEnabled(true);
        var executor = configuration.xxlJobExecutor(beans.getBeanProvider(MeterRegistry.class),
                beans.getBeanProvider(XxlJobTemplate.class), configuration.xxlJobProperties(),
                configuration.xxlJobAdminProperties(), properties, metrics);
        assertThat(executor).isExactlyInstanceOf(XxlJobAutoBindingSpringExecutor.class);
        properties.setPort("invalid");
        assertThatThrownBy(() -> configuration.xxlJobExecutor(beans.getBeanProvider(MeterRegistry.class),
                beans.getBeanProvider(XxlJobTemplate.class), configuration.xxlJobProperties(),
                configuration.xxlJobAdminProperties(), properties, metrics)).isInstanceOf(NumberFormatException.class);
    }

    @Test
    void createsDefaultAdminClientAndTemplateWithoutNetworkRequests() {
        var configuration = new XxlJobAutoConfiguration();
        var beans = new StaticListableBeanFactory();
        try (UnirestInstance unirest = configuration.unirestInstance()) {
            beans.addBean("unirest", unirest);
            assertThat(configuration.xxlJobAdminClient(beans.getBeanProvider(UnirestInstance.class),
                    configuration.xxlJobProperties(), configuration.xxlJobAdminProperties())).isNotNull();
            assertThat(configuration.xxlJobTemplate(beans.getBeanProvider(UnirestInstance.class),
                    beans.getBeanProvider(XxlJobAdminClient.class), configuration.xxlJobProperties(),
                    configuration.xxlJobAdminProperties())).isNotNull();
        }
    }

    @Test
    void readsLegacyCallbackQueueAndBindsOnApplicationStarted() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            var metrics = new XxlJobMetrics(new XxlJobSpringExecutor(), null);
            LegacyCallbackQueue queue = new LegacyCallbackQueue();
            queue.callBackQueue.add("pending");
            Double size = ReflectionTestUtils.invokeMethod(metrics, "callbackQueueSize", LegacyCallbackQueue.class, queue);
            assertThat(size).isEqualTo(1);
            Double absent = ReflectionTestUtils.invokeMethod(metrics, "callbackQueueSize", Object.class, new Object());
            assertThat(absent).isZero();
            ApplicationStartedEvent event = mock(ApplicationStartedEvent.class);
            ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
            when(event.getApplicationContext()).thenReturn(context);
            when(context.getBean(MeterRegistry.class)).thenReturn(registry);
            metrics.onApplicationEvent(event);
            assertThatThrownBy(() -> metrics.bindTo(null)).isInstanceOf(NullPointerException.class);
        } finally {
            registry.close();
        }
    }
}
