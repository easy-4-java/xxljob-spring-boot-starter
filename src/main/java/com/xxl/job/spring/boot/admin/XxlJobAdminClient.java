package com.xxl.job.spring.boot.admin;

import com.xxl.job.spring.boot.model.ReturnT;
import com.xxl.job.spring.boot.AdminVersion;

import java.util.Map;

/**
 * xxl-job-admin 统一 HTTP 客户端接口。
 * <p>
 * 职责：登录与会话管理、表单 POST、版本感知路径、线程安全 Cookie 存储。
 * </p>
 */
public interface XxlJobAdminClient {

    /**
     * 当前配置的 admin 版本。
     */
    AdminVersion version();

    /**
     * 是否为完整 V3 admin API（3.3.0+）。
     */
    boolean isV3();

    /**
     * 登录 admin，成功时缓存 Cookie。
     */
    boolean login(String userName, String password, boolean remember);

    /**
     * 使用配置中的账号密码登录（若尚未登录）。
     */
    void loginIfNeeded();

    /**
     * 登出并清除 Cookie。
     */
    ReturnT<String> logout();

    /**
     * 向 admin 发送表单 POST（自动登录、session 过期自动重试一次）。
     *
     * @param pathSuffix 路径后缀，如 {@code /jobgroup/pageList}
     */
    XxlJobAdminHttpResponse postForm(String pathSuffix, Map<String, Object> paramMap);

    /**
     * 拼接完整 URL。
     */
    String buildUrl(String pathSuffix);

}
