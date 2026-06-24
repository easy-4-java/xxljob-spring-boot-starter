package com.xxl.job.spring.boot;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(XxlJobAdminProperties.PREFIX)
@Data
public class XxlJobAdminProperties {

    public static final String PREFIX = "xxl.job.admin";

    /**
     * 调度中心部署跟地址：如调度中心集群部署存在多个地址则用逗号分隔
     */
    private String addresses;

    /**
     * 调度中心登录账号
     */
    private String username;

    /**
     * 调度中心登录密码
     */
    private String password;

    /**
     * 调度中心登录状态保持。
     * 默认 false：remember-me Cookie 的 Max-Age=2147483647 会导致 Apache HttpClient 解析失败。
     */
    private boolean remember = false;

    /**
     * Admin Web API 协议版本（按路径选，非 admin Maven 版本号）。
     * 默认 V2_X：官方 xxl-job-admin 2.x 及 3.0.0 均使用 /login、/jobgroup/save 等 V2 路径。
     * V3_X：starter 自定义路径（/auth/doLogin 等），官方 3.0.0 源码中不存在。
     */
    private AdminVersion version = AdminVersion.V2_X;

}
