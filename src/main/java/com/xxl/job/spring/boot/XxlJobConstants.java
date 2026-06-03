package com.xxl.job.spring.boot;

/**
 * 任务调度平台常量类
 */
public class XxlJobConstants {

    private XxlJobConstants() {
    }

    public static final String XXL_RPC_COOKIE = "XXL_JOB_LOGIN_IDENTITY";
    public static final String XXL_RPC_ACCESS_TOKEN = "XXL-RPC-ACCESS-TOKEN";

    /**
     * 任务调度平台默认跨平台Http任务处理器
     */
    public static final String DEFAULT_HTTP_JOB_HANDLER = "evaluationHttpJobHandler";

    /**
     * 任务调度平台默认运行模式(BEAN模式)
     */
    public static final String DEFAULT_GLUE_TYPE = "BEAN";


    /**
     * 任务调度平台API-登录
     */
    public static final String LOGIN_GET = "/login";

    /**
     * 任务调度平台API-注销
     */
    public static final String LOGOUT_GET = "/logout";

    /**
     * 任务调度平台API-任务组列表
     */
    public static final String JOBGROUP_PAGELIST = "/jobgroup/pageList";

    /**
     * 任务调度平台API-新增任务组
     */
    public static final String JOBGROUP_SAVE = "/jobgroup/save";

    /**
     * 任务调度平台API-更新任务组
     */
    public static final String JOBGROUP_UPDATE = "/jobgroup/update";

    /**
     * 任务调度平台API-移除任务组
     */
    public static final String JOBGROUP_REMOVE = "/jobgroup/remove";

    /**
     * 任务调度平台API-查询任务组
     */
    public static final String JOBGROUP_GET = "/jobgroup/loadById";


    /**
     * 任务调度平台API-执行器列表
     */
    public static final String JOBINFO_EXECUTOR_LIST = "/jobinfo/executorList";

    /**
     * 任务调度平台API-任务列表
     */
    public static String JOBINFO_PAGELIST = "/jobinfo/pageList";

    /**
     * 任务调度平台API-新增任务
     */
    public static final String JOBINFO_ADD = "/jobinfo/add";

    /**
     * 任务调度平台API-修改任务
     */
    public static final String JOBINFO_UPDATE = "/jobinfo/update";

    /**
     * 任务调度平台API-删除任务
     */
    public static final String JOBINFO_REMOVE = "/jobinfo/remove";

    /**
     * 任务调度平台API-停止任务
     */
    public static final String JOBINFO_STOP = "/jobinfo/stop";

    /**
     * 任务调度平台API-开启任务
     */
    public static final String JOBINFO_START = "/jobinfo/start";

    /**
     * 任务调度平台API-触发一次任务
     */
    public static final String JOBINFO_TRIGGER = "/jobinfo/trigger";


}
