package com.xxl.job.spring.boot;

public class XxlJobConstants {

    private XxlJobConstants() {
    }

    public static final String XXL_RPC_COOKIE = "XXL_JOB_LOGIN_IDENTITY";
    public static final String XXL_RPC_ACCESS_TOKEN = "XXL-RPC-ACCESS-TOKEN";

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

    // === xxl-job-admin 2.x 路径 ===
    public static final String LOGIN_GET_V2 = "/login";
    public static final String LOGOUT_GET_V2 = "/logout";
    public static final String JOBGROUP_SAVE_V2 = "/jobgroup/save";
    public static final String JOBGROUP_REMOVE_V2 = "/jobgroup/remove";
    public static final String JOBINFO_ADD_V2 = "/jobinfo/add";
    public static final String JOBINFO_REMOVE_V2 = "/jobinfo/remove";

    // === xxl-job-admin 3.x 路径 ===
    public static final String LOGIN_GET_V3 = "/auth/doLogin";
    public static final String LOGOUT_GET_V3 = "/auth/logout";
    public static final String JOBGROUP_SAVE_V3 = "/jobgroup/insert";
    public static final String JOBGROUP_REMOVE_V3 = "/jobgroup/delete";
    public static final String JOBINFO_ADD_V3 = "/jobinfo/insert";
    public static final String JOBINFO_REMOVE_V3 = "/jobinfo/delete";

}
