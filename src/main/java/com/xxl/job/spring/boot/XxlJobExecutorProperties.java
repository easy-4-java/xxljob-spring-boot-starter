/*
 * Copyright (c) 2017, hiwepy (https://github.com/hiwepy).
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.xxl.job.spring.boot;

import com.xxl.job.core.XxlJobConstants;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class XxlJobExecutorProperties {

	public static final String PREFIX = "xxl.job.executor";

	/**
	 * Enable XXL-Job Executor.
	 */
	private boolean enabled = Boolean.FALSE;

	/**
	 * 	执行器AppName [必填]：执行器心跳注册分组依据；为空则关闭自动注册.
	 */
	private String appname = "";

	private String title = "";

	private String address = null;

	/**
	 * 	执行器IP [选填]：默认为空表示自动获取IP，多网卡时可手动设置指定IP，该IP不会绑定Host仅作为通讯实用；地址信息用于 "执行器注册" 和 "调度中心请求并触发任务"；
	 */
	private String ip = "";
	/**
	 * 	执行器端口号 [选填]：小于等于0则自动获取；默认端口为9999，单机部署多个执行器时，注意要配置不同执行器端口；
	 */
	private String port = "-1";
	/**
	 * 	执行器运行日志文件存储磁盘路径 [选填] ：需要对该路径拥有读写权限；为空则使用默认路径；
	 */
	private String logPath = "/data/applogs/xxl-job/jobhandler";
	/**
	 * 	执行器日志保存天数 [选填] ：值大于3时生效，启用执行器Log文件定期清理功能，否则不生效；
	 */
	private int logRetentionDays = 30;
	/**
	 * 执行器，任务Handler名称
	 */
	private String defaultExecutorHandler = XxlJobConstants.DEFAULT_HTTP_JOB_HANDLER;



}
