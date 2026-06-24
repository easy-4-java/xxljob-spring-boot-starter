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
     * <ul>
     *   <li>V2_X（默认）：admin 2.x / 3.0.0 / 3.1.x</li>
     *   <li>V3_2_X：admin 3.2.0 混合协议（登录 V3 + CRUD V2）</li>
     *   <li>V3_X：admin 3.3.0+ 完整 V3 API</li>
     * </ul>
     */
    private AdminVersion version = AdminVersion.V2_X;

}
