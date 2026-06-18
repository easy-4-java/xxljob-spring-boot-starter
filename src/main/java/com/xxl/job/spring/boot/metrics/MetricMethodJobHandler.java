package com.xxl.job.spring.boot.metrics;

import com.xxl.job.core.handler.IJobHandler;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.xxl.job.spring.boot.annotation.XxlJobCron;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.StopWatch;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * MetricMethodJobHandler
 */
@Slf4j
public class MetricMethodJobHandler extends IJobHandler {

    private final Object target;
    private final Method method;
    private Method initMethod;
    private Method destroyMethod;

    private MeterRegistry registry;
    private Collection<Tag> tags;
    private final Counter submitted;
    private final Counter running;
    private final Counter completed;
    private final Timer duration;

    public MetricMethodJobHandler(MeterRegistry registry, Object target, Method method, Method initMethod, Method destroyMethod, Collection<Tag> tags) {

        this.registry = registry;

        this.target = target;
        this.method = method;

        this.initMethod = initMethod;
        this.destroyMethod = destroyMethod;

        this.tags = Objects.isNull(tags) ? Collections.emptyList() : tags;
        this.submitted = registry.counter(XxlJobMetrics.METRIC_NAME_JOB_REQUESTS_SUBMITTED, this.tags);
        this.running = registry.counter(XxlJobMetrics.METRIC_NAME_JOB_REQUESTS_RUNNING, this.tags);
        this.completed = registry.counter(XxlJobMetrics.METRIC_NAME_JOB_REQUESTS_COMPLETED, this.tags);
        this.duration = registry.timer(XxlJobMetrics.METRIC_NAME_JOB_REQUESTS_DURATION, this.tags);

    }

    @Override
    public void execute() throws Exception {

        // 1、创建并启动 StopWatch
        XxlJob job = AnnotatedElementUtils.findMergedAnnotation(method, XxlJob.class);
        XxlJobCron jobCron = AnnotatedElementUtils.findMergedAnnotation(method, XxlJobCron.class);
        
        // 优先从 @XxlJob 获取名称，否则从 @XxlJobCron 获取
        String jobName;
        String jobDesc;
        if (job != null && StringUtils.hasText(job.value())) {
            jobName = job.value();
            jobDesc = job.value();
        } else if (jobCron != null && StringUtils.hasText(jobCron.value())) {
            jobName = jobCron.value();
            jobDesc = jobCron.desc();
        } else {
            jobName = method.getName();
            jobDesc = method.getName();
        }

        StopWatch stopWatch = new StopWatch(jobName);
        stopWatch.start(jobDesc);

        // 一次请求计数 +1
        submitted.increment();
        // 当前正在运行的请求数 +1
        running.increment();

        // 3、构建指标
        String metric = MetricNames.name(XxlJobMetrics.XXL_JOB_METRIC_NAME_PREFIX, jobName);
        List<Tag> jobTags = new ArrayList<>(tags);
        jobTags.add(Tag.of("job", jobName));
        Timer timer = registry.timer(metric, jobTags);

        try {
            Class<?>[] paramTypes = method.getParameterTypes();
            if (paramTypes.length > 0) {
                method.invoke(target, new Object[paramTypes.length]);       // method-param can not be primitive-types
            } else {
                method.invoke(target);
            }
        } catch (Throwable ex) {
            if(stopWatch.isRunning()){
                stopWatch.stop();
            }
            throw ex;
        } finally {
            if(stopWatch.isRunning()){
                stopWatch.stop();
            }
            // 记录本次请求耗时
            timer.record(stopWatch.getTotalTimeMillis(), TimeUnit.MILLISECONDS);
            duration.record(stopWatch.getTotalTimeMillis(), TimeUnit.MILLISECONDS);
            // 当前正在运行的请求数 -1
            running.increment(-1);
            // 当前已完成的请求数 +1
            completed.increment();
            // 2、记录方法执行时间
            log.info(stopWatch.prettyPrint());
        }

    }

    @Override
    public void init() throws Exception {
        if(initMethod != null) {
            initMethod.invoke(target);
        }
    }

    @Override
    public void destroy() throws Exception {
        if(destroyMethod != null) {
            destroyMethod.invoke(target);
        }
    }

    @Override
    public String toString() {
        return super.toString()+"["+ target.getClass() + "#" + method.getName() +"]";
    }
}