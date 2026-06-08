package com.xxl.job.spring.boot;


import com.xxl.job.core.executor.XxlJobExecutor;
import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import com.xxl.job.spring.boot.cookie.CaffeineCacheCookieJar;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.CollectionUtils;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;

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

	@Bean
	public XxlJobTemplate xxlJobTemplate(
			ObjectProvider<OkHttpClient> okhttp3ClientProvider,
			XxlJobProperties properties,
			XxlJobAdminProperties adminProperties,
			XxlJobAdminCookieProperties cookieProperties,
			XxlJobExecutorProperties executorProperties) {
		OkHttpClient okhttp3Client = okhttp3ClientProvider.getIfAvailable(() -> new OkHttpClient.Builder()
				.cookieJar(new CaffeineCacheCookieJar(cookieProperties.getMaximumSize(), cookieProperties.getExpireAfterWrite(), cookieProperties.getExpireAfterAccess())).build());
		return new XxlJobTemplate(okhttp3Client, properties, adminProperties, executorProperties);
	}
	
	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = XxlJobExecutorProperties.PREFIX, value = "enabled", havingValue = "true", matchIfMissing = true)
	public XxlJobSpringExecutor xxlJobExecutor(
			ObjectProvider<MeterRegistry> registryProvider,
			ObjectProvider<XxlJobTemplate> xxlJobTemplateProvider,
			XxlJobProperties properties,
			XxlJobAdminProperties adminProperties,
			XxlJobExecutorProperties executorProperties,
			XxlJobMetricsProperties metricsProperties ) {
		if (metricsProperties.isEnabled()) {
			log.info(">>>>>>>>>>> xxl-job auto binding and metrics executor init.");
			Collection<Tag> extraTags = CollectionUtils.isEmpty(metricsProperties.getExtraTags()) ? new ArrayList<>() : metricsProperties.getExtraTags()
					.entrySet().stream().map(e -> Tag.of(e.getKey(), e.getValue()))
					.collect(Collectors.toList());
			extraTags.add(Tag.of("executor", executorProperties.getAppname()));
			XxlJobAutoBindingAndMetricsSpringExecutor xxlJobExecutor = new XxlJobAutoBindingAndMetricsSpringExecutor(registryProvider.getObject(), xxlJobTemplateProvider.getObject(), extraTags);
			xxlJobExecutor.setAdminAddresses(adminProperties.getAddresses());
			xxlJobExecutor.setAppname(executorProperties.getAppname());
			xxlJobExecutor.setAppTitle(executorProperties.getTitle());
			xxlJobExecutor.setIp(executorProperties.getIp());
			xxlJobExecutor.setPort(Integer.parseInt(executorProperties.getPort()));
			xxlJobExecutor.setAccessToken(properties.getAccessToken());
			xxlJobExecutor.setLogPath(executorProperties.getLogpath());
			xxlJobExecutor.setLogRetentionDays(executorProperties.getLogretentiondays());
			return xxlJobExecutor;
		} else {
			log.info(">>>>>>>>>>> xxl-job auto binding executor init.");
			XxlJobAutoBindingSpringExecutor xxlJobExecutor = new XxlJobAutoBindingSpringExecutor(xxlJobTemplateProvider.getObject());
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
	

}
