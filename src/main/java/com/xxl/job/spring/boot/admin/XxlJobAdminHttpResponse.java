package com.xxl.job.spring.boot.admin;

import lombok.Getter;

/**
 * xxl-job-admin HTTP 响应封装，屏蔽底层 HTTP 客户端实现细节。
 */
@Getter
public class XxlJobAdminHttpResponse {

    private final int status;
    private final String statusText;
    private final String body;
    private final String contentType;

    public XxlJobAdminHttpResponse(int status, String statusText, String body, String contentType) {
        this.status = status;
        this.statusText = statusText;
        this.body = body;
        this.contentType = contentType;
    }

    /**
     * 判断 HTTP 状态码是否为 2xx。
     */
    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }

    /**
     * 判断响应体是否为 JSON（用于检测 session 过期后返回的 HTML 登录页）。
     */
    public boolean isJson() {
        return contentType != null && contentType.contains("application/json");
    }

    /**
     * 生成可读的错误信息。
     */
    public String errorMessage() {
        if (statusText != null && !statusText.isEmpty()) {
            return "HTTP " + status + " " + statusText;
        }
        if (body != null && !body.isEmpty()) {
            return "HTTP " + status + ", body: " + body.substring(0, Math.min(body.length(), 200));
        }
        return "HTTP " + status;
    }

    /**
     * 安全截取响应体用于日志输出。
     */
    public String safeBody(int maxLen) {
        if (body == null) {
            return "null";
        }
        return body.substring(0, Math.min(body.length(), maxLen));
    }

}
