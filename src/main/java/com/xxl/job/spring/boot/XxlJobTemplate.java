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
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.spring.boot.admin.XxlJobAdminClient;
import com.xxl.job.spring.boot.admin.XxlJobAdminHttpResponse;
import com.xxl.job.spring.boot.model.XxlJobGroup;
import com.xxl.job.spring.boot.model.XxlJobGroupList;
import com.xxl.job.spring.boot.model.XxlJobInfo;
import com.xxl.job.spring.boot.model.XxlJobInfoList;
import kong.unirest.UnirestInstance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * xxl-job-admin 业务 API 门面，HTTP 通信委托给 {@link XxlJobAdminClient}。
 */
@Slf4j
public class XxlJobTemplate {

	public final static String EMPTY = "";

	protected XxlJobAdminClient adminClient;
	protected XxlJobProperties properties;
	protected XxlJobAdminProperties adminProperties;
	protected XxlJobExecutorProperties executorProperties;

	public XxlJobTemplate(XxlJobAdminClient adminClient,
						   XxlJobProperties properties,
						   XxlJobAdminProperties adminProperties,
						   XxlJobExecutorProperties executorProperties) {
		this.adminClient = adminClient;
		this.properties = properties;
		this.adminProperties = adminProperties;
		this.executorProperties = executorProperties;
	}

	/**
	 * 兼容旧构造方式：内部创建默认 {@link XxlJobAdminClient}。
	 */
	public XxlJobTemplate(UnirestInstance unirestInstance,
						   XxlJobProperties properties,
						   XxlJobAdminProperties adminProperties,
						   XxlJobExecutorProperties executorProperties) {
		this(new com.xxl.job.spring.boot.admin.DefaultXxlJobAdminClient(unirestInstance, properties, adminProperties),
				properties, adminProperties, executorProperties);
	}

	private boolean isV3() {
		return adminClient.isV3();
	}

	// ======================== 认证 ========================

	/**
	 * 登录 xxl-job-admin。
	 */
	public ReturnT<String> login(String userName, String password, boolean remember) {
		return adminClient.login(userName, password, remember)
				? new ReturnT<>("ok")
				: new ReturnT<>(ReturnT.FAIL_CODE, "login failed");
	}

	/**
	 * 登出 xxl-job-admin。
	 */
	public ReturnT<String> logout() {
		return adminClient.logout();
	}

	// ======================== v2/v3 参数差异 ========================

	private void putIdParam(Map<String, Object> paramMap, Integer id) {
		if (isV3()) {
			paramMap.put("ids[]", String.valueOf(id));
		} else {
			paramMap.put("id", id);
		}
	}

	// ======================== 响应解析 ========================

