package com.xxl.job.spring.boot;


import com.xxl.job.core.executor.XxlJobExecutor;
import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import kong.unirest.core.Unirest;
import kong.unirest.core.UnirestInstance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(XxlJobExecutor.class)
@EnableConfigurationProperties({
	XxlJobProperties.class,
	XxlJobAdminProperties.class,
	XxlJobAdminCookieProperties.class,
	XxlJobExecutorProperties.class,
	XxlJobMetricsProperties.class
})
@Slf4j
public class XxlJobAutoConfiguration {

	@Bean(destroyMethod = "shutDown")
	@ConditionalOnMissingBean
	public UnirestInstance unirestInstance() {
		UnirestInstance instance = Unirest.spawnInstance();
		instance.config()
				.connectTimeout(10000)
				.enableCookieManagement(true)
				.followRedirects(true);
		return instance;
	}

	@Bean
	public XxlJobTemplate xxlJobTemplate(
			ObjectProvider<UnirestInstance> unirestProvider,
			XxlJobProperties properties,
			XxlJobAdminProperties adminProperties,
			XxlJobExecutorProperties executorProperties) {
		UnirestInstance instance = unirestProvider.getIfAvailable(this::unirestInstance);
		return new XxlJobTemplate(instance, properties, adminProperties, executorProperties);
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = XxlJobExecutorProperties.PREFIX, value = "enabled", havingValue = "true", matchIfMissing = true)
	public XxlJobSpringExecutor xxlJobExecutor(
			XxlJobTemplate xxlJobTemplate,
			XxlJobProperties properties,
			XxlJobAdminProperties adminProperties,
			XxlJobExecutorProperties executorProperties) {
		log.info(">>>>>>>>>>> xxl-job auto binding executor init.");
		XxlJobAutoBindingSpringExecutor xxlJobExecutor = new XxlJobAutoBindingSpringExecutor(xxlJobTemplate);
		xxlJobExecutor.setAdminAddresses(adminProperties.getAddresses());
		xxlJobExecutor.setAppname(executorProperties.getAppname());
		xxlJobExecutor.setAppTitle(executorProperties.getTitle());
		xxlJobExecutor.setIp(executorProperties.getIp());
		xxlJobExecutor.setPort(Integer.parseInt(executorProperties.getPort()));
		xxlJobExecutor.setAccessToken(properties.getAccessToken());
		xxlJobExecutor.setLogPath(executorProperties.getLogpath());
		xxlJobExecutor.setLogRetentionDays(executorProperties.getLogretentiondays());
		return xxlJobExecutor;
	}

}
