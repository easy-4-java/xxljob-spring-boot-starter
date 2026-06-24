package com.xxl.job.spring.boot;

import com.xxl.job.core.executor.XxlJobExecutor;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.xxl.job.spring.boot.annotation.XxlJobCron;
import com.xxl.job.spring.boot.util.XxlJobHandlerRegistrar;
import com.xxl.job.spring.boot.metrics.MetricMethodJobHandler;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

public class XxlJobAutoBindingAndMetricsSpringExecutor extends XxlJobAutoBindingSpringExecutor {

    private final MeterRegistry registry;
    private final Collection<Tag> tags;

    public XxlJobAutoBindingAndMetricsSpringExecutor(MeterRegistry registry, XxlJobTemplate xxlJobTemplate, Collection<Tag> tags) {
        super(xxlJobTemplate);
        this.registry = registry;
        this.tags = Objects.isNull(tags) ? Collections.emptyList() : tags;
    }

    @Override
    protected void registJobHandler(XxlJob xxlJob, Object bean, Method executeMethod) {
        // 获取 JobHandler 名称、init/destroy 方法
        String name = null;
        String initMethodName = null;
        String destroyMethodName = null;

        // 优先从 @XxlJob 获取
        if (xxlJob != null && StringUtils.hasText(xxlJob.value())) {
            name = xxlJob.value();
            initMethodName = xxlJob.init();
            destroyMethodName = xxlJob.destroy();
        }

        // 如果 @XxlJob 未找到或 value 为空，尝试从 @XxlJobCron 获取
        if (!StringUtils.hasText(name)) {
            XxlJobCron xxlJobCron = AnnotationUtils.findAnnotation(executeMethod, XxlJobCron.class);
            if (xxlJobCron != null && StringUtils.hasText(xxlJobCron.value())) {
                name = xxlJobCron.value();
                initMethodName = xxlJobCron.init();
                destroyMethodName = xxlJobCron.destroy();
            }
        }

        if (!StringUtils.hasText(name)) {
            return;
        }

        //make and simplify the variables since they'll be called several times later
        Class<?> clazz = bean.getClass();
        String methodName = executeMethod.getName();
        if (loadJobHandler(name) != null) {
            throw new RuntimeException("xxl-job jobhandler[" + name + "] naming conflicts.");
        }

        executeMethod.setAccessible(true);

        // init and destroy
        Method initMethod = null;
        Method destroyMethod = null;

        if (StringUtils.hasText(initMethodName)) {
            try {
                initMethod = clazz.getDeclaredMethod(initMethodName);
                initMethod.setAccessible(true);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("xxl-job method-jobhandler initMethod invalid, for[" + clazz + "#" + methodName + "] .");
            }
        }
        if (StringUtils.hasText(destroyMethodName)) {
            try {
                destroyMethod = clazz.getDeclaredMethod(destroyMethodName);
                destroyMethod.setAccessible(true);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("xxl-job method-jobhandler destroyMethod invalid, for[" + clazz + "#" + methodName + "] .");
            }
        }

        // registry jobhandler（跨 core 版本兼容静态/实例 API）
        XxlJobHandlerRegistrar.registerJobHandler(this, name, new MetricMethodJobHandler(registry, bean, executeMethod, initMethod, destroyMethod, tags));
    }

}
