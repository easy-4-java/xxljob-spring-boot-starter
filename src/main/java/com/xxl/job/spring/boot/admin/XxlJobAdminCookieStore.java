package com.xxl.job.spring.boot.admin;

import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 线程安全的 Cookie 存储，手工解析 Set-Cookie 头。
 * <p>
 * 绕过 Apache HttpClient 对无效 Expires 的严格校验（remember-me 时 Max-Age=2147483647
 * 会导致 Cookie 解析失败，后续请求无 Cookie 被 302 重定向到登录页）。
 * </p>
 */
@Slf4j
public class XxlJobAdminCookieStore {

    private final Map<String, String> cookies = new ConcurrentHashMap<>();

    /**
     * 从响应 Set-Cookie 头吸收 Cookie（仅取 name=value，忽略 Expires/Max-Age 等属性）。
     */
    public void absorbFromHeaders(Collection<String> setCookieHeaders) {
        if (setCookieHeaders == null || setCookieHeaders.isEmpty()) {
            return;
        }
        for (String header : setCookieHeaders) {
            absorbOne(header);
        }
    }

    /**
     * 解析单条 Set-Cookie 头。
     */
    public void absorbOne(String setCookieHeader) {
        if (setCookieHeader == null || setCookieHeader.isEmpty()) {
            return;
        }
        String nameValue = setCookieHeader.split(";", 2)[0].trim();
        int eq = nameValue.indexOf('=');
        if (eq <= 0) {
            return;
        }
        String name = nameValue.substring(0, eq).trim();
        String value = nameValue.substring(eq + 1).trim();
        if (!name.isEmpty()) {
            cookies.put(name, value);
            log.debug("xxl-job cookie stored: {}", name);
        }
    }

    /**
     * 是否已存储指定名称的登录 Cookie。
     */
    public boolean hasLoginCookie(String name) {
        return name != null && cookies.containsKey(name);
    }

    /**
     * 构建仅含指定名称的请求 Cookie 头；未命中时回退为全部 Cookie。
     */
    public String buildCookieHeader(String preferredName) {
        if (preferredName != null && cookies.containsKey(preferredName)) {
            return preferredName + "=" + cookies.get(preferredName);
        }
        return buildCookieHeader();
    }

    /**
     * 构建请求 Cookie 头（全部已存 Cookie）。
     */
    public String buildCookieHeader() {
        if (cookies.isEmpty()) {
            return null;
        }
        return cookies.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("; "));
    }

    /**
     * 获取指定名称的 Cookie 值。
     */
    public String get(String name) {
        return cookies.get(name);
    }

    /**
     * 清空所有 Cookie（登出或 session 过期时调用）。
     */
    public void clear() {
        cookies.clear();
    }

    /**
     * 是否已存储 Cookie。
     */
    public boolean isEmpty() {
        return cookies.isEmpty();
    }

}
