package com.xxl.job.spring.boot;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从 xxl-job-admin 登录响应中解析会话 Cookie。
 * <p>
 * 当 {@code remember=true} 时，admin 可能下发 {@code Max-Age=Integer.MAX_VALUE}，
 * 部分 HTTP 客户端（如 OkHttp）会打出 {@code Invalid cookie Expires=2094} 并丢弃 Cookie，
 * 导致后续 API 返回 302。此处直接从 {@code Set-Cookie} 头解析 name=value，绕过客户端 Cookie 解析限制。
 * </p>
 */
public final class AdminSessionCookieSupport {

    /** v2 admin 登录 Cookie */
    public static final String V2_IDENTITY_COOKIE = XxlJobConstants.XXL_RPC_COOKIE;

    /** v3 admin（xxl-sso）登录 Cookie */
    public static final String V3_SSO_TOKEN_COOKIE = "xxl_job_login_token";

    private AdminSessionCookieSupport() {
    }

    /**
     * 解析响应头中的全部 Set-Cookie，仅保留 name=value（忽略 Expires/Max-Age 等属性）。
     *
     * @param setCookieHeaders Set-Cookie 头列表
     * @return 不可变 Cookie 映射
     */
    public static Map<String, String> parseSetCookieHeaders(List<String> setCookieHeaders) {
        if (setCookieHeaders == null || setCookieHeaders.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> cookies = new LinkedHashMap<>();
        for (String header : setCookieHeaders) {
            parseOneSetCookie(header, cookies);
        }
        return cookies.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(cookies);
    }

    /**
     * 判断是否包含可用于鉴权的 Cookie。
     */
    public static boolean hasAuthCookie(Map<String, String> cookies) {
        if (cookies == null || cookies.isEmpty()) {
            return false;
        }
        return cookies.containsKey(V3_SSO_TOKEN_COOKIE) || cookies.containsKey(V2_IDENTITY_COOKIE);
    }

    private static void parseOneSetCookie(String header, Map<String, String> into) {
        if (header == null || header.isBlank()) {
            return;
        }
        String pair = header.split(";", 2)[0].trim();
        int eq = pair.indexOf('=');
        if (eq <= 0) {
            return;
        }
        String name = pair.substring(0, eq).trim();
        String value = pair.substring(eq + 1).trim();
        if (!name.isEmpty() && !value.isEmpty()) {
            into.put(name, value);
        }
    }
}
