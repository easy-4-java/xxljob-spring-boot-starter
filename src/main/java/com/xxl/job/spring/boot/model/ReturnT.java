package com.xxl.job.spring.boot.model;

import java.io.Serializable;

/**
 * Admin HTTP API 统一响应包装（与 xxl-job-admin JSON 协议对齐）。
 * <p>
 * 独立于 xxl-job-core 版本，避免 core 3.3+ 移除 {@code biz.model.ReturnT} 后编译失败。
 * </p>
 *
 * @param <T> 业务数据类型
 */
public class ReturnT<T> implements Serializable {

    public static final long serialVersionUID = 42L;

    public static final int SUCCESS_CODE = 200;
    public static final int FAIL_CODE = 500;

    public static final ReturnT<String> SUCCESS = new ReturnT<>(null);
    public static final ReturnT<String> FAIL = new ReturnT<>(FAIL_CODE, null);

    private int code;
    private String msg;
    private T content;

    public ReturnT() {
    }

    /**
     * 构造失败/自定义码响应。
     */
    public ReturnT(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    /**
     * 构造成功响应。
     */
    public ReturnT(T content) {
        this.code = SUCCESS_CODE;
        this.content = content;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public T getContent() {
        return content;
    }

    public void setContent(T content) {
        this.content = content;
    }

    @Override
    public String toString() {
        return "ReturnT [code=" + code + ", msg=" + msg + ", content=" + content + "]";
    }

}
