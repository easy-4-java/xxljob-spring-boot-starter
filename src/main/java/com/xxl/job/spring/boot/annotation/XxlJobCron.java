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
package com.xxl.job.spring.boot.annotation;

import com.xxl.job.core.glue.GlueTypeEnum;
import com.xxl.job.core.constant.ExecutorBlockStrategyEnum;
import com.xxl.job.spring.boot.executor.ExecutorRouteStrategyEnum;
import com.xxl.job.spring.boot.executor.MisfireStrategyEnum;
import com.xxl.job.spring.boot.executor.ScheduleTypeEnum;

import java.lang.annotation.*;

/**
 * 增强版 XxlJob 注解，可 100% 替代 @XxlJob
 * <p>
 * 通过 {@code @AliasFor(annotation = XxlJob.class)} 实现与 @XxlJob 的元注解桥接，
 * Spring 的 {@code AnnotatedElementUtils.findMergedAnnotation()} 会自动识别。
 * <p>
 * 使用示例（不再需要 @XxlJob）：
 * <pre>
 * &#64;XxlJobCron(value = "myJob", cron = "0/10 * * * * ?", desc = "我的任务", author = "admin")
 * public void myJob() { ... }
 * </pre>
 * <p>
 * 也可以与 @XxlJob 组合使用（向后兼容），此时 @XxlJobCron.value() 优先作为 JobHandler 名称：
 * <pre>
 * &#64;XxlJob("myJob")
 * &#64;XxlJobCron(cron = "0/10 * * * * ?", desc = "我的任务")
 * public void myJob() { ... }
 * </pre>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface XxlJobCron {

	/**
	 * JobHandler名称，用于在执行器中注册 JobHandler
	 * <p>等同于 @XxlJob.value()，扫描时自动映射</p>
	 */
	String value() default "";

	/**
	 * 初始化方法名，JobThread 初始化时调用
	 * <p>等同于 @XxlJob.init()</p>
	 */
	String init() default "";

	/**
	 * 销毁方法名，JobThread 销毁时调用
	 * <p>等同于 @XxlJob.destroy()</p>
	 */
	String destroy() default "";

	/**
	 * 任务UID编号
	 */
	String uid() default "";

	/**
	 * 任务执行CRON表达式
	 */
	String cron() default "";

	/**
	 * 负责人
	 */
	String author() default "xxl-job";

	/**
	 * 报警邮件
	 */
	String alarmEmail() default "";

	/**
	 * 调度类型 ScheduleTypeEnum
	 */
	ScheduleTypeEnum scheduleType() default ScheduleTypeEnum.CRON;

	/**
	 * 执行器描述
	 */
	String desc() default "";

	/**
	 * 执行器，任务参数
	 */
	String param() default "";

	/**
	 * GLUE类型	#com.xxl.job.core.glue.GlueTypeEnum
	 */
	GlueTypeEnum glueType() default GlueTypeEnum.BEAN;

	/**
	 * 执行器路由策略
	 */
	ExecutorRouteStrategyEnum routeStrategy() default ExecutorRouteStrategyEnum.LEAST_FREQUENTLY_USED;

	/**
	 * 阻塞处理策略
	 */
	ExecutorBlockStrategyEnum blockStrategy() default ExecutorBlockStrategyEnum.COVER_EARLY;

	/**
	 * 调度过期策略
	 */
	MisfireStrategyEnum misfireStrategy() default MisfireStrategyEnum.DO_NOTHING;

	/**
	 * 任务执行超时时间，单位毫秒
	 */
	int timeout() default 3000;

	/**
	 * 失败重试次数
	 */
	int failRetryCount() default 3;

	/**
	 * 自启动
	 */
	boolean selfStarting() default false;

}
