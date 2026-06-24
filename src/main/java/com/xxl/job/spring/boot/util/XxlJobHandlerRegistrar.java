package com.xxl.job.spring.boot.util;

import com.xxl.job.core.executor.XxlJobExecutor;
import com.xxl.job.core.handler.IJobHandler;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * 跨 xxl-job-core 版本注册 JobHandler（兼容 regist/registry、静态/实例方法差异）。
 */
public final class XxlJobHandlerRegistrar {

    private XxlJobHandlerRegistrar() {
    }

    /**
     * 向执行器注册 JobHandler。
     *
     * @param executor 当前 Spring 执行器实例
     * @param name     handler 名称
     * @param handler  JobHandler 实现
     */
    public static void registerJobHandler(XxlJobExecutor executor, String name, IJobHandler handler) {
        for (String methodName : new String[]{"registryJobHandler", "registJobHandler"}) {
            try {
                Method method = XxlJobExecutor.class.getMethod(methodName, String.class, IJobHandler.class);
                if (Modifier.isStatic(method.getModifiers())) {
                    method.invoke(null, name, handler);
                } else {
                    method.invoke(executor, name, handler);
                }
                return;
            } catch (NoSuchMethodException ignored) {
                // try next method name
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException("xxl-job register job handler failed: " + methodName, ex);
            }
        }
        throw new IllegalStateException("xxl-job-core has no regist/registryJobHandler(String, IJobHandler)");
    }

}
