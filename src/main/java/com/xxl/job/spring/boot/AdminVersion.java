package com.xxl.job.spring.boot;

/**
 * xxl-job-admin Web API 协议版本（按 HTTP 路径选，非 admin Maven 版本号）。
 *
 * <p>版本对照（官方 xxl-job 分支比对）：
 * <ul>
 *   <li>{@link #V2_X}：2.x、3.0.0 ~ 3.1.x — /login、/jobgroup/save、/jobinfo/add、参数 id</li>
 *   <li>{@link #V3_X}：3.3.0+ — /auth/doLogin、/jobgroup/insert、/jobinfo/insert、参数 ids[]（pageList 尚未完全适配）</li>
 * </ul>
 * 3.2.0 为混合协议（登录 V3 + CRUD V2），当前 starter 无单一枚举覆盖，建议跳过。
 */
public enum AdminVersion {

    /**
     * V2 Web API：官方 admin 2.x、3.0.0、3.1.x；Cookie {@code XXL_JOB_LOGIN_IDENTITY}
     */
    V2_X,

    /**
     * V3 Web API：官方 admin 3.3.0+；Cookie {@code xxl_job_login_token}；需同步升级 xxl-job-core 3.3+
     */
    V3_X

}
