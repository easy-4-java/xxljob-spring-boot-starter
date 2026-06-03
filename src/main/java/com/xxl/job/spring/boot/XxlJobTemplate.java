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
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.spring.boot.model.XxlJobGroup;
import com.xxl.job.spring.boot.model.XxlJobGroupList;
import com.xxl.job.spring.boot.model.XxlJobInfo;
import com.xxl.job.spring.boot.model.XxlJobInfoList;
import kong.unirest.HttpResponse;
import kong.unirest.UnirestInstance;
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

	public ReturnT<String> login(String userName, String password, boolean remember) {
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
				return new ReturnT<String>(response.getBody());
			}
			log.error("xxl-job login fail. status:{}", response.getStatus());
			loggedIn = false;
			return new ReturnT<String>(ReturnT.FAIL_CODE, response.getStatusText());
		} catch (Exception e) {
			log.error("xxl-job login error.", e);
			return new ReturnT<String>(ReturnT.FAIL_CODE, e.getMessage());
		}
	}

	public ReturnT<String> logout() {
		try {
			String url = buildUrl(XxlJobConstants.LOGOUT_GET);
			HttpResponse<String> response = unirestInstance.post(url)
					.header(XxlJobConstants.XXL_RPC_ACCESS_TOKEN, properties.getAccessToken())
					.asString();
			if (response.isSuccess()) {
				log.info("xxl-job logout success.");
				loggedIn = false;
				return ReturnT.SUCCESS;
			}
			log.error("xxl-job logout fail.");
			return new ReturnT<String>(ReturnT.FAIL_CODE, response.getStatusText());
		} catch (Exception e) {
			return new ReturnT<String>(ReturnT.FAIL_CODE, e.getMessage());
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

	public ReturnT<XxlJobGroupList> jobInfoGroupList(int start, int length) {
		return this.jobInfoGroupList(start, length, EMPTY, EMPTY);
	}

	public ReturnT<XxlJobGroupList> jobInfoGroupList(int start, int length, String appname, String title) {
		Map<String, Object> paramMap = new HashMap<>(4);
		paramMap.put("start", Math.max(0, start));
		paramMap.put("length", Math.min(length, 5));
		paramMap.put("appname", appname);
		paramMap.put("title", title);
		return doRequestForPageList(XxlJobConstants.JOBGROUP_PAGELIST, paramMap, XxlJobGroupList.class);
	}

	public ReturnT<XxlJobGroup> jobInfoGroup(Integer jobGroupId) {
		if (Objects.isNull(jobGroupId)) {
			return new ReturnT<>(ReturnT.FAIL_CODE, "任务执行器主键ID不能为空");
		}
		Map<String, Object> paramMap = new HashMap<>(1);
		paramMap.put("id", jobGroupId);
		return doRequest(XxlJobConstants.JOBGROUP_GET, paramMap);
	}

	public ReturnT<String> addJobGroup(XxlJobGroup jobGroup) {
		if (Objects.isNull(jobGroup)) {
			return new ReturnT<>(ReturnT.FAIL_CODE, "任务执行器信息不能为空");
		}
		Map<String, Object> paramMap = JSON.parseObject(JSON.toJSONString(jobGroup), Map.class);
		return doRequest(XxlJobConstants.JOBGROUP_SAVE, paramMap);
	}

	public ReturnT<String> updateJobGroup(XxlJobGroup jobGroup) {
		Map<String, Object> paramMap = JSON.parseObject(JSON.toJSONString(jobGroup), Map.class);
		return doRequest(XxlJobConstants.JOBGROUP_UPDATE, paramMap);
	}

	public ReturnT<String> removeJobGroup(Integer jobGroupId) {
		if (Objects.isNull(jobGroupId)) {
			return new ReturnT<>(ReturnT.FAIL_CODE, "任务执行器主键ID不能为空");
		}
		Map<String, Object> paramMap = new HashMap<>(1);
		paramMap.put("id", jobGroupId);
		return doRequest(XxlJobConstants.JOBGROUP_REMOVE, paramMap);
	}

	public ReturnT<XxlJobInfoList> jobInfoList(int start, int length, Integer jobGroup) {
		return this.jobInfoList(start, length, jobGroup, -1, EMPTY, EMPTY, EMPTY);
	}

	public ReturnT<XxlJobInfoList> jobInfoList(int start, int length, Integer jobGroup, Integer triggerStatus) {
		return this.jobInfoList(start, length, jobGroup, triggerStatus, EMPTY, EMPTY, EMPTY);
	}

	public ReturnT<XxlJobInfoList> jobInfoList(int start, int length, Integer jobGroup,
											   Integer triggerStatus, String jobDesc, String executorHandler, String author) {
		if (Objects.isNull(jobGroup)) {
			return new ReturnT<>(ReturnT.FAIL_CODE, "任务执行器主键ID不能为空");
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

	public ReturnT<String> addUniqueJob(XxlJobInfo jobInfo) {
		if (Objects.isNull(jobInfo)) {
			return new ReturnT<>(ReturnT.FAIL_CODE, "任务信息不能为空");
		}
		if (Objects.isNull(jobInfo.getJobGroup())) {
			return new ReturnT<>(ReturnT.FAIL_CODE, "任务执行器主键ID不能为空");
		}
		ReturnT<XxlJobInfoList> returnT1 = this.jobInfoList(0, Integer.MAX_VALUE, jobInfo.getJobGroup());
		if (returnT1.getCode() == ReturnT.FAIL_CODE) {
			return new ReturnT<>(ReturnT.FAIL_CODE, "获取任务列表失败，失败原因:" + returnT1.getMsg());
		}
		XxlJobInfoList jobInfoList = returnT1.getContent();
		if (Objects.isNull(jobInfoList) || CollectionUtils.isEmpty(jobInfoList.getData())) {
			return this.addJob(jobInfo);
		}
		StringUtils.trimWhitespace(jobInfo.getJobDesc());
		if (jobInfoList.getData().stream().anyMatch(job -> StringUtils.trimWhitespace(job.getJobDesc())
				.equals(StringUtils.trimWhitespace(jobInfo.getJobDesc())))) {
			return new ReturnT<>(ReturnT.FAIL_CODE, "任务组内已存在相同描述的任务");
		}
		return this.addJob(jobInfo);
	}

	public ReturnT<String> addJob(XxlJobInfo jobInfo) {
		if (Objects.isNull(jobInfo)) {
			return new ReturnT<>(ReturnT.FAIL_CODE, "任务信息不能为空");
		}
		if (Objects.isNull(jobInfo.getJobGroup())) {
			return new ReturnT<>(ReturnT.FAIL_CODE, "任务执行器主键ID不能为空");
		}
		if (Objects.isNull(jobInfo.getJobDesc())) {
			return new ReturnT<>(ReturnT.FAIL_CODE, "任务描述不能为空");
		}
		Map<String, Object> paramMap = JSON.parseObject(JSON.toJSONString(jobInfo), Map.class);
		return doRequest(XxlJobConstants.JOBINFO_ADD, paramMap);
	}

	public ReturnT<String> updateJob(XxlJobInfo jobInfo) {
		if (Objects.isNull(jobInfo)) {
			return new ReturnT<>(ReturnT.FAIL_CODE, "任务信息不能为空");
		}
		if (Objects.isNull(jobInfo.getId())) {
			return new ReturnT<>(ReturnT.FAIL_CODE, "任务ID不能为空");
		}
		if (Objects.isNull(jobInfo.getJobDesc())) {
			return new ReturnT<>(ReturnT.FAIL_CODE, "任务描述不能为空");
		}
		Map<String, Object> paramMap = JSON.parseObject(JSON.toJSONString(jobInfo), Map.class);
		return doRequest(XxlJobConstants.JOBINFO_UPDATE, paramMap);
	}

	public ReturnT<String> removeJob(Integer jobId) {
		if (Objects.isNull(jobId)) {
			return new ReturnT<>(ReturnT.FAIL_CODE, "任务ID不能为空");
		}
		Map<String, Object> paramMap = new HashMap<>(1);
		paramMap.put("id", jobId);
		return doRequest(XxlJobConstants.JOBINFO_REMOVE, paramMap);
	}

	public ReturnT<String> stopJob(Integer jobId) {
		if (Objects.isNull(jobId)) {
			return new ReturnT<>(ReturnT.FAIL_CODE, "任务ID不能为空");
		}
		Map<String, Object> paramMap = new HashMap<>(1);
		paramMap.put("id", jobId);
		return doRequest(XxlJobConstants.JOBINFO_STOP, paramMap);
	}

	public ReturnT<String> startJob(Integer jobId) {
		if (Objects.isNull(jobId)) {
			return new ReturnT<>(ReturnT.FAIL_CODE, "任务ID不能为空");
		}
		Map<String, Object> paramMap = new HashMap<>(1);
		paramMap.put("id", jobId);
		return doRequest(XxlJobConstants.JOBINFO_START, paramMap);
	}

	public ReturnT<String> triggerJob(XxlJobInfo jobInfo) {
		if (Objects.isNull(jobInfo)) {
			return new ReturnT<>(ReturnT.FAIL_CODE, "任务信息不能为空");
		}
		return this.triggerJob(jobInfo.getId(), jobInfo.getExecutorParam());
	}

	public ReturnT<String> triggerJob(Integer jobInfoId, String executorParam) {
		if (Objects.isNull(jobInfoId)) {
			return new ReturnT<>(ReturnT.FAIL_CODE, "任务ID不能为空");
		}
		Map<String, Object> paramMap = new HashMap<>(2);
		paramMap.put("id", jobInfoId);
		paramMap.put("executorParam", executorParam);
		return doRequest(XxlJobConstants.JOBINFO_TRIGGER, paramMap);
	}

	private <T> ReturnT<T> doRequest(String suffix, Map<String, Object> paramMap) {
		try {
			HttpResponse<String> response = doPost(suffix, paramMap, false);
			if (response.isSuccess()) {
				log.info("xxl-job request successful.");
				String body = response.getBody();
				log.debug("xxl-job response body: {} .", body);
				if (isResponseJson(response)) {
					ReturnT<T> returnT = JSON.parseObject(body, new TypeReference<ReturnT<T>>() {});
					return returnT;
				}
			}
			log.error("xxl-job request fail. status:{}", response.getStatus());
			return new ReturnT<>(ReturnT.FAIL_CODE, response.getStatusText());
		} catch (Exception e) {
			return new ReturnT<>(ReturnT.FAIL_CODE, e.getMessage());
		}
	}

	private <T> ReturnT<T> doRequest(String suffix, Map<String, Object> paramMap, Class<T> objectClass) {
		try {
			HttpResponse<String> response = doPost(suffix, paramMap, false);
			if (response.isSuccess()) {
				log.info("xxl-job request successful.");
				String body = response.getBody();
				log.debug("xxl-job response body: {} .", body);
				if (isResponseJson(response)) {
					T rt = JSON.parseObject(body, objectClass);
					return new ReturnT<>(rt);
				}
			}
			log.error("xxl-job request fail. status:{}", response.getStatus());
			return new ReturnT<>(ReturnT.FAIL_CODE, response.getStatusText());
		} catch (Exception e) {
			return new ReturnT<>(ReturnT.FAIL_CODE, e.getMessage());
		}
	}

	/**
	 * 处理 pageList 接口返回的非 ReturnT 包装的响应（直接是 Map 格式）
	 */
	private <T> ReturnT<T> doRequestForPageList(String suffix, Map<String, Object> paramMap, Class<T> objectClass) {
		try {
			HttpResponse<String> response = doPost(suffix, paramMap, false);
			if (response.isSuccess()) {
				log.info("xxl-job pageList request successful.");
				String body = response.getBody();
				log.debug("xxl-job pageList response body: {} .", body);
				if (isResponseJson(response)) {
					T result = JSON.parseObject(body, objectClass);
					return new ReturnT<>(result);
				}
			}
			log.error("xxl-job pageList request fail. status:{}", response.getStatus());
			return new ReturnT<>(ReturnT.FAIL_CODE, response.getStatusText());
		} catch (Exception e) {
			return new ReturnT<>(ReturnT.FAIL_CODE, e.getMessage());
		}
	}

}
