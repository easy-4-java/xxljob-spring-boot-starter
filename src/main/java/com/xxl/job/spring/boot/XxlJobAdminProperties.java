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
     * 调度中心登录状态保持
     */
    private boolean remember;

    /**
     * xxl-job-admin 版本，默认 V3_X
     * V2_X: admin 2.x, 路径 /login, /jobgroup/save, /jobinfo/add, 参数 id
     * V3_X: admin 3.x, 路径 /auth/doLogin, /jobgroup/insert, /jobinfo/insert, 参数 ids[]
     */
    private AdminVersion version = AdminVersion.V3_X;

}
