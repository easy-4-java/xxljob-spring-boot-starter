package com.xxl.job.spring.boot;

/**
 * xxl-job-admin Web API 协议版本（按 HTTP 路径选，非 admin Maven 版本号）。
 *
 * <p>版本对照（官方 xxl-job 分支比对）：
 * <ul>
 *   <li>{@link #V2_X}：2.x、3.0.0 ~ 3.1.x — /login、/jobgroup/save、/jobinfo/add、参数 id、Cookie XXL_JOB_LOGIN_IDENTITY</li>
 *   <li>{@link #V3_2_X}：3.2.0 混合协议 — 登录 /auth/doLogin + CRUD 仍 V2 路径、参数 id、Cookie xxl_job_login_token</li>
 *   <li>{@link #V3_X}：3.3.0+ 完整 V3 — /auth/doLogin、insert/delete、ids[]、offset/pagesize、Response JSON</li>
 * </ul>
 */
public enum AdminVersion {

    /**
     * V2 Web API：官方 admin 2.x、3.0.0、3.1.x；Cookie {@code XXL_JOB_LOGIN_IDENTITY}
     */
    V2_X,

    /**
     * 3.2.0 混合协议：登录/登出 V3（/auth/doLogin），CRUD 与分页仍 V2；Cookie {@code xxl_job_login_token}
     */
    V3_2_X,

    /**
     * V3 Web API：官方 admin 3.3.0+；Cookie {@code xxl_job_login_token}；须同步升级 xxl-job-core 3.3+
     */
    V3_X;

    /**
     * 是否使用 V3 登录路径（/auth/doLogin）与 SSO Cookie。
     */
    public boolean usesV3Login() {
        return this == V3_2_X || this == V3_X;
    }

    /**
     * 是否使用完整 V3 CRUD 协议（insert/delete、ids[]、offset/pagesize、Response JSON）。
     */
    public boolean usesV3FullApi() {
        return this == V3_X;
    }

    /**
     * 登录会话 Cookie 名称。
     */
    public String loginCookieName() {
        return usesV3Login()
                ? XxlJobConstants.COOKIE_LOGIN_TOKEN
                : XxlJobConstants.COOKIE_LOGIN_IDENTITY;
    }

}
