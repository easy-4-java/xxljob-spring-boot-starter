package com.xxl.job.spring.boot.admin;

import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.spring.boot.AdminVersion;
import com.xxl.job.spring.boot.XxlJobAdminProperties;
import com.xxl.job.spring.boot.XxlJobConstants;
import com.xxl.job.spring.boot.XxlJobProperties;
import kong.unirest.HttpResponse;
import kong.unirest.HttpRequestWithBody;
import kong.unirest.UnirestInstance;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 基于 Unirest 的 xxl-job-admin HTTP 客户端默认实现。
 * <p>
 * 禁用 Unirest 内置 Cookie 管理，改用手工 {@link XxlJobAdminCookieStore} 解析 Set-Cookie，
 * 规避 remember-me Cookie 无效 Expires 导致 Apache HttpClient 拒绝解析的问题。
 * </p>
 */
@Slf4j
public class DefaultXxlJobAdminClient implements XxlJobAdminClient {

    private final UnirestInstance unirestInstance;
    private final XxlJobProperties properties;
    private final XxlJobAdminProperties adminProperties;
    private final XxlJobAdminCookieStore cookieStore = new XxlJobAdminCookieStore();

    private final Object loginLock = new Object();
    private volatile boolean authenticated = false;

    public DefaultXxlJobAdminClient(UnirestInstance unirestInstance,
                                    XxlJobProperties properties,
                                    XxlJobAdminProperties adminProperties) {
        this.unirestInstance = unirestInstance;
        this.properties = properties;
        this.adminProperties = adminProperties;
    }

    @Override
    public AdminVersion version() {
        AdminVersion v = adminProperties.getVersion();
        return v != null ? v : AdminVersion.V2_X;
    }

    @Override
    public boolean isV3() {
        return version() == AdminVersion.V3_X;
    }

    @Override
    public boolean login(String userName, String password, boolean remember) {
        try {
            String url = buildUrl(XxlJobConstants.loginPath(version()));
            log.info("xxl-job login POST {} (v={})", url, version());
            HttpResponse<String> response = executePost(url,
                    Map.of("userName", userName, "password", password, "ifRemember", remember ? "on" : "off"),
                    false);
            log.info("xxl-job login response: status={}, body={}", response.getStatus(), safeBody(response));
            if (response.isSuccess()) {
                absorbCookies(response);
                authenticated = true;
                log.info("xxl-job login SUCCESS");
                return true;
            }
            log.error("xxl-job login FAIL: status={}", response.getStatus());
            authenticated = false;
            cookieStore.clear();
            return false;
        } catch (Exception e) {
            log.error("xxl-job login ERROR: {}", e.getMessage(), e);
            authenticated = false;
            cookieStore.clear();
            return false;
        }
    }

    @Override
    public void loginIfNeeded() {
        if (authenticated) {
            return;
        }
        synchronized (loginLock) {
            if (authenticated) {
                return;
            }
            login(adminProperties.getUsername(), adminProperties.getPassword(), adminProperties.isRemember());
        }
    }

    @Override
    public ReturnT<String> logout() {
        try {
            String url = buildUrl(XxlJobConstants.logoutPath(version()));
            HttpResponse<String> response = executePost(url, Map.of(), false);
            authenticated = false;
            cookieStore.clear();
            if (response.isSuccess()) {
                log.info("xxl-job logout success.");
                return ReturnT.SUCCESS;
            }
            log.error("xxl-job logout fail.");
            return new ReturnT<>(ReturnT.FAIL_CODE, response.getStatusText());
        } catch (Exception e) {
            return new ReturnT<>(ReturnT.FAIL_CODE, e.getMessage());
        }
    }

    @Override
    public XxlJobAdminHttpResponse postForm(String pathSuffix, Map<String, Object> paramMap) {
        loginIfNeeded();
        XxlJobAdminHttpResponse response = toAdminResponse(executePost(buildUrl(pathSuffix), paramMap, true));
        // session 过期：返回 HTML 而非 JSON，重新登录后重试一次
        if (response.isSuccess() && !response.isJson()) {
            log.warn("xxl-job session expired, re-login. body:{}", response.safeBody(200));
            authenticated = false;
            cookieStore.clear();
            loginIfNeeded();
            return toAdminResponse(executePost(buildUrl(pathSuffix), paramMap, true));
        }
        return response;
    }

    @Override
    public String buildUrl(String pathSuffix) {
        String address = adminProperties.getAddresses();
        if (address.endsWith("/")) {
            address = address.substring(0, address.length() - 1);
        }
        return address + pathSuffix;
    }

    /**
     * 执行 POST 请求，手工携带 Cookie 头。
     */
    private HttpResponse<String> executePost(String url, Map<String, Object> paramMap, boolean attachCookies) {
        HttpRequestWithBody req = unirestInstance.post(url)
                .header(XxlJobConstants.XXL_RPC_ACCESS_TOKEN, properties.getAccessToken());
        if (attachCookies) {
            String cookieHeader = cookieStore.buildCookieHeader();
            if (cookieHeader != null && !cookieHeader.isEmpty()) {
                req.header("Cookie", cookieHeader);
            }
        }
        HttpResponse<String> response = req.fields(paramMap).asString();
        if (attachCookies) {
            absorbCookies(response);
        }
        return response;
    }

    /**
     * 从响应头吸收 Set-Cookie。
     */
    private void absorbCookies(HttpResponse<String> response) {
        cookieStore.absorbFromHeaders(response.getHeaders().get("Set-Cookie"));
    }

    private XxlJobAdminHttpResponse toAdminResponse(HttpResponse<String> response) {
        String body = null;
        try {
            body = response.getBody();
        } catch (Exception ignored) {
        }
        String contentType = response.getHeaders().getFirst("Content-Type");
        return new XxlJobAdminHttpResponse(response.getStatus(), response.getStatusText(), body, contentType);
    }

    private String safeBody(HttpResponse<String> r) {
        try {
            String b = r.getBody();
            return b != null ? b.substring(0, Math.min(b.length(), 200)) : "null";
        } catch (Exception e) {
            return "err";
        }
    }

}
