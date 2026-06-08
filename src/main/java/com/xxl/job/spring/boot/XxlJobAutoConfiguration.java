package com.xxl.job.spring.boot;


import com.xxl.job.core.executor.XxlJobExecutor;
import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
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
					public X509Certificate[] getAcceptedIssuers() {
						return new X509Certificate[0];
					}
					public void checkClientTrusted(X509Certificate[] certs, String authType) {}
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
