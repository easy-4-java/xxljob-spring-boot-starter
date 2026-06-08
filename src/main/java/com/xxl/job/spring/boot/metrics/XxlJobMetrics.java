/*
 * Copyright (c) 2018, hiwepy (https://github.com/hiwepy).
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.xxl.job.spring.boot.metrics;

import com.xxl.job.core.biz.model.HandleCallbackParam;
import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import com.xxl.job.core.thread.TriggerCallbackThread;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

/**
 * XXL Job Metrics
 */
@Slf4j
public class XxlJobMetrics implements MeterBinder, ApplicationListener<ApplicationStartedEvent> {

	/**
	 * Prefix used for all XXL Job metric names.
	 */
	public static final String XXL_JOB_METRIC_NAME_PREFIX = "xxl";

	/**
	 * job
	 */
	public static final String METRIC_NAME_JOB_REQUESTS_SUBMITTED 			= XXL_JOB_METRIC_NAME_PREFIX + ".job.submitted";
	public static final String METRIC_NAME_JOB_REQUESTS_RUNNING 			= XXL_JOB_METRIC_NAME_PREFIX + ".job.running";
	public static final String METRIC_NAME_JOB_REQUESTS_COMPLETED 			= XXL_JOB_METRIC_NAME_PREFIX + ".job.completed";
	public static final String METRIC_NAME_JOB_REQUESTS_DURATION 			= XXL_JOB_METRIC_NAME_PREFIX + ".job.duration";
	public static final String METRIC_NAME_JOB_QUEUE_SIZE 			= XXL_JOB_METRIC_NAME_PREFIX + ".job.queue.size";

	private XxlJobSpringExecutor executor;
	private Iterable<Tag> tags;

	public XxlJobMetrics(XxlJobSpringExecutor executor) {
		this(executor, Collections.emptyList());
	}

	public XxlJobMetrics(XxlJobSpringExecutor executor, Iterable<Tag> tags) {
		this.executor = executor;
		this.tags = tags;
	}

	@Override
	public void onApplicationEvent(ApplicationStartedEvent event) {
		this.bindTo(event.getApplicationContext().getBean(MeterRegistry.class));
	}

	@Override
	public void bindTo(MeterRegistry registry) {

		bindCounter(registry, METRIC_NAME_JOB_QUEUE_SIZE, "the size of job callBack Queue ", TriggerCallbackThread.getInstance(), (m) -> {
			try {
				Field field = ReflectionUtils.findField(TriggerCallbackThread.class, "callBackQueue");
				ReflectionUtils.makeAccessible(field);
				Object queue = field.get(m);
				if (Objects.nonNull(queue) && queue instanceof LinkedBlockingQueue) {
					return ((LinkedBlockingQueue<?>) queue).size();
				}
				return 0;
			} catch (Exception e) {
				log.error(e.getMessage());
			}
			return 0;
		});
		// bindCounter(registry, METRIC_NAME_DISPATCHER_MAX_REQUESTS, "max requests of dispatcher ", dispatcher, Dispatcher::getMaxRequests);
	}



	private <T> void bindTimer(MeterRegistry registry, String name, String desc, T metricsHandler,
						   ToLongFunction<T> countFunc, ToDoubleFunction<T> consumer) {
		FunctionTimer.builder(name, metricsHandler, countFunc, consumer, TimeUnit.SECONDS)
				.description(desc)
				.tags(tags)
				.register(registry);
	}

	private <T> void bindGauge(MeterRegistry registry, String name, String desc, T metricResult,
								   ToDoubleFunction<T> consumer) {
		Gauge.builder(name, metricResult, consumer)
				.description(desc)
				.tags(tags)
				.register(registry);
	}
	
	private <T> void bindTimeGauge(MeterRegistry registry, String name, String desc, T metricResult,
							   ToDoubleFunction<T> consumer) {
		TimeGauge.builder(name, metricResult, TimeUnit.SECONDS, consumer)
				.description(desc)
				.tags(tags)
				.register(registry);
	}

	private <T> void bindCounter(MeterRegistry registry, String name, String desc, T metricsHandler,
							 ToDoubleFunction<T> consumer) {
		FunctionCounter.builder(name, metricsHandler, consumer)
				.description(desc)
				.tags(tags)
				.register(registry);
	}

}