	private <T> ReturnT<T> parseReturnT(String body) {
		JSONObject json = JSON.parseObject(body);
		int code = json.getIntValue("code", ReturnT.FAIL_CODE);
		String msg = json.getString("msg");
		String content = json.getString("data");
		if (content == null) {
			content = json.getString("content");
		}
		if (content != null) {
			Object raw = json.get("data");
			if (raw == null) {
				raw = json.get("content");
			}
			if (raw instanceof Number) {
				raw = String.valueOf(raw);
			}
			@SuppressWarnings("unchecked")
			T data = (T) raw;
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
		p.put("start", Math.max(0, start));
		p.put("length", Math.min(length, 5));
		p.put("appname", appname);
		p.put("title", title);
		try {
			XxlJobAdminHttpResponse r = adminClient.postForm(XxlJobConstants.JOBGROUP_PAGELIST, p);
			if (r.isSuccess() && r.isJson()) {
				return parsePageListResult(r.getBody(), XxlJobGroupList.class);
			}
			return new ReturnT<>(ReturnT.FAIL_CODE, r.errorMessage());
		} catch (Exception e) {
			return new ReturnT<>(ReturnT.FAIL_CODE, e.getMessage());
		}
	}

	public ReturnT<XxlJobGroup> jobInfoGroup(Integer jobGroupId) {
		if (Objects.isNull(jobGroupId)) {
			return new ReturnT<>(ReturnT.FAIL_CODE, "任务执行器主键ID不能为空");
		}
		Map<String, Object> p = new HashMap<>(1);
		p.put("id", jobGroupId);
		return doRequest(XxlJobConstants.JOBGROUP_GET, p);
	}

	public ReturnT<String> addJobGroup(XxlJobGroup g) {
		if (Objects.isNull(g)) {
			return new ReturnT<>(ReturnT.FAIL_CODE, "任务执行器信息不能为空");
		}
		return doRequest(XxlJobConstants.jobGroupSavePath(adminClient.version()),
				JSON.parseObject(JSON.toJSONString(g), Map.class));
	}

	public ReturnT<String> updateJobGroup(XxlJobGroup g) {
		return doRequest(XxlJobConstants.JOBGROUP_UPDATE, JSON.parseObject(JSON.toJSONString(g), Map.class));
	}

	public ReturnT<String> removeJobGroup(Integer id) {
		if (Objects.isNull(id)) {
			return new ReturnT<>(ReturnT.FAIL_CODE, "任务执行器主键ID不能为空");
		}
		Map<String, Object> p = new HashMap<>(1);
		putIdParam(p, id);
		return doRequest(XxlJobConstants.jobGroupRemovePath(adminClient.version()), p);
	}

	public ReturnT<XxlJobInfoList> jobInfoList(int s, int l, Integer g) {
		return this.jobInfoList(s, l, g, -1, EMPTY, EMPTY, EMPTY);
	}

	public ReturnT<XxlJobInfoList> jobInfoList(int s, int l, Integer g, Integer ts) {
		return this.jobInfoList(s, l, g, ts, EMPTY, EMPTY, EMPTY);
	}

	public ReturnT<XxlJobInfoList> jobInfoList(int start, int length, Integer jobGroup,
			Integer triggerStatus, String jobDesc, String executorHandler, String author) {
		if (Objects.isNull(jobGroup)) {
			return new ReturnT<>(ReturnT.FAIL_CODE, "任务执行器主键ID不能为空");
		}
		Map<String, Object> p = new HashMap<>(7);
		p.put("start", Math.max(0, start));
		p.put("length", Math.max(length, 5));
		p.put("jobGroup", jobGroup);
		p.put("triggerStatus", triggerStatus);
		p.put("jobDesc", jobDesc);
		p.put("executorHandler", executorHandler);
		p.put("author", author);
		try {
			XxlJobAdminHttpResponse r = adminClient.postForm(XxlJobConstants.JOBINFO_PAGELIST, p);
			if (r.isSuccess() && r.isJson()) {
				return parsePageListResult(r.getBody(), XxlJobInfoList.class);
			}
			return new ReturnT<>(ReturnT.FAIL_CODE, r.errorMessage());
		} catch (Exception e) {
			return new ReturnT<>(ReturnT.FAIL_CODE, e.getMessage());
		}
	}

	public ReturnT<String> addUniqueJob(XxlJobInfo jobInfo) {
		if (Objects.isNull(jobInfo)) {
			return new ReturnT<>(ReturnT.FAIL_CODE, "任务信息不能为空");
		}
		if (Objects.isNull(jobInfo.getJobGroup())) {
			return new ReturnT<>(ReturnT.FAIL_CODE, "任务执行器主键ID不能为空");
		}
		ReturnT<XxlJobInfoList> r1 = this.jobInfoList(0, Integer.MAX_VALUE, jobInfo.getJobGroup());
		if (r1.getCode() == ReturnT.FAIL_CODE) {
			return new ReturnT<>(ReturnT.FAIL_CODE, "获取任务列表失败，" + r1.getMsg());
		}
		XxlJobInfoList list = r1.getContent();
		if (Objects.isNull(list) || CollectionUtils.isEmpty(list.getData())) {
			return this.addJob(jobInfo);
		}
		StringUtils.trimWhitespace(jobInfo.getJobDesc());
		if (list.getData().stream().anyMatch(j -> StringUtils.trimWhitespace(j.getJobDesc())
				.equals(StringUtils.trimWhitespace(jobInfo.getJobDesc())))) {
			return new ReturnT<>(ReturnT.FAIL_CODE, "任务组内已存在相同描述的任务");
		}
		return this.addJob(jobInfo);
	}

	public ReturnT<String> addJob(XxlJobInfo j) {
		if (Objects.isNull(j)) {
			return new ReturnT<>(ReturnT.FAIL_CODE, "任务信息不能为空");
		}
		if (Objects.isNull(j.getJobGroup())) {
			return new ReturnT<>(ReturnT.FAIL_CODE, "任务执行器主键ID不能为空");
		}
		if (Objects.isNull(j.getJobDesc())) {
			return new ReturnT<>(ReturnT.FAIL_CODE, "任务描述不能为空");
		}
		return doRequest(XxlJobConstants.jobInfoAddPath(adminClient.version()),
				JSON.parseObject(JSON.toJSONString(j), Map.class));
	}

	public ReturnT<String> updateJob(XxlJobInfo j) {
		if (Objects.isNull(j)) {
			return new ReturnT<>(ReturnT.FAIL_CODE, "任务信息不能为空");
		}
		if (Objects.isNull(j.getId())) {
			return new ReturnT<>(ReturnT.FAIL_CODE, "任务ID不能为空");
		}
		if (Objects.isNull(j.getJobDesc())) {
			return new ReturnT<>(ReturnT.FAIL_CODE, "任务描述不能为空");
		}
		return doRequest(XxlJobConstants.JOBINFO_UPDATE, JSON.parseObject(JSON.toJSONString(j), Map.class));
	}

	public ReturnT<String> removeJob(Integer id) {
		if (Objects.isNull(id)) {
			return new ReturnT<>(ReturnT.FAIL_CODE, "任务ID不能为空");
		}
		Map<String, Object> p = new HashMap<>(1);
		putIdParam(p, id);
		return doRequest(XxlJobConstants.jobInfoRemovePath(adminClient.version()), p);
	}

	public ReturnT<String> stopJob(Integer id) {
		if (Objects.isNull(id)) {
			return new ReturnT<>(ReturnT.FAIL_CODE, "任务ID不能为空");
		}
		Map<String, Object> p = new HashMap<>(1);
		putIdParam(p, id);
		return doRequest(XxlJobConstants.JOBINFO_STOP, p);
	}

	public ReturnT<String> startJob(Integer id) {
		if (Objects.isNull(id)) {
			return new ReturnT<>(ReturnT.FAIL_CODE, "任务ID不能为空");
		}
		Map<String, Object> p = new HashMap<>(1);
		putIdParam(p, id);
		return doRequest(XxlJobConstants.JOBINFO_START, p);
	}

	public ReturnT<String> triggerJob(XxlJobInfo j) {
		if (Objects.isNull(j)) {
			return new ReturnT<>(ReturnT.FAIL_CODE, "任务信息不能为空");
		}
		return this.triggerJob(j.getId(), j.getExecutorParam());
	}

	public ReturnT<String> triggerJob(Integer id, String executorParam) {
		if (Objects.isNull(id)) {
			return new ReturnT<>(ReturnT.FAIL_CODE, "任务ID不能为空");
		}
		Map<String, Object> p = new HashMap<>(3);
		p.put("id", id);
		p.put("executorParam", executorParam);
		// 官方 3.0.0 /trigger 要求 addressList 参数（空串表示由调度中心路由）
		p.put("addressList", "");
		return doRequest(XxlJobConstants.JOBINFO_TRIGGER, p);
	}

	// ======================== 内部请求 ========================

	private <T> ReturnT<T> doRequest(String suffix, Map<String, Object> paramMap) {
		try {
			XxlJobAdminHttpResponse r = adminClient.postForm(suffix, paramMap);
			if (r.isSuccess()) {
				log.debug("xxl-job response: {}", r.getBody());
				if (r.isJson()) {
					return parseReturnT(r.getBody());
				}
				log.error("xxl-job non-JSON suffix:{}, body:{}", suffix, r.safeBody(200));
			}
			log.error("xxl-job fail. suffix:{} {}", suffix, r.errorMessage());
			return new ReturnT<>(ReturnT.FAIL_CODE, r.errorMessage());
		} catch (Exception e) {
			return new ReturnT<>(ReturnT.FAIL_CODE, e.getMessage());
		}
	}

}
