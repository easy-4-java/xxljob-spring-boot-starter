/*
 * Copyright (c) 2018, hiwepy (https://github.com/hiwepy).
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

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.xxl.tool.response.Response;
import com.xxl.job.spring.boot.model.XxlJobGroup;
import com.xxl.job.spring.boot.model.XxlJobGroupList;
import com.xxl.job.spring.boot.model.XxlJobInfo;
import com.xxl.job.spring.boot.model.XxlJobInfoList;
import kong.unirest.core.HttpResponse;
import kong.unirest.core.UnirestInstance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;

@Slf4j
public class XxlJobTemplate {

	public final static String EMPTY = "";

	protected UnirestInstance unirestInstance;
	protected XxlJobProperties properties;
	protected XxlJobAdminProperties adminProperties;
	protected XxlJobExecutorProperties executorProperties;

	private volatile boolean loggedIn = false;

	public XxlJobTemplate(UnirestInstance unirestInstance,
						   XxlJobProperties properties,
						   XxlJobAdminProperties adminProperties,
						   XxlJobExecutorProperties executorProperties) {
		this.unirestInstance = unirestInstance;
		this.properties = properties;
		this.adminProperties = adminProperties;
		this.executorProperties = executorProperties;
	}

	private String buildUrl(String suffix) {
		String address = adminProperties.getAddresses();
		if (address.endsWith("/")) {
			address = address.substring(0, address.length() - 1);
		}
		return address + suffix;
	}

	private void loginIfNeed() {
		if (!loggedIn) {
			this.login(adminProperties.getUsername(), adminProperties.getPassword(), adminProperties.isRemember());
		}
	}

	public Response<String> login(String userName, String password, boolean remember) {
		try {
			String url = buildUrl(XxlJobConstants.LOGIN_GET);
			HttpResponse<String> response = unirestInstance.post(url)
					.header(XxlJobConstants.XXL_RPC_ACCESS_TOKEN, properties.getAccessToken())
					.field("userName", userName)
					.field("password", password)
					.field("ifRemember", remember ? "on" : "off")
					.asString();
			if (response.isSuccess()) {
				log.info("xxl-job login success.");
				loggedIn = true;
				return Response.ofSuccess(response.getBody());
			}
			log.error("xxl-job login fail. status:{}", response.getStatus());
			loggedIn = false;
			return Response.ofFail( response.getStatusText());
		} catch (Exception e) {
			log.error("xxl-job login error.", e);
			return Response.ofFail( e.getMessage());
		}
	}

	public Response<String> logout() {
		try {
			String url = buildUrl(XxlJobConstants.LOGOUT_GET);
			HttpResponse<String> response = unirestInstance.post(url)
					.header(XxlJobConstants.XXL_RPC_ACCESS_TOKEN, properties.getAccessToken())
					.asString();
			if (response.isSuccess()) {
				log.info("xxl-job logout success.");
				loggedIn = false;
				return Response.ofSuccess();
			}
			log.error("xxl-job logout fail.");
			return Response.ofFail( response.getStatusText());
		} catch (Exception e) {
			return Response.ofFail( e.getMessage());
		}
	}

	private HttpResponse<String> doPost(String suffix, Map<String, Object> paramMap, boolean isLoginRequest) {
		String url = buildUrl(suffix);
		if (!isLoginRequest) {
			loginIfNeed();
		}
		return unirestInstance.post(url)
				.header(XxlJobConstants.XXL_RPC_ACCESS_TOKEN, properties.getAccessToken())
				.fields(paramMap)
				.asString();
	}

	private boolean isResponseJson(HttpResponse<String> response) {
		String contentType = response.getHeaders().getFirst("Content-Type");
		return contentType != null && contentType.contains("application/json");
	}

	public Response<XxlJobGroupList> jobInfoGroupList(int start, int length) {
		return this.jobInfoGroupList(start, length, EMPTY, EMPTY);
	}

	public Response<XxlJobGroupList> jobInfoGroupList(int start, int length, String appname, String title) {
		Map<String, Object> paramMap = new HashMap<>(4);
		paramMap.put("start", Math.max(0, start));
		paramMap.put("length", Math.min(length, 5));
		paramMap.put("appname", appname);
		paramMap.put("title", title);
		return doRequestForPageList(XxlJobConstants.JOBGROUP_PAGELIST, paramMap, XxlJobGroupList.class);
	}

	public Response<XxlJobGroup> jobInfoGroup(Integer jobGroupId) {
		if (Objects.isNull(jobGroupId)) {
			return Response.ofFail( "任务执行器主键ID不能为空");
		}
		Map<String, Object> paramMap = new HashMap<>(1);
		paramMap.put("id", jobGroupId);
		return doRequest(XxlJobConstants.JOBGROUP_GET, paramMap);
	}

	public Response<String> addJobGroup(XxlJobGroup jobGroup) {
		if (Objects.isNull(jobGroup)) {
			return Response.ofFail( "任务执行器信息不能为空");
		}
		Map<String, Object> paramMap = JSON.parseObject(JSON.toJSONString(jobGroup), Map.class);
		return doRequest(XxlJobConstants.JOBGROUP_SAVE, paramMap);
	}

	public Response<String> updateJobGroup(XxlJobGroup jobGroup) {
		Map<String, Object> paramMap = JSON.parseObject(JSON.toJSONString(jobGroup), Map.class);
		return doRequest(XxlJobConstants.JOBGROUP_UPDATE, paramMap);
	}

	public Response<String> removeJobGroup(Integer jobGroupId) {
		if (Objects.isNull(jobGroupId)) {
			return Response.ofFail( "任务执行器主键ID不能为空");
		}
		Map<String, Object> paramMap = new HashMap<>(1);
		paramMap.put("id", jobGroupId);
		return doRequest(XxlJobConstants.JOBGROUP_REMOVE, paramMap);
	}

	public Response<XxlJobInfoList> jobInfoList(int start, int length, Integer jobGroup) {
		return this.jobInfoList(start, length, jobGroup, -1, EMPTY, EMPTY, EMPTY);
	}

	public Response<XxlJobInfoList> jobInfoList(int start, int length, Integer jobGroup, Integer triggerStatus) {
		return this.jobInfoList(start, length, jobGroup, triggerStatus, EMPTY, EMPTY, EMPTY);
	}

	public Response<XxlJobInfoList> jobInfoList(int start, int length, Integer jobGroup,
											   Integer triggerStatus, String jobDesc, String executorHandler, String author) {
		if (Objects.isNull(jobGroup)) {
			return Response.ofFail( "任务执行器主键ID不能为空");
		}
		Map<String, Object> paramMap = new HashMap<>(7);
		paramMap.put("start", Math.max(0, start));
		paramMap.put("length", Math.max(length, 5));
		paramMap.put("jobGroup", jobGroup);
		paramMap.put("triggerStatus", triggerStatus);
		paramMap.put("jobDesc", jobDesc);
		paramMap.put("executorHandler", executorHandler);
		paramMap.put("author", author);
		return doRequestForPageList(XxlJobConstants.JOBINFO_PAGELIST, paramMap, XxlJobInfoList.class);
	}

	public Response<String> addUniqueJob(XxlJobInfo jobInfo) {
		if (Objects.isNull(jobInfo)) {
			return Response.ofFail( "任务信息不能为空");
		}
		if (Objects.isNull(jobInfo.getJobGroup())) {
			return Response.ofFail( "任务执行器主键ID不能为空");
		}
		Response<XxlJobInfoList> returnT1 = this.jobInfoList(0, Integer.MAX_VALUE, jobInfo.getJobGroup());
		if (!returnT1.isSuccess()) {
			return Response.ofFail( "获取任务列表失败，失败原因:" + returnT1.getMsg());
		}
		XxlJobInfoList jobInfoList = returnT1.getData();
		if (Objects.isNull(jobInfoList) || CollectionUtils.isEmpty(jobInfoList.getData())) {
			return this.addJob(jobInfo);
		}
		StringUtils.trimWhitespace(jobInfo.getJobDesc());
		if (jobInfoList.getData().stream().anyMatch(job -> StringUtils.trimWhitespace(job.getJobDesc())
				.equals(StringUtils.trimWhitespace(jobInfo.getJobDesc())))) {
			return Response.ofFail( "任务组内已存在相同描述的任务");
		}
		return this.addJob(jobInfo);
	}

	public Response<String> addJob(XxlJobInfo jobInfo) {
		if (Objects.isNull(jobInfo)) {
			return Response.ofFail( "任务信息不能为空");
		}
		if (Objects.isNull(jobInfo.getJobGroup())) {
			return Response.ofFail( "任务执行器主键ID不能为空");
		}
		if (Objects.isNull(jobInfo.getJobDesc())) {
			return Response.ofFail( "任务描述不能为空");
		}
		Map<String, Object> paramMap = JSON.parseObject(JSON.toJSONString(jobInfo), Map.class);
		return doRequest(XxlJobConstants.JOBINFO_ADD, paramMap);
	}

	public Response<String> updateJob(XxlJobInfo jobInfo) {
		if (Objects.isNull(jobInfo)) {
			return Response.ofFail( "任务信息不能为空");
		}
		if (Objects.isNull(jobInfo.getId())) {
			return Response.ofFail( "任务ID不能为空");
		}
		if (Objects.isNull(jobInfo.getJobDesc())) {
			return Response.ofFail( "任务描述不能为空");
		}
		Map<String, Object> paramMap = JSON.parseObject(JSON.toJSONString(jobInfo), Map.class);
		return doRequest(XxlJobConstants.JOBINFO_UPDATE, paramMap);
	}

	public Response<String> removeJob(Integer jobId) {
		if (Objects.isNull(jobId)) {
			return Response.ofFail( "任务ID不能为空");
		}
		Map<String, Object> paramMap = new HashMap<>(1);
		paramMap.put("id", jobId);
		return doRequest(XxlJobConstants.JOBINFO_REMOVE, paramMap);
	}

	public Response<String> stopJob(Integer jobId) {
		if (Objects.isNull(jobId)) {
			return Response.ofFail( "任务ID不能为空");
		}
		Map<String, Object> paramMap = new HashMap<>(1);
		paramMap.put("id", jobId);
		return doRequest(XxlJobConstants.JOBINFO_STOP, paramMap);
	}

	public Response<String> startJob(Integer jobId) {
		if (Objects.isNull(jobId)) {
			return Response.ofFail( "任务ID不能为空");
		}
		Map<String, Object> paramMap = new HashMap<>(1);
		paramMap.put("id", jobId);
		return doRequest(XxlJobConstants.JOBINFO_START, paramMap);
	}

	public Response<String> triggerJob(XxlJobInfo jobInfo) {
		if (Objects.isNull(jobInfo)) {
			return Response.ofFail( "任务信息不能为空");
		}
		return this.triggerJob(jobInfo.getId(), jobInfo.getExecutorParam());
	}

	public Response<String> triggerJob(Integer jobInfoId, String executorParam) {
		if (Objects.isNull(jobInfoId)) {
			return Response.ofFail( "任务ID不能为空");
		}
		Map<String, Object> paramMap = new HashMap<>(2);
		paramMap.put("id", jobInfoId);
		paramMap.put("executorParam", executorParam);
		return doRequest(XxlJobConstants.JOBINFO_TRIGGER, paramMap);
	}

	private <T> Response<T> doRequest(String suffix, Map<String, Object> paramMap) {
		try {
			HttpResponse<String> response = doPost(suffix, paramMap, false);
			if (response.isSuccess()) {
				log.info("xxl-job request successful.");
				String body = response.getBody();
				log.debug("xxl-job response body: {} .", body);
				if (isResponseJson(response)) {
					Response<T> returnT = JSON.parseObject(body, new TypeReference<Response<T>>() {});
					return returnT;
				}
			}
			log.error("xxl-job request fail. status:{}", response.getStatus());
			return Response.ofFail( response.getStatusText());
		} catch (Exception e) {
			return Response.ofFail( e.getMessage());
		}
	}

	private <T> Response<T> doRequest(String suffix, Map<String, Object> paramMap, Class<T> objectClass) {
		try {
			HttpResponse<String> response = doPost(suffix, paramMap, false);
			if (response.isSuccess()) {
				log.info("xxl-job request successful.");
				String body = response.getBody();
				log.debug("xxl-job response body: {} .", body);
				if (isResponseJson(response)) {
					T rt = JSON.parseObject(body, objectClass);
					return Response.ofSuccess(rt);
				}
			}
			log.error("xxl-job request fail. status:{}", response.getStatus());
			return Response.ofFail( response.getStatusText());
		} catch (Exception e) {
			return Response.ofFail( e.getMessage());
		}
	}

	/**
	 * 处理 pageList 接口返回的非 Response 包装的响应（直接是 Map 格式）
	 */
	private <T> Response<T> doRequestForPageList(String suffix, Map<String, Object> paramMap, Class<T> objectClass) {
		try {
			HttpResponse<String> response = doPost(suffix, paramMap, false);
			if (response.isSuccess()) {
				log.info("xxl-job pageList request successful.");
				String body = response.getBody();
				log.debug("xxl-job pageList response body: {} .", body);
				if (isResponseJson(response)) {
					T result = JSON.parseObject(body, objectClass);
					return Response.ofSuccess(result);
				}
			}
			log.error("xxl-job pageList request fail. status:{}", response.getStatus());
			return Response.ofFail( response.getStatusText());
		} catch (Exception e) {
			return Response.ofFail( e.getMessage());
		}
	}

}
