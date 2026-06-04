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

	private final Object loginLock = new Object();
	private volatile boolean authenticated = false;

	// 自动检测 admin 版本：默认 v2，首次 404 后切换到 v3
	private volatile boolean useV3 = false;
	private static final java.util.Map<String, String> V2_TO_V3 = new java.util.HashMap<>();
	static {
		V2_TO_V3.put(XxlJobConstants.LOGIN_GET_V2, XxlJobConstants.LOGIN_GET_V3);
		V2_TO_V3.put(XxlJobConstants.LOGOUT_GET_V2, XxlJobConstants.LOGOUT_GET_V3);
		V2_TO_V3.put(XxlJobConstants.JOBGROUP_SAVE_V2, XxlJobConstants.JOBGROUP_SAVE_V3);
		V2_TO_V3.put(XxlJobConstants.JOBGROUP_REMOVE_V2, XxlJobConstants.JOBGROUP_REMOVE_V3);
		V2_TO_V3.put(XxlJobConstants.JOBINFO_ADD_V2, XxlJobConstants.JOBINFO_ADD_V3);
		V2_TO_V3.put(XxlJobConstants.JOBINFO_REMOVE_V2, XxlJobConstants.JOBINFO_REMOVE_V3);
	}

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

	private boolean loginIfNeed() {
		log.debug("xxl-job loginIfNeed: authenticated={}, useV3={}", authenticated, useV3);
		if (authenticated) {
			return true;
		}
		synchronized (loginLock) {
			if (authenticated) {
				return true;
			}
			log.info("xxl-job attempting login: admin={}, v2Path={}, v3Path={}",
					adminProperties.getAddresses(), XxlJobConstants.LOGIN_GET_V2, XxlJobConstants.LOGIN_GET_V3);
			if (doLogin(adminProperties.getUsername(), adminProperties.getPassword(), adminProperties.isRemember())) {
				log.info("xxl-job loginIfNeed: v2 login OK");
				return true;
			}
			log.warn("xxl-job loginIfNeed: v2 login FAILED, useV3={}", useV3);
			if (!useV3) {
				log.info("xxl-job loginIfNeed: attempting v3 fallback");
				if (doLoginV3(adminProperties.getUsername(), adminProperties.getPassword(), adminProperties.isRemember())) {
					useV3 = true;
					log.info("xxl-job loginIfNeed: v3 login OK, useV3=true");
					return true;
				}
				log.error("xxl-job loginIfNeed: v3 login also FAILED");
			}
			log.error("xxl-job loginIfNeed: all login attempts failed");
			return false;
		}
	}

	private boolean doLogin(String userName, String password, boolean remember) {
		String url = buildUrl(XxlJobConstants.LOGIN_GET_V2);
		log.info("xxl-job [v2] login POST {}", url);
		try {
			HttpResponse<String> response = unirestInstance.post(url)
					.header(XxlJobConstants.XXL_RPC_ACCESS_TOKEN, properties.getAccessToken())
					.field("userName", userName)
					.field("password", password)
					.field("ifRemember", remember ? "on" : "off")
					.asString();
			log.info("xxl-job [v2] login response: status={}, contentType={}, body={}",
					response.getStatus(),
					response.getHeaders().getFirst("Content-Type"),
					response.getBody() != null ? response.getBody().substring(0, Math.min(response.getBody().length(), 200)) : "null");
			if (response.isSuccess()) {
				log.info("xxl-job [v2] login SUCCESS");
				authenticated = true;
				return true;
			}
			log.warn("xxl-job [v2] login FAIL: status={}", response.getStatus());
			authenticated = false;
			return false;
		} catch (Exception e) {
			log.error("xxl-job [v2] login ERROR: {}", e.getMessage(), e);
			authenticated = false;
			return false;
		}
	}

	private boolean doLoginV3(String userName, String password, boolean remember) {
		String url = buildUrl(XxlJobConstants.LOGIN_GET_V3);
		log.info("xxl-job [v3] login POST {}", url);
		try {
			HttpResponse<String> response = unirestInstance.post(url)
					.header(XxlJobConstants.XXL_RPC_ACCESS_TOKEN, properties.getAccessToken())
					.field("userName", userName)
					.field("password", password)
					.field("ifRemember", remember ? "on" : "off")
					.asString();
			log.info("xxl-job [v3] login response: status={}, contentType={}, body={}",
					response.getStatus(),
					response.getHeaders().getFirst("Content-Type"),
					response.getBody() != null ? response.getBody().substring(0, Math.min(response.getBody().length(), 200)) : "null");
			if (response.isSuccess()) {
				log.info("xxl-job [v3] login SUCCESS");
				authenticated = true;
				return true;
			}
			log.warn("xxl-job [v3] login FAIL: status={}", response.getStatus());
			authenticated = false;
			return false;
		} catch (Exception e) {
			log.error("xxl-job [v3] login ERROR: {}", e.getMessage(), e);
			authenticated = false;
			return false;
		}
	}

	public ReturnT<String> login(String userName, String password, boolean remember) {
		boolean ok = doLogin(userName, password, remember);
		if (!ok && !useV3) {
			ok = doLoginV3(userName, password, remember);
			if (ok) useV3 = true;
		}
		return ok ? new ReturnT<String>("ok") : new ReturnT<String>(ReturnT.FAIL_CODE, "login failed");
	}

	public ReturnT<String> logout() {
		try {
			String url = buildUrl(XxlJobConstants.LOGOUT_GET_V2);
			HttpResponse<String> response = unirestInstance.post(url)
					.header(XxlJobConstants.XXL_RPC_ACCESS_TOKEN, properties.getAccessToken())
					.asString();
			if (response.isSuccess()) {
				log.info("xxl-job logout success.");
				authenticated = false;
				return ReturnT.SUCCESS;
			}
			log.error("xxl-job logout fail.");
			return new ReturnT<String>(ReturnT.FAIL_CODE, response.getStatusText());
		} catch (Exception e) {
			return new ReturnT<String>(ReturnT.FAIL_CODE, e.getMessage());
		}
	}

	private HttpResponse<String> doPost(String suffix, Map<String, Object> paramMap, boolean isLoginRequest) {
		log.debug("xxl-job doPost: suffix={}, isLogin={}, useV3={}", suffix, isLoginRequest, useV3);
		String url = buildUrl(suffix);
		if (!isLoginRequest) {
			if (!loginIfNeed()) {
				log.error("xxl-job login failed, cannot execute request.");
				throw new RuntimeException("xxl-job login failed before request: " + suffix);
			}
		}
		HttpResponse<String> response = executePost(url, paramMap);

		// 自动版本检测：v2 路径返回 404 时切换到 v3
		if (!useV3 && response.getStatus() == 404) {
			String v3Suffix = V2_TO_V3.get(suffix);
			if (v3Suffix != null) {
				log.warn("xxl-job admin v2 path {} returned 404, switching to v3 path {}", suffix, v3Suffix);
				useV3 = true;
				authenticated = false;
				if (loginIfNeed()) {
					return executePost(buildUrl(v3Suffix), paramMap);
				}
			}
		}

		// 非登录请求：检测 session 过期（非 JSON 响应 = 被重定向到登录页）
		if (!isLoginRequest && response.isSuccess() && !isResponseJson(response)) {
			String body = null;
			try { body = response.getBody(); } catch (Exception ignored) {}
			log.warn("xxl-job session expired (non-JSON response), re-login. body preview: {}",
					body != null ? body.substring(0, Math.min(body.length(), 200)) : "null");
			authenticated = false;
			if (loginIfNeed()) {
				log.info("xxl-job re-login success, retrying request.");
				return executePost(url, paramMap);
			}
			log.error("xxl-job re-login failed after session expiry");
		}
		return response;
	}

	private HttpResponse<String> executePost(String url, Map<String, Object> paramMap) {
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
		return doRequest(XxlJobConstants.JOBGROUP_SAVE_V2, paramMap);
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
		return doRequest(XxlJobConstants.JOBGROUP_REMOVE_V2, paramMap);
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
		return doRequest(XxlJobConstants.JOBINFO_ADD_V2, paramMap);
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
		return doRequest(XxlJobConstants.JOBINFO_REMOVE_V2, paramMap);
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

	/**
	 * 解析 admin 响应，兼容 data/content 字段差异。
	 * xxl-job-admin 3.3.0+ 使用 Response{code, msg, data}
	 * xxl-job-admin 2.x 使用 ReturnT{code, msg, content}
	 */
	private <T> ReturnT<T> parseReturnT(String body) {
		com.alibaba.fastjson2.JSONObject json = JSON.parseObject(body);
		int code = json.getIntValue("code", ReturnT.FAIL_CODE);
		String msg = json.getString("msg");
		String innerJson = json.getString("data");
		if (innerJson == null) {
			innerJson = json.getString("content");
		}
		if (innerJson != null) {
			T data = JSON.parseObject(innerJson, new TypeReference<T>() {});
			return new ReturnT<>(data);
		}
		return new ReturnT<>(code, msg);
	}

	private String errorMsg(HttpResponse<String> response) {
		String statusText = response.getStatusText();
		if (statusText != null && !statusText.isEmpty()) {
			return "HTTP " + response.getStatus() + " " + statusText;
		}
		// HTTP/2 无 reason phrase，用响应体代替
		String body = response.getBody();
		if (body != null && !body.isEmpty()) {
			return "HTTP " + response.getStatus() + ", body: " + body.substring(0, Math.min(body.length(), 200));
		}
		return "HTTP " + response.getStatus();
	}

	private <T> ReturnT<T> doRequest(String suffix, Map<String, Object> paramMap) {
		try {
			HttpResponse<String> response = doPost(suffix, paramMap, false);
			if (response.isSuccess()) {
				log.info("xxl-job request successful.");
				String body = response.getBody();
				log.debug("xxl-job response body: {} .", body);
				if (isResponseJson(response)) {
					return parseReturnT(body);
				}
				// 非 JSON 响应通常是登录失败或重定向
				log.error("xxl-job request returned non-JSON response. url suffix:{}, body:{}", suffix, body);
			}
			log.error("xxl-job request fail. suffix:{} {}", suffix, errorMsg(response));
			return new ReturnT<>(ReturnT.FAIL_CODE, errorMsg(response));
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
				log.error("xxl-job request returned non-JSON response. url suffix:{}, body:{}", suffix, body);
			}
			log.error("xxl-job request fail. suffix:{} {}", suffix, errorMsg(response));
			return new ReturnT<>(ReturnT.FAIL_CODE, errorMsg(response));
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
				log.error("xxl-job pageList returned non-JSON response. url suffix:{}, body:{}", suffix, body);
			}
			log.error("xxl-job pageList request fail. suffix:{} {}", suffix, errorMsg(response));
			return new ReturnT<>(ReturnT.FAIL_CODE, errorMsg(response));
		} catch (Exception e) {
			return new ReturnT<>(ReturnT.FAIL_CODE, e.getMessage());
		}
	}

}
