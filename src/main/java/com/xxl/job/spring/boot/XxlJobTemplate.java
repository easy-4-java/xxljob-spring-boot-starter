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
import com.alibaba.fastjson2.JSONObject;
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

	public XxlJobTemplate(UnirestInstance unirestInstance,
						   XxlJobProperties properties,
						   XxlJobAdminProperties adminProperties,
						   XxlJobExecutorProperties executorProperties) {
		this.unirestInstance = unirestInstance;
		this.properties = properties;
		this.adminProperties = adminProperties;
		this.executorProperties = executorProperties;
	}

	private AdminVersion version() {
		AdminVersion v = adminProperties.getVersion();
		return v != null ? v : AdminVersion.V3_X;
	}

	private boolean isV3() {
		return version() == AdminVersion.V3_X;
	}

	private String buildUrl(String suffix) {
		String address = adminProperties.getAddresses();
		if (address.endsWith("/")) {
			address = address.substring(0, address.length() - 1);
		}
		return address + suffix;
	}

	// ======================== 认证 ========================

	public ReturnT<String> login(String userName, String password, boolean remember) {
		return doLogin(userName, password, remember) ? new ReturnT<String>("ok") : new ReturnT<String>(ReturnT.FAIL_CODE, "login failed");
	}

	public ReturnT<String> logout() {
		try {
			String url = buildUrl(XxlJobConstants.logoutPath(version()));
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

	private void loginIfNeed() {
		if (authenticated) return;
		synchronized (loginLock) {
			if (authenticated) return;
			doLogin(adminProperties.getUsername(), adminProperties.getPassword(), adminProperties.isRemember());
		}
	}

	private boolean doLogin(String userName, String password, boolean remember) {
		try {
			String url = buildUrl(XxlJobConstants.loginPath(version()));
			log.info("xxl-job login POST {} (v={})", url, version());
			HttpResponse<String> response = unirestInstance.post(url)
					.header(XxlJobConstants.XXL_RPC_ACCESS_TOKEN, properties.getAccessToken())
					.field("userName", userName)
					.field("password", password)
					.field("ifRemember", remember ? "on" : "off")
					.asString();
			log.info("xxl-job login response: status={}, body={}",
					response.getStatus(), safeBody(response));
			if (response.isSuccess()) {
				log.info("xxl-job login SUCCESS");
				authenticated = true;
				return true;
			}
			log.error("xxl-job login FAIL: status={}", response.getStatus());
			authenticated = false;
			return false;
		} catch (Exception e) {
			log.error("xxl-job login ERROR: {}", e.getMessage(), e);
			authenticated = false;
			return false;
		}
	}

	private String safeBody(HttpResponse<String> r) {
		try {
			String b = r.getBody();
			return b != null ? b.substring(0, Math.min(b.length(), 200)) : "null";
		} catch (Exception e) {
			return "err";
		}
	}

	// ======================== HTTP ========================

	private HttpResponse<String> doPost(String suffix, Map<String, Object> paramMap) {
		String url = buildUrl(suffix);
		loginIfNeed();
		HttpResponse<String> response = executePost(url, paramMap);
		// session 过期检测
		if (response.isSuccess() && !isResponseJson(response)) {
			String body = null;
			try { body = response.getBody(); } catch (Exception ignored) {}
			log.warn("xxl-job session expired, re-login. body:{}", safeBody(response));
			authenticated = false;
			loginIfNeed();
			return executePost(url, paramMap);
		}
		return response;
	}

	private HttpResponse<String> executePost(String url, Map<String, Object> paramMap) {
		return unirestInstance.post(url)
				.header(XxlJobConstants.XXL_RPC_ACCESS_TOKEN, properties.getAccessToken())
				.fields(paramMap)
				.asString();
	}

	private boolean isResponseJson(HttpResponse<String> r) {
		String ct = r.getHeaders().getFirst("Content-Type");
		return ct != null && ct.contains("application/json");
	}

	private String errorMsg(HttpResponse<String> r) {
		String st = r.getStatusText();
		if (st != null && !st.isEmpty()) return "HTTP " + r.getStatus() + " " + st;
		String b = null;
		try { b = r.getBody(); } catch (Exception ignored) {}
		if (b != null && !b.isEmpty()) return "HTTP " + r.getStatus() + ", body: " + b.substring(0, Math.min(b.length(), 200));
		return "HTTP " + r.getStatus();
	}

	// ======================== v2/v3 参数差异 ========================

	private void putIdParam(Map<String, Object> paramMap, Integer id) {
		if (isV3()) paramMap.put("ids[]", Collections.singletonList(id));
		else paramMap.put("id", id);
	}

	// ======================== 响应解析 ========================

	private <T> ReturnT<T> parseReturnT(String body) {
		JSONObject json = JSON.parseObject(body);
		int code = json.getIntValue("code", ReturnT.FAIL_CODE);
		String msg = json.getString("msg");
		String inner = json.getString("data");
		if (inner == null) inner = json.getString("content");
		if (inner != null) {
			T data = JSON.parseObject(inner, new TypeReference<T>() {});
			return new ReturnT<>(data);
		}
		return new ReturnT<>(code, msg);
	}

	private <T> ReturnT<T> parsePageListResult(String body, Class<T> clazz) {
		JSONObject json = JSON.parseObject(body);
		if (json.containsKey("code") && json.containsKey("data")) {
			String inner = json.getString("data");
			if (inner != null) {
				T result = JSON.parseObject(inner, clazz);
				return new ReturnT<>(result);
			}
		}
		T result = JSON.parseObject(body, clazz);
		return new ReturnT<>(result);
	}

	// ======================== 业务 API ========================

	public ReturnT<XxlJobGroupList> jobInfoGroupList(int start, int length) {
		return this.jobInfoGroupList(start, length, EMPTY, EMPTY);
	}

	public ReturnT<XxlJobGroupList> jobInfoGroupList(int start, int length, String appname, String title) {
		Map<String, Object> p = new HashMap<>(4);
		p.put("start", Math.max(0, start)); p.put("length", Math.min(length, 5));
		p.put("appname", appname); p.put("title", title);
		try {
			HttpResponse<String> r = doPost(XxlJobConstants.JOBGROUP_PAGELIST, p);
			if (r.isSuccess() && isResponseJson(r)) return parsePageListResult(r.getBody(), XxlJobGroupList.class);
			return new ReturnT<>(ReturnT.FAIL_CODE, errorMsg(r));
		} catch (Exception e) { return new ReturnT<>(ReturnT.FAIL_CODE, e.getMessage()); }
	}

	public ReturnT<XxlJobGroup> jobInfoGroup(Integer jobGroupId) {
		if (Objects.isNull(jobGroupId)) return new ReturnT<>(ReturnT.FAIL_CODE, "任务执行器主键ID不能为空");
		Map<String, Object> p = new HashMap<>(1); p.put("id", jobGroupId);
		return doRequest(XxlJobConstants.JOBGROUP_GET, p);
	}

	public ReturnT<String> addJobGroup(XxlJobGroup g) {
		if (Objects.isNull(g)) return new ReturnT<>(ReturnT.FAIL_CODE, "任务执行器信息不能为空");
		return doRequest(XxlJobConstants.jobGroupSavePath(version()), JSON.parseObject(JSON.toJSONString(g), Map.class));
	}

	public ReturnT<String> updateJobGroup(XxlJobGroup g) {
		return doRequest(XxlJobConstants.JOBGROUP_UPDATE, JSON.parseObject(JSON.toJSONString(g), Map.class));
	}

	public ReturnT<String> removeJobGroup(Integer id) {
		if (Objects.isNull(id)) return new ReturnT<>(ReturnT.FAIL_CODE, "任务执行器主键ID不能为空");
		Map<String, Object> p = new HashMap<>(1); putIdParam(p, id);
		return doRequest(XxlJobConstants.jobGroupRemovePath(version()), p);
	}

	public ReturnT<XxlJobInfoList> jobInfoList(int s, int l, Integer g) { return this.jobInfoList(s, l, g, -1, EMPTY, EMPTY, EMPTY); }
	public ReturnT<XxlJobInfoList> jobInfoList(int s, int l, Integer g, Integer ts) { return this.jobInfoList(s, l, g, ts, EMPTY, EMPTY, EMPTY); }

	public ReturnT<XxlJobInfoList> jobInfoList(int start, int length, Integer jobGroup,
			Integer triggerStatus, String jobDesc, String executorHandler, String author) {
		if (Objects.isNull(jobGroup)) return new ReturnT<>(ReturnT.FAIL_CODE, "任务执行器主键ID不能为空");
		Map<String, Object> p = new HashMap<>(7);
		p.put("start", Math.max(0, start)); p.put("length", Math.max(length, 5));
		p.put("jobGroup", jobGroup); p.put("triggerStatus", triggerStatus);
		p.put("jobDesc", jobDesc); p.put("executorHandler", executorHandler); p.put("author", author);
		try {
			HttpResponse<String> r = doPost(XxlJobConstants.JOBINFO_PAGELIST, p);
			if (r.isSuccess() && isResponseJson(r)) return parsePageListResult(r.getBody(), XxlJobInfoList.class);
			return new ReturnT<>(ReturnT.FAIL_CODE, errorMsg(r));
		} catch (Exception e) { return new ReturnT<>(ReturnT.FAIL_CODE, e.getMessage()); }
	}

	public ReturnT<String> addUniqueJob(XxlJobInfo jobInfo) {
		if (Objects.isNull(jobInfo)) return new ReturnT<>(ReturnT.FAIL_CODE, "任务信息不能为空");
		if (Objects.isNull(jobInfo.getJobGroup())) return new ReturnT<>(ReturnT.FAIL_CODE, "任务执行器主键ID不能为空");
		ReturnT<XxlJobInfoList> r1 = this.jobInfoList(0, Integer.MAX_VALUE, jobInfo.getJobGroup());
		if (r1.getCode() == ReturnT.FAIL_CODE) return new ReturnT<>(ReturnT.FAIL_CODE, "获取任务列表失败，" + r1.getMsg());
		XxlJobInfoList list = r1.getContent();
		if (Objects.isNull(list) || CollectionUtils.isEmpty(list.getData())) return this.addJob(jobInfo);
		StringUtils.trimWhitespace(jobInfo.getJobDesc());
		if (list.getData().stream().anyMatch(j -> StringUtils.trimWhitespace(j.getJobDesc()).equals(StringUtils.trimWhitespace(jobInfo.getJobDesc()))))
			return new ReturnT<>(ReturnT.FAIL_CODE, "任务组内已存在相同描述的任务");
		return this.addJob(jobInfo);
	}

	public ReturnT<String> addJob(XxlJobInfo j) {
		if (Objects.isNull(j)) return new ReturnT<>(ReturnT.FAIL_CODE, "任务信息不能为空");
		if (Objects.isNull(j.getJobGroup())) return new ReturnT<>(ReturnT.FAIL_CODE, "任务执行器主键ID不能为空");
		if (Objects.isNull(j.getJobDesc())) return new ReturnT<>(ReturnT.FAIL_CODE, "任务描述不能为空");
		return doRequest(XxlJobConstants.jobInfoAddPath(version()), JSON.parseObject(JSON.toJSONString(j), Map.class));
	}

	public ReturnT<String> updateJob(XxlJobInfo j) {
		if (Objects.isNull(j)) return new ReturnT<>(ReturnT.FAIL_CODE, "任务信息不能为空");
		if (Objects.isNull(j.getId())) return new ReturnT<>(ReturnT.FAIL_CODE, "任务ID不能为空");
		if (Objects.isNull(j.getJobDesc())) return new ReturnT<>(ReturnT.FAIL_CODE, "任务描述不能为空");
		return doRequest(XxlJobConstants.JOBINFO_UPDATE, JSON.parseObject(JSON.toJSONString(j), Map.class));
	}

	public ReturnT<String> removeJob(Integer id) {
		if (Objects.isNull(id)) return new ReturnT<>(ReturnT.FAIL_CODE, "任务ID不能为空");
		Map<String, Object> p = new HashMap<>(1); putIdParam(p, id);
		return doRequest(XxlJobConstants.jobInfoRemovePath(version()), p);
	}

	public ReturnT<String> stopJob(Integer id) {
		if (Objects.isNull(id)) return new ReturnT<>(ReturnT.FAIL_CODE, "任务ID不能为空");
		Map<String, Object> p = new HashMap<>(1); putIdParam(p, id);
		return doRequest(XxlJobConstants.JOBINFO_STOP, p);
	}

	public ReturnT<String> startJob(Integer id) {
		if (Objects.isNull(id)) return new ReturnT<>(ReturnT.FAIL_CODE, "任务ID不能为空");
		Map<String, Object> p = new HashMap<>(1); putIdParam(p, id);
		return doRequest(XxlJobConstants.JOBINFO_START, p);
	}

	public ReturnT<String> triggerJob(XxlJobInfo j) {
		if (Objects.isNull(j)) return new ReturnT<>(ReturnT.FAIL_CODE, "任务信息不能为空");
		return this.triggerJob(j.getId(), j.getExecutorParam());
	}

	public ReturnT<String> triggerJob(Integer id, String executorParam) {
		if (Objects.isNull(id)) return new ReturnT<>(ReturnT.FAIL_CODE, "任务ID不能为空");
		Map<String, Object> p = new HashMap<>(3);
		p.put("id", id); p.put("executorParam", executorParam);
		if (isV3()) p.put("addressList", "");
		return doRequest(XxlJobConstants.JOBINFO_TRIGGER, p);
	}

	// ======================== 内部请求 ========================

	private <T> ReturnT<T> doRequest(String suffix, Map<String, Object> paramMap) {
		try {
			HttpResponse<String> r = doPost(suffix, paramMap);
			if (r.isSuccess()) {
				String body = r.getBody();
				log.debug("xxl-job response: {}", body);
				if (isResponseJson(r)) return parseReturnT(body);
				log.error("xxl-job non-JSON suffix:{}, body:{}", suffix, safeBody(r));
			}
			log.error("xxl-job fail. suffix:{} {}", suffix, errorMsg(r));
			return new ReturnT<>(ReturnT.FAIL_CODE, errorMsg(r));
		} catch (Exception e) { return new ReturnT<>(ReturnT.FAIL_CODE, e.getMessage()); }
	}

}
