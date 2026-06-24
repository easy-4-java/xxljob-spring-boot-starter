package com.xxl.job.spring.boot;

import java.util.HashMap;
import java.util.Map;

public class XxlJobConstants {

    private XxlJobConstants() {
    }

    /** V2 admin 登录 Cookie 名 */
    public static final String COOKIE_LOGIN_IDENTITY = "XXL_JOB_LOGIN_IDENTITY";
    /** V3 admin（xxl-sso）登录 Cookie 名 */
    public static final String COOKIE_LOGIN_TOKEN = "xxl_job_login_token";

    /** @deprecated 使用 {@link #COOKIE_LOGIN_IDENTITY} */
    @Deprecated
    public static final String XXL_RPC_COOKIE = COOKIE_LOGIN_IDENTITY;
    public static final String XXL_RPC_ACCESS_TOKEN = "XXL-JOB-ACCESS-TOKEN";

    public static final String DEFAULT_HTTP_JOB_HANDLER = "evaluationHttpJobHandler";
    public static final String DEFAULT_GLUE_TYPE = "BEAN";

    // === 公共路径（v2/v3 相同） ===
    public static final String JOBGROUP_PAGELIST = "/jobgroup/pageList";
    public static final String JOBGROUP_UPDATE = "/jobgroup/update";
    public static final String JOBGROUP_GET = "/jobgroup/loadById";
    public static final String JOBINFO_EXECUTOR_LIST = "/jobinfo/executorList";
    public static String JOBINFO_PAGELIST = "/jobinfo/pageList";
    public static final String JOBINFO_UPDATE = "/jobinfo/update";
    public static final String JOBINFO_STOP = "/jobinfo/stop";
    public static final String JOBINFO_START = "/jobinfo/start";
    public static final String JOBINFO_TRIGGER = "/jobinfo/trigger";

    // === 版本差异路径 ===
    private static final Map<AdminVersion, String> LOGIN_PATHS = new HashMap<>();
    private static final Map<AdminVersion, String> LOGOUT_PATHS = new HashMap<>();
    private static final Map<AdminVersion, String> JOBGROUP_SAVE_PATHS = new HashMap<>();
    private static final Map<AdminVersion, String> JOBGROUP_REMOVE_PATHS = new HashMap<>();
    private static final Map<AdminVersion, String> JOBINFO_ADD_PATHS = new HashMap<>();
    private static final Map<AdminVersion, String> JOBINFO_REMOVE_PATHS = new HashMap<>();

    static {
        // 登录/登出：V2_X 用 /login；V3_2_X / V3_X 用 /auth/*
        LOGIN_PATHS.put(AdminVersion.V2_X, "/login");
        LOGIN_PATHS.put(AdminVersion.V3_2_X, "/auth/doLogin");
        LOGIN_PATHS.put(AdminVersion.V3_X, "/auth/doLogin");
        LOGOUT_PATHS.put(AdminVersion.V2_X, "/logout");
        LOGOUT_PATHS.put(AdminVersion.V3_2_X, "/auth/logout");
        LOGOUT_PATHS.put(AdminVersion.V3_X, "/auth/logout");
        // CRUD：V2_X / V3_2_X 用 save/add/remove；V3_X 用 insert/delete
        JOBGROUP_SAVE_PATHS.put(AdminVersion.V2_X, "/jobgroup/save");
        JOBGROUP_SAVE_PATHS.put(AdminVersion.V3_2_X, "/jobgroup/save");
        JOBGROUP_SAVE_PATHS.put(AdminVersion.V3_X, "/jobgroup/insert");
        JOBGROUP_REMOVE_PATHS.put(AdminVersion.V2_X, "/jobgroup/remove");
        JOBGROUP_REMOVE_PATHS.put(AdminVersion.V3_2_X, "/jobgroup/remove");
        JOBGROUP_REMOVE_PATHS.put(AdminVersion.V3_X, "/jobgroup/delete");
        JOBINFO_ADD_PATHS.put(AdminVersion.V2_X, "/jobinfo/add");
        JOBINFO_ADD_PATHS.put(AdminVersion.V3_2_X, "/jobinfo/add");
        JOBINFO_ADD_PATHS.put(AdminVersion.V3_X, "/jobinfo/insert");
        JOBINFO_REMOVE_PATHS.put(AdminVersion.V2_X, "/jobinfo/remove");
        JOBINFO_REMOVE_PATHS.put(AdminVersion.V3_2_X, "/jobinfo/remove");
        JOBINFO_REMOVE_PATHS.put(AdminVersion.V3_X, "/jobinfo/delete");
    }

    /**
     * 登录接口路径。
     */
    public static String loginPath(AdminVersion v) {
        return LOGIN_PATHS.getOrDefault(v, "/login");
    }

    /**
     * 登出接口路径。
     */
    public static String logoutPath(AdminVersion v) {
        return LOGOUT_PATHS.getOrDefault(v, "/logout");
    }

    /**
     * 执行器组新增路径。
     */
    public static String jobGroupSavePath(AdminVersion v) {
        return JOBGROUP_SAVE_PATHS.getOrDefault(v, "/jobgroup/save");
    }

    /**
     * 执行器组删除路径。
     */
    public static String jobGroupRemovePath(AdminVersion v) {
        return JOBGROUP_REMOVE_PATHS.getOrDefault(v, "/jobgroup/remove");
    }

    /**
     * 任务新增路径。
     */
    public static String jobInfoAddPath(AdminVersion v) {
        return JOBINFO_ADD_PATHS.getOrDefault(v, "/jobinfo/add");
    }

    /**
     * 任务删除路径。
     */
    public static String jobInfoRemovePath(AdminVersion v) {
        return JOBINFO_REMOVE_PATHS.getOrDefault(v, "/jobinfo/remove");
    }

}
