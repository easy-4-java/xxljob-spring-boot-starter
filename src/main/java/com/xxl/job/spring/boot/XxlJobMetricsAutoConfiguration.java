package com.xxl.job.spring.boot;

import com.xxl.job.core.executor.XxlJobExecutor;
import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import com.xxl.job.spring.boot.metrics.XxlJobMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * XXL Job
 */
@AutoConfigureAfter(MetricsAutoConfiguration.class)
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({ MeterRegistry.class, XxlJobExecutor.class  })
@ConditionalOnProperty(prefix = XxlJobMetricsProperties.PREFIX, value = "enabled", havingValue = "true", matchIfMissing = false)
@EnableConfigurationProperties({ XxlJobProperties.class, XxlJobAdminProperties.class, XxlJobExecutorProperties.class, XxlJobMetricsProperties.class })
@Slf4j
public class XxlJobMetricsAutoConfiguration {

	@Bean
	@ConditionalOnProperty(prefix = XxlJobExecutorProperties.PREFIX, value = "enabled", havingValue = "true", matchIfMissing = true)
	public XxlJobMetrics xxlJobMetrics(XxlJobSpringExecutor executor) {
		return new XxlJobMetrics(executor);
	}

}
