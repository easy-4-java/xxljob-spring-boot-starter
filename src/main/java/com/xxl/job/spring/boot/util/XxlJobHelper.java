package com.xxl.job.spring.boot.util;

import com.xxl.job.core.glue.GlueTypeEnum;
import com.xxl.job.core.constant.ExecutorBlockStrategyEnum;
import com.xxl.job.spring.boot.executor.ExecutorRouteStrategyEnum;
import com.xxl.job.spring.boot.executor.ExecutorTriggerPeriodEnum;
import com.xxl.job.spring.boot.executor.ScheduleTypeEnum;
import com.xxl.job.spring.boot.model.XxlJobInfo;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class XxlJobHelper {

    private XxlJobHelper() {}

    /**
     * 构建定时调度平台jobInfo
     * @param jobGroup 调度组
     * @param scheduleType 调度类型
     * @param scheduleConf 调度配置
     * @param jobDesc 任务描述
     * @param author 任务负责人
     * @param executorHandler 行器，任务Handler名称
     * @param glueType GLUE类型	#com.xxl.job.core.glue.GlueTypeEnum
     * @param executorRouteStrategy 执行器路由策略 #com.xxl.job.sping.boot.executor.ExecutorRouteStrategyEnum
     * @param executorBlockStrategy 阻塞处理策略 #com.xxl.job.sping.boot.executor.ExecutorBlockStrategyEnum
     * @param callbackUri 回调url
     */
    public static XxlJobInfo buildJobInfo(Integer jobGroup,
                                          ScheduleTypeEnum scheduleType,
                                          String scheduleConf,
                                          String jobDesc,
                                          String author,
                                          String executorHandler,
                                          GlueTypeEnum glueType,
                                          ExecutorRouteStrategyEnum executorRouteStrategy,
                                          ExecutorBlockStrategyEnum executorBlockStrategy,
                                          String callbackUri) {
        XxlJobInfo jobInfo = new XxlJobInfo();
        jobInfo.setJobGroup(jobGroup);
        jobInfo.setJobDesc(jobDesc);
        jobInfo.setAuthor(author);
        jobInfo.setExecutorHandler(executorHandler);
        jobInfo.setGlueType(glueType.name());
        jobInfo.setExecutorRouteStrategy(executorRouteStrategy.name());
        jobInfo.setExecutorBlockStrategy(executorBlockStrategy.name());
        jobInfo.setExecutorParam(callbackUri);
        jobInfo.setScheduleType(scheduleType.name());
        jobInfo.setScheduleConf(scheduleConf);
        jobInfo.setJobCron(scheduleConf);
        return jobInfo;
    }

    /**
     * 将指定的日期和时间转换为Cron表达式
     *
     * @param day    日期
     * @param period 周期
     * @param time   时间
     * @return Cron表达式
     */
    public static String getCronExpression(Integer day, ExecutorTriggerPeriodEnum period, Date time) {
        StringBuilder buffer = new StringBuilder();
        String triggerTimeStr = getTriggerTimeCronStr(time);
        String[] arr = triggerTimeStr.split(":");
        String hour = arr[0];
        String minute = arr[1];
        String second = arr[2];
        buffer.append(second).append(" ").append(minute).append(" ").append(hour);
        switch(period) {
            case MONTH : buffer.append(" ").append(day).append(" * ?"); break;
            case WEEK : buffer.append(" ? * ").append(day); break;
            case DAILY : buffer.append(" * * ?"); break;
        }
        return buffer.toString();
    }

    /**
     * 获取触发时间Cron表达式
     *
     * @param triggerTime 触发时间
     * @return 触发时间Cron表达式
     */
    public static String getTriggerTimeCronStr(Date triggerTime) {
        if (null == triggerTime) {
            throw new IllegalArgumentException("triggerTime参数错误!");
        }
        DateFormat format = new SimpleDateFormat("HH:mm:ss");
        return format.format(triggerTime);
    }

}
