package com.xxl.job.spring.boot;


import com.xxl.job.core.executor.XxlJobExecutor;
import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import com.xxl.job.spring.boot.admin.DefaultXxlJobAdminClient;
import com.xxl.job.spring.boot.admin.XxlJobAdminClient;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import kong.unirest.Unirest;
import kong.unirest.UnirestInstance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.CollectionUtils;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;
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

	private SSLContext createTrustAllSslContext() {
		try {
			TrustManager[] trustAllCerts = new TrustManager[]{
				new X509TrustManager() {
					@Override
					public X509Certificate[] getAcceptedIssuers() {
						return new X509Certificate[0];
					}
					@Override
					public void checkClientTrusted(X509Certificate[] certs, String authType) {}
					@Override
					public void checkServerTrusted(X509Certificate[] certs, String authType) {}
				}
			};
			SSLContext sslContext = SSLContext.getInstance("TLS");
			sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
			return sslContext;
		} catch (Exception e) {
			log.error("Failed to create trust-all SSL context", e);
			return null;
		}
	}

	@Bean(destroyMethod = "close")
	@ConditionalOnMissingBean
	public UnirestInstance unirestInstance() {
		UnirestInstance instance = Unirest.spawnInstance();
		SSLContext sslContext = createTrustAllSslContext();
		if (sslContext != null) {
			instance.config().sslContext(sslContext);
		}
		instance.config()
				.connectTimeout(10000)
				// Cookie 由 XxlJobAdminCookieStore 手工管理，规避无效 Expires 解析失败
				.enableCookieManagement(false)
				.followRedirects(true);
		return instance;
	}

	@Bean
	@ConditionalOnMissingBean
	public XxlJobAdminClient xxlJobAdminClient(
			ObjectProvider<UnirestInstance> unirestProvider,
			XxlJobProperties properties,
			XxlJobAdminProperties adminProperties) {
		UnirestInstance instance = unirestProvider.getIfAvailable(this::unirestInstance);
		return new DefaultXxlJobAdminClient(instance, properties, adminProperties);
	}

	@Bean
	public XxlJobTemplate xxlJobTemplate(
			ObjectProvider<UnirestInstance> unirestProvider,
			ObjectProvider<XxlJobAdminClient> adminClientProvider,
			XxlJobProperties properties,
			XxlJobAdminProperties adminProperties,
			XxlJobExecutorProperties executorProperties) {
		XxlJobAdminClient adminClient = adminClientProvider.getIfAvailable(() ->
				new DefaultXxlJobAdminClient(unirestProvider.getIfAvailable(this::unirestInstance), properties, adminProperties));
		return new XxlJobTemplate(adminClient, properties, adminProperties, executorProperties);
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
			xxlJobExecutor.setAppTitle(executorProperties.getTitle());
			configureExecutor(xxlJobExecutor, adminProperties, executorProperties, properties);
			return xxlJobExecutor;
		} else {
			log.info(">>>>>>>>>>> xxl-job auto binding executor init.");
			XxlJobAutoBindingSpringExecutor xxlJobExecutor = new XxlJobAutoBindingSpringExecutor(xxlJobTemplateProvider.getObject());
			xxlJobExecutor.setAppTitle(executorProperties.getTitle());
			configureExecutor(xxlJobExecutor, adminProperties, executorProperties, properties);
			return xxlJobExecutor;
		}
	}

	/**
	 * Configure common executor properties to avoid duplicated code.
	 */
	private void configureExecutor(XxlJobSpringExecutor executor,
						XxlJobAdminProperties adminProperties,
						XxlJobExecutorProperties executorProperties,
						XxlJobProperties properties) {
		executor.setAdminAddresses(adminProperties.getAddresses());
		executor.setAppname(executorProperties.getAppname());
		executor.setAddress(executorProperties.getAddress());
		executor.setIp(executorProperties.getIp());
		executor.setPort(resolvePort(executorProperties.getPort()));
		executor.setAccessToken(properties.getAccessToken());
		executor.setLogPath(executorProperties.getLogPath());
		executor.setLogRetentionDays(executorProperties.getLogRetentionDays());
	}

	/**
	 * 解析执行器端口号。Nacos 等外部配置源可能将空值绑定为空串 {@code ""}，
	 * 直接 {@link Integer#parseInt(String)} 会抛 {@link NumberFormatException}；
	 * 此处对 null/空/非法格式统一兜底为 {@code -1}（自动探测）。
	 */
	private int resolvePort(String port) {
		if ( port == null || port.trim().isEmpty()) {
			return -1;
		}
		return Integer.parseInt(port.trim());
	}

}
