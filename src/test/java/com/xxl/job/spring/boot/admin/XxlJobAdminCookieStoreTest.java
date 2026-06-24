package com.xxl.job.spring.boot.admin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * XxlJobAdminCookieStore 单元测试：验证对 remember-me 无效 Expires Cookie 的容错解析。
 */
@DisplayName("XxlJobAdminCookieStore 单元测试")
class XxlJobAdminCookieStoreTest {

    @Test
    @DisplayName("应解析 remember-me 无效 Expires 的 Set-Cookie")
    void shouldParseRememberMeCookieWithInvalidExpires() {
        XxlJobAdminCookieStore store = new XxlJobAdminCookieStore();
        // xxl-job-admin remember-me 典型 Cookie：Max-Age=2147483647 导致 Apache HttpClient 拒绝
        String rememberMeCookie = "XXL_JOB_LOGIN_IDENTITY=7b226964223a312c22757365726e616d65223a2261646d696e227d; "
                + "Path=/; Max-Age=2147483647; Expires=Invalid, 19 Jan 2038 03:14:07 GMT";
        store.absorbOne(rememberMeCookie);

        assertThat(store.get("XXL_JOB_LOGIN_IDENTITY")).isNotNull();
        assertThat(store.buildCookieHeader()).contains("XXL_JOB_LOGIN_IDENTITY=");
    }

    @Test
    @DisplayName("应同时存储多条 Set-Cookie")
    void shouldStoreMultipleCookies() {
        XxlJobAdminCookieStore store = new XxlJobAdminCookieStore();
        store.absorbFromHeaders(Arrays.asList(
                "XXL_JOB_LOGIN_IDENTITY=mock-identity; Path=/",
                "xxl_job_login_token=mock-token; Path=/; HttpOnly"
        ));

        String header = store.buildCookieHeader();
        assertThat(header).contains("XXL_JOB_LOGIN_IDENTITY=mock-identity");
        assertThat(header).contains("xxl_job_login_token=mock-token");
    }

    @Test
    @DisplayName("clear 应清空所有 Cookie")
    void shouldClearCookies() {
        XxlJobAdminCookieStore store = new XxlJobAdminCookieStore();
        store.absorbOne("XXL_JOB_LOGIN_IDENTITY=abc; Path=/");
        store.clear();
        assertThat(store.isEmpty()).isTrue();
        assertThat(store.buildCookieHeader()).isNull();
    }

}
