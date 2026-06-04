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
package com.xxl.job.spring.boot;

import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import com.xxl.job.core.glue.GlueFactory;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.xxl.job.core.handler.impl.MethodJobHandler;
import com.xxl.job.spring.boot.annotation.XxlJobCron;
import com.xxl.job.spring.boot.model.XxlJobGroup;
import com.xxl.job.spring.boot.model.XxlJobGroupList;
import com.xxl.job.spring.boot.model.XxlJobInfo;
import com.xxl.job.spring.boot.model.XxlJobInfoList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Xxl Job Handler 自动注册
 * <p>
 * 支持两种扫描模式：
 * 1. @XxlJob + @XxlJobCron 组合使用（兼容旧写法）
 * 2. @XxlJobCron 独立使用（推荐，100% 替代 @XxlJob）
 * </p>
 *
 * @author ： <a href="https://github.com/hiwepy">wandl</a>
 */
@Slf4j
public class XxlJobAutoBindingSpringExecutor extends XxlJobSpringExecutor {

    private XxlJobTemplate xxlJobTemplate;
    private String appName;
    private String appTitle;
    private List<XxlJobInfo> cacheJobs = new ArrayList<>();
    private Random RANDOM_ORDER = new Random(10);

    // 自愈：记录注册失败的任务，定时重试
    private final List<FailedJob> failedJobs = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService retryScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "xxl-job-self-healing");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean retryRunning = new AtomicBoolean(false);
    private static final int MAX_RETRY = 10;

    private static class FailedJob {
        final XxlJobInfo jobInfo;
        final boolean isNew; // true=add, false=update
        int retryCount;
        FailedJob(XxlJobInfo jobInfo, boolean isNew) { this.jobInfo = jobInfo; this.isNew = isNew; }
    }

    public XxlJobAutoBindingSpringExecutor(XxlJobTemplate xxlJobTemplate) {
        this.xxlJobTemplate = xxlJobTemplate;
    }

    @Override
    public void setAppname(String appName) {
        super.setAppname(appName);
        this.appName = appName;
    }

    public void setAppTitle(String appTitle) {
        this.appTitle = appTitle;
    }

    // start
    @Override
    public void afterSingletonsInstantiated() {

        // init JobHandler Repository (for method)
        initJobHandlerMethodRepository(applicationContext);

        // refresh GlueFactory
        GlueFactory.refreshInstance(1);

        // super start
        try {
            super.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void initJobHandlerMethodRepository(ApplicationContext applicationContext) {
        if (applicationContext == null) {
            return;
        }
        // init job handler from method
        String[] beanDefinitionNames = applicationContext.getBeanNamesForType(Object.class, false, true);
        for (String beanDefinitionName : beanDefinitionNames) {

            // get bean
            Object bean = null;
            Lazy onBean = applicationContext.findAnnotationOnBean(beanDefinitionName, Lazy.class);
            if (onBean != null) {
                log.debug("xxl-job annotation scan, skip @Lazy Bean:{}", beanDefinitionName);
                continue;
            } else {
                bean = applicationContext.getBean(beanDefinitionName);
            }

            // ============================================================
            // 扫描 @XxlJob 注解
            // ============================================================
            Map<Method, XxlJob> xxlJobMethods = null;
            try {
                xxlJobMethods = MethodIntrospector.selectMethods(bean.getClass(),
                        new MethodIntrospector.MetadataLookup<XxlJob>() {
                            @Override
                            public XxlJob inspect(Method method) {
                                return AnnotatedElementUtils.findMergedAnnotation(method, XxlJob.class);
                            }
                        });
            } catch (Throwable ex) {
                log.error("xxl-job method-jobhandler resolve error for bean[" + beanDefinitionName + "].", ex);
            }

            // ============================================================
            // 扫描 @XxlJobCron 注解（独立使用，100% 替代 @XxlJob）
            // ============================================================
            Map<Method, XxlJobCron> xxlJobCronMethods = null;
            try {
                xxlJobCronMethods = MethodIntrospector.selectMethods(bean.getClass(),
                        new MethodIntrospector.MetadataLookup<XxlJobCron>() {
                            @Override
                            public XxlJobCron inspect(Method method) {
                                return AnnotationUtils.findAnnotation(method, XxlJobCron.class);
                            }
                        });
            } catch (Throwable ex) {
                log.error("xxl-job XxlJobCron resolve error for bean[" + beanDefinitionName + "].", ex);
            }

            // 合并两种模式的结果
            Set<Method> allMethods = new LinkedHashSet<>();
            if (xxlJobMethods != null) {
                allMethods.addAll(xxlJobMethods.keySet());
            }
            if (xxlJobCronMethods != null) {
                allMethods.addAll(xxlJobCronMethods.keySet());
            }

            if (allMethods.isEmpty()) {
                continue;
            }

            // generate and regist method job handler
            for (Method executeMethod : allMethods) {

                XxlJob xxlJob = (xxlJobMethods != null) ? xxlJobMethods.get(executeMethod) : null;
                XxlJobCron xxlJobCron = (xxlJobCronMethods != null) ? xxlJobCronMethods.get(executeMethod) : null;

                // 获取 JobHandler 名称
                String handlerName = resolveHandlerName(xxlJob, xxlJobCron);
                if (!StringUtils.hasText(handlerName)) {
                    continue;
                }

                // regist job handler (通过父类方法注册到执行器)
                registJobHandler(xxlJob, bean, executeMethod);
                // regist cron task info (for auto-binding to admin)
                registJobHandlerCronTaskInfo(handlerName, xxlJobCron, bean, executeMethod);
            }

        }

        // 所有 Bean 扫描完毕后，统一向 Admin 注册定时任务
        registJobHandlerCronTaskToAdmin();
    }

    /**
     * 解析 JobHandler 名称
     * 优先级：@XxlJobCron.value() > @XxlJob.value()
     */
    private String resolveHandlerName(XxlJob xxlJob, XxlJobCron xxlJobCron) {
        // 优先使用 @XxlJobCron.value()
        if (xxlJobCron != null && StringUtils.hasText(xxlJobCron.value())) {
            return xxlJobCron.value();
        }
        // 其次使用 @XxlJob.value()
        if (xxlJob != null && StringUtils.hasText(xxlJob.value())) {
            return xxlJob.value();
        }
        return null;
    }

    /**
     * 注册定时任务信息到 Admin（支持 @XxlJobCron 独立使用或与 @XxlJob 组合使用）
     */
    private void registJobHandlerCronTaskInfo(String handlerName, XxlJobCron xxlJobCron, Object bean, Method executeMethod) {
        try {
            // 如果没有 @XxlJobCron 注解，不需要自动注册到 Admin
            if (xxlJobCron == null) {
                return;
            }

            XxlJobInfo xxlJobInfo = new XxlJobInfo();
            // 任务描述
            xxlJobInfo.setJobDesc(xxlJobCron.desc());
            // 调度配置，值含义取决于调度类型
            xxlJobInfo.setScheduleConf(xxlJobCron.cron());
            xxlJobInfo.setJobCron(xxlJobCron.cron());
            // 负责人
            xxlJobInfo.setAuthor(xxlJobCron.author());
            // 报警邮件
            xxlJobInfo.setAlarmEmail(xxlJobCron.alarmEmail());
            // 调度类型
            xxlJobInfo.setScheduleType(xxlJobCron.scheduleType().name());
            // 运行模式
            xxlJobInfo.setGlueType(xxlJobCron.glueType().name());
            // JobHandler
            xxlJobInfo.setExecutorHandler(handlerName);
            // 任务参数
            xxlJobInfo.setExecutorParam(xxlJobCron.param());
            // 路由策略
            xxlJobInfo.setExecutorRouteStrategy(xxlJobCron.routeStrategy().name());
            // 失败重试次数
            xxlJobInfo.setExecutorFailRetryCount(xxlJobCron.failRetryCount());
            // 调度过期策略
            xxlJobInfo.setMisfireStrategy(xxlJobCron.misfireStrategy().name());
            // 阻塞处理策略
            xxlJobInfo.setExecutorBlockStrategy(xxlJobCron.blockStrategy().name());
            // 任务超时时间
            xxlJobInfo.setExecutorTimeout(xxlJobCron.timeout());
            // 是否自启动
            xxlJobInfo.setSelfStarting(xxlJobCron.selfStarting());
            cacheJobs.add(xxlJobInfo);
        } catch (Exception ex) {
            log.error(ex.getMessage());
        }
    }

    public void registJobHandlerCronTaskToAdmin() {

        try {

            // 检查执行器是否存在
            if (!StringUtils.hasText(appName)) {
                return;
            }

            // 检查任务组是否存在
            ReturnT<XxlJobGroupList> returnT1 = getXxlJobTemplate().jobInfoGroupList(0, Integer.MAX_VALUE, appName, null);
            if (returnT1.getCode() == ReturnT.FAIL_CODE) {
                log.error(">>>>>>>>>>> 执行器查询失败!失败原因:{}", returnT1.getMsg());
                return;
            }
            // 执行器不存在则创建
            XxlJobGroupList jobGroupList = returnT1.getContent();
            Integer jobGroupId = null;
            if (Objects.isNull(jobGroupList) || CollectionUtils.isEmpty(jobGroupList.getData())
                    || jobGroupList.getData().stream().noneMatch(xxlJobGroup -> xxlJobGroup.getAppName().equals(appName))) {
                log.info(">>>>>>>>>>> 执行器'{}'不存在，开始自动添加！", appName);
                // 创建任务组对象
                XxlJobGroup xxlJobGroup = new XxlJobGroup();
                xxlJobGroup.setAppName(appName);
                xxlJobGroup.setAddressType(0);
                xxlJobGroup.setOrder(RANDOM_ORDER.nextInt(1000));
                xxlJobGroup.setTitle(appTitle);
                ReturnT<String> returnT2 = getXxlJobTemplate().addJobGroup(xxlJobGroup);
                if (returnT2.getCode() == ReturnT.FAIL_CODE) {
                    log.error(">>>>>>>>>>> 执行器'{}'添加添加失败!失败原因:{}", appName, returnT2.getMsg());
                    return;
                }
                returnT1 = getXxlJobTemplate().jobInfoGroupList(0, Integer.MAX_VALUE, appName, null);
                if (returnT1.getCode() == ReturnT.FAIL_CODE) {
                    log.error(">>>>>>>>>>> 执行器查询失败!失败原因:{}", returnT1.getMsg());
                    return;
                }
                jobGroupList = returnT1.getContent();
            }
            // 从搜索结果中提取 jobGroupId（创建新组或已有组都需提取）
            if (Objects.nonNull(jobGroupList) && !CollectionUtils.isEmpty(jobGroupList.getData())) {
                jobGroupId = jobGroupList.getData().stream()
                        .filter(xxlJobGroup -> xxlJobGroup.getAppName().equals(appName))
                        .findFirst().map(XxlJobGroup::getId).orElse(null);
            }
            // 定时任务是否存在
            ReturnT<XxlJobInfoList> returnT3 = getXxlJobTemplate().jobInfoList(0, Integer.MAX_VALUE, jobGroupId);
            if (returnT3.getCode() == ReturnT.FAIL_CODE) {
                log.error(">>>>>>>>>>> 定时任务查询失败!失败原因:{}", returnT3.getMsg());
                return;
            }
            XxlJobInfoList jobInfoList = returnT3.getContent();

            // 执行器存在或者创建成功，添加定时任务
            for (XxlJobInfo xxlJobInfo : cacheJobs) {

                xxlJobInfo.setJobGroup(jobGroupId);

                log.info(">>>>>>>>>>> xxl-job cron task register jobhandler, name:{}, cron:{}", xxlJobInfo.getExecutorHandler(), xxlJobInfo.getScheduleConf());

                if (Objects.isNull(jobInfoList) || CollectionUtils.isEmpty(jobInfoList.getData())
                        || jobInfoList.getData().stream().noneMatch(jobInfo -> jobInfo.getExecutorHandler().equals(xxlJobInfo.getExecutorHandler()))
                ) {
                    log.info(">>>>>>>>>>> 不存在 ExecutorHandler = {} 的定时任务，开始自动添加！", xxlJobInfo.getExecutorHandler());
                    ReturnT<String> returnT4 = getXxlJobTemplate().addJob(xxlJobInfo);
                    if (returnT4.getCode() == ReturnT.FAIL_CODE) {
                        log.error(">>>>>>>>>>> 自动添加 ExecutorHandler = {} 的定时任务失败!失败原因:{}", xxlJobInfo.getExecutorHandler(), returnT4.getMsg());
                        failedJobs.add(new FailedJob(xxlJobInfo, true));
                    } else {
                        log.info(">>>>>>>>>>> 自动添加 ExecutorHandler = {} 的定时任务成功!", xxlJobInfo.getExecutorHandler());
                        xxlJobInfo.setId(Integer.valueOf(returnT4.getContent()));
                    }
                } else {
                    Optional<XxlJobInfo> optional = jobInfoList.getData().stream().filter(jobInfo -> jobInfo.getExecutorHandler().equals(xxlJobInfo.getExecutorHandler())).findFirst();
                    xxlJobInfo.setId(optional.get().getId());

                    log.info(">>>>>>>>>>> 存在 JobId = {}, ExecutorHandler = {} 的定时任务，开始自动更新！", xxlJobInfo.getId(), xxlJobInfo.getExecutorHandler());

                    ReturnT<String> returnT4 = getXxlJobTemplate().updateJob(xxlJobInfo);
                    if (returnT4.getCode() == ReturnT.FAIL_CODE) {
                        log.error(">>>>>>>>>>> 自动更新 JobId = {}, ExecutorHandler = {} 的定时任务失败!失败原因:{}", xxlJobInfo.getId(), xxlJobInfo.getExecutorHandler(), returnT4.getMsg());
                        failedJobs.add(new FailedJob(xxlJobInfo, false));
                    } else {
                        log.info(">>>>>>>>>>> 自动更新 JobId = {}, ExecutorHandler = {} 的定时任务成功!", xxlJobInfo.getId(), xxlJobInfo.getExecutorHandler());
                    }
                }

                // 如果是自启动，则启动任务
                if (xxlJobInfo.isSelfStarting() && Objects.nonNull(xxlJobInfo.getId())) {
                    ReturnT<String> returnT4 = getXxlJobTemplate().startJob(xxlJobInfo.getId());
                    if (returnT4.getCode() == ReturnT.FAIL_CODE) {
                        log.error(">>>>>>>>>>> 自动启动  ExecutorHandler = {} 的定时任务失败!失败原因:{}", xxlJobInfo.getExecutorHandler(), returnT4.getMsg());
                    } else {
                        log.info(">>>>>>>>>>> 自动启动 ExecutorHandler = {} 的定时任务成功!", xxlJobInfo.getExecutorHandler());
                    }
                }

            }

            // 启动自愈重试调度器
            startRetryScheduler();

        } catch (Exception ex) {
            log.error(ex.getMessage());
        }
    }

    private void startRetryScheduler() {
        if (!retryRunning.compareAndSet(false, true)) {
            return;
        }
        log.info(">>>>>>>>>>> xxl-job self-healing scheduler started, failed jobs: {}", failedJobs.size());
        retryScheduler.scheduleWithFixedDelay(() -> {
            if (failedJobs.isEmpty()) {
                return;
            }
            log.info(">>>>>>>>>>> xxl-job self-healing retry: {} pending jobs", failedJobs.size());
            List<FailedJob> snapshot = new ArrayList<>(failedJobs);
            for (FailedJob fj : snapshot) {
                try {
                    if (fj.retryCount >= MAX_RETRY) {
                        log.warn(">>>>>>>>>>> xxl-job self-healing: handler={} exceeded max retries, giving up", fj.jobInfo.getExecutorHandler());
                        failedJobs.remove(fj);
                        continue;
                    }
                    fj.retryCount++;
                    ReturnT<String> result;
                    if (fj.isNew) {
                        result = getXxlJobTemplate().addJob(fj.jobInfo);
                    } else {
                        result = getXxlJobTemplate().updateJob(fj.jobInfo);
                    }
                    if (result.getCode() == ReturnT.SUCCESS_CODE) {
                        log.warn(">>>>>>>>>>> xxl-job self-healing SUCCESS: handler={} (retry #{})", fj.jobInfo.getExecutorHandler(), fj.retryCount);
                        failedJobs.remove(fj);
                    } else {
                        log.warn(">>>>>>>>>>> xxl-job self-healing retry #{}: handler={} still failed: {}", fj.retryCount, fj.jobInfo.getExecutorHandler(), result.getMsg());
                    }
                } catch (Exception e) {
                    log.error(">>>>>>>>>>> xxl-job self-healing retry error: handler={}", fj.jobInfo.getExecutorHandler(), e);
                }
            }
        }, 60, 60, TimeUnit.SECONDS);
    }

    public XxlJobTemplate getXxlJobTemplate() {
        return xxlJobTemplate;
    }

    /**
     * 重写注册方法，支持 @XxlJobCron 独立使用（不依赖 @XxlJob）
     * 当 xxlJob 为 null 时，从 @XxlJobCron 获取 handler 名称和生命周期方法
     */
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

        // registry jobhandler
        registJobHandler(name, new MethodJobHandler(bean, executeMethod, initMethod, destroyMethod));
    }

    // ---------------------- applicationContext ----------------------
    public ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

}
