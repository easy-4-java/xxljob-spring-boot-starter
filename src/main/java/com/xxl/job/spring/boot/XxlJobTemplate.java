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
import com.xxl.tool.response.Response;
import com.xxl.job.spring.boot.model.XxlJobGroup;
import com.xxl.job.spring.boot.model.XxlJobGroupList;
import com.xxl.job.spring.boot.model.XxlJobInfo;
import com.xxl.job.spring.boot.model.XxlJobInfoList;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.*;

@Slf4j
public class XxlJobTemplate {

	public final static String EMPTY = "";
	public final static String APPLICATION_JSON_VALUE = "application/json";
	public final static String APPLICATION_JSON_UTF8_VALUE = "application/json;charset=UTF-8";
	public final static okhttp3.MediaType APPLICATION_JSON = okhttp3.MediaType.parse(APPLICATION_JSON_VALUE);
	public final static okhttp3.MediaType APPLICATION_JSON_UTF8 = okhttp3.MediaType.parse(APPLICATION_JSON_UTF8_VALUE);

	protected OkHttpClient okhttp3Client;
	protected XxlJobProperties properties;
	protected XxlJobAdminProperties adminProperties;
	protected XxlJobExecutorProperties executorProperties;

	public XxlJobTemplate( OkHttpClient okhttp3Client,
						   XxlJobProperties properties,
			XxlJobAdminProperties adminProperties,
			XxlJobExecutorProperties executorProperties) {
		this.okhttp3Client = okhttp3Client;
		this.properties = properties;
		this.adminProperties = adminProperties;
		this.executorProperties = executorProperties;
	}

	public Response<String> login(String userName, String password, boolean remember) {
		try {
			Map<String, Object> paramMap = new HashMap<>(3);
			paramMap.put("userName", userName);
			paramMap.put("password", password);
			paramMap.put("ifRemember", remember ? "on" : "off");
			String url = this.joinPath(XxlJobConstants.LOGIN_GET);
			Request request = this.buildRequestEntity(url, paramMap, true);
			okhttp3.Response response = okhttp3Client.newCall(request).execute();
			if(response.isSuccessful()) {
				log.info("xxl-job login success.");
				String cookie = response.header(HttpHeaders.SET_COOKIE);
				log.info("xxl-job cookie {}.", cookie);
				return Response.ofSuccess(cookie);
			}
			log.error("xxl-job login fail.");
			return Response.ofFail(response.toString());
		} catch (IOException e) {
			return Response.ofFail(e.getMessage());
		}
	}

	private boolean isResponseJson(okhttp3.Response response) {
		String contentType = response.header(HttpHeaders.CONTENT_TYPE);
		return contentType != null && (contentType.startsWith(MediaType.APPLICATION_JSON_VALUE));
	}

	public Response<String> logout() throws IOException {
		Map<String, Object> paramMap = Collections.emptyMap();
		String url = this.joinPath(XxlJobConstants.LOGOUT_GET);
		Request request = this.buildRequestEntity(url, paramMap);
		okhttp3.Response response = okhttp3Client.newCall(request).execute();
		if(response.isSuccessful()) {
			log.info("xxl-job logout success.");
			return Response.ofSuccess();
		}
		log.error("xxl-job logout fail.");
		return Response.ofFail(response.toString());
	}

	public Response<XxlJobGroupList> jobInfoGroupList(int start, int length) {
		return this.jobInfoGroupList(start, length, EMPTY, EMPTY);
	}

	public Response<XxlJobGroupList> jobInfoGroupList(int start, int length, String appname, String title) {
		Map<String, Object> paramMap = new HashMap<>(7);
		paramMap.put("start", Math.max(0, start));
		paramMap.put("length", Math.min(length, 5));
		paramMap.put("appname", appname);
		paramMap.put("title", title);
		String url = this.joinPath(XxlJobConstants.JOBGROUP_PAGELIST);
		Request request = this.buildRequestEntity(url, paramMap, false);
		return this.doRequestForPageList(request, XxlJobGroupList.class);
	}

	public Response<XxlJobGroup> jobInfoGroup(Integer jobGroupId) {
		if ( Objects.isNull(jobGroupId)) {
			return Response.ofFail("任务执行器主键ID不能为空");
		}
		Map<String, Object> paramMap = new HashMap<>(1);
		paramMap.put("id", jobGroupId);
		String url = this.joinPath(XxlJobConstants.JOBGROUP_GET);
		Request request = this.buildRequestEntity(url, paramMap, false);
		return this.doRequest(request);
	}

	public Response<String> addJobGroup(XxlJobGroup jobGroup) {
		if ( Objects.isNull(jobGroup)) {
			return Response.ofFail("任务执行器信息不能为空");
		}
		Map<String, Object> paramMap = JSON.parseObject(JSON.toJSONString(jobGroup), Map.class);
		String url = this.joinPath(XxlJobConstants.JOBGROUP_SAVE);
		Request request = this.buildRequestEntity(url, paramMap, false);
		return this.doRequest(request);
	}

	public Response<String> updateJobGroup(XxlJobGroup jobGroup) {
		Map<String, Object> paramMap = JSON.parseObject(JSON.toJSONString(jobGroup), Map.class);
		String url = this.joinPath(XxlJobConstants.JOBGROUP_UPDATE);
		Request request = this.buildRequestEntity(url, paramMap, false);
		return this.doRequest(request);
	}

	public Response<String> removeJobGroup(Integer jobGroupId) {
		if ( Objects.isNull(jobGroupId)) {
			return Response.ofFail("任务执行器主键ID不能为空");
		}
		Map<String, Object> paramMap = new HashMap<>(1);
		paramMap.put("id", jobGroupId);
		String url = this.joinPath(XxlJobConstants.JOBGROUP_REMOVE);
		Request request = this.buildRequestEntity(url, paramMap, false);
		return this.doRequest(request);
	}

	public Response<XxlJobInfoList> jobInfoList(int start, int length, Integer jobGroup) {
		return this.jobInfoList(start, length, jobGroup, -1, EMPTY, EMPTY, EMPTY);
	}

    public Response<XxlJobInfoList> jobInfoList(int start, int length, Integer jobGroup, Integer triggerStatus) {
    	return this.jobInfoList(start, length, jobGroup, triggerStatus, EMPTY, EMPTY, EMPTY);
	}

    public Response<XxlJobInfoList> jobInfoList(int start, int length, Integer jobGroup,
											   Integer triggerStatus, String jobDesc, String executorHandler, String author) {
		if ( Objects.isNull(jobGroup)) {
			return Response.ofFail("任务执行器主键ID不能为空");
		}
		Map<String, Object> paramMap = new HashMap<>(7);
		paramMap.put("start", Math.max(0, start));
		paramMap.put("length", Math.max(length, 5));
		paramMap.put("jobGroup", jobGroup);
		paramMap.put("triggerStatus", triggerStatus);
		paramMap.put("jobDesc", jobDesc);
		paramMap.put("executorHandler", executorHandler);
		paramMap.put("author", author);
		String url = this.joinPath(XxlJobConstants.JOBINFO_PAGELIST);
		Request request = this.buildRequestEntity(url, paramMap, false);
		return this.doRequestForPageList(request, XxlJobInfoList.class);
	}

	public Response<String> addUniqueJob(XxlJobInfo jobInfo) {
		if (Objects.isNull(jobInfo)) {
			return Response.ofFail("任务信息不能为空");
		}
		if ( Objects.isNull(jobInfo.getJobGroup())) {
			return Response.ofFail("任务执行器主键ID不能为空");
		}
		Response<XxlJobInfoList> returnT1 = this.jobInfoList(0, Integer.MAX_VALUE, jobInfo.getJobGroup());
		if (!returnT1.isSuccess()) {
			return Response.ofFail("获取任务列表失败，失败原因:" + returnT1.getMsg());
		}
		XxlJobInfoList jobInfoList = returnT1.getData();
		if(Objects.isNull(jobInfoList) || CollectionUtils.isEmpty(jobInfoList.getData())) {
			return this.addJob(jobInfo);
		}
		StringUtils.trimWhitespace(jobInfo.getJobDesc());
		if(jobInfoList.getData().stream().anyMatch(job -> StringUtils.trimWhitespace(job.getJobDesc())
				.equals(StringUtils.trimWhitespace(jobInfo.getJobDesc())))) {
			return Response.ofFail("任务组内已存在相同描述的任务");
		}
		return this.addJob(jobInfo);
	}

	public Response<String> addJob(XxlJobInfo jobInfo) {
		if (Objects.isNull(jobInfo)) {
			return Response.ofFail("任务信息不能为空");
		}
		if ( Objects.isNull(jobInfo.getJobGroup())) {
			return Response.ofFail("任务执行器主键ID不能为空");
		}
		if ( Objects.isNull(jobInfo.getJobDesc())) {
			return Response.ofFail("任务描述不能为空");
		}
		Map<String, Object> paramMap = JSON.parseObject(JSON.toJSONString(jobInfo), Map.class);
		String url = this.joinPath(XxlJobConstants.JOBINFO_ADD);
		Request request = this.buildRequestEntity(url, paramMap, false);
		return this.doRequest(request);
    }

    public Response<String> updateJob(XxlJobInfo jobInfo) {
		if (Objects.isNull(jobInfo)) {
			return Response.ofFail("任务信息不能为空");
		}
		if ( Objects.isNull(jobInfo.getId())) {
			return Response.ofFail("任务ID不能为空");
		}
		if ( Objects.isNull(jobInfo.getJobDesc())) {
			return Response.ofFail("任务描述不能为空");
		}
		Map<String, Object> paramMap = JSON.parseObject(JSON.toJSONString(jobInfo), Map.class);
		String url = this.joinPath(XxlJobConstants.JOBINFO_UPDATE);
		Request request = this.buildRequestEntity(url, paramMap, false);
		return this.doRequest(request);
    }

    public Response<String> removeJob(Integer jobId) {
		if ( Objects.isNull(jobId)) {
			return Response.ofFail("任务ID不能为空");
		}
		Map<String, Object> paramMap = new HashMap<>(1);
		paramMap.put("id", jobId);
		String url = this.joinPath(XxlJobConstants.JOBINFO_REMOVE);
		Request request = this.buildRequestEntity(url, paramMap, false);
		return this.doRequest(request);
    }

    public Response<String> stopJob(Integer jobId) {
		if ( Objects.isNull(jobId)) {
			return Response.ofFail("任务ID不能为空");
		}
		Map<String, Object> paramMap = new HashMap<>(1);
		paramMap.put("id", jobId);
		String url = this.joinPath(XxlJobConstants.JOBINFO_STOP);
		Request request = this.buildRequestEntity(url, paramMap, false);
		return this.doRequest(request);
    }

	public Response<String> startJob(Integer jobId) {
		if ( Objects.isNull(jobId)) {
			return Response.ofFail("任务ID不能为空");
		}
		Map<String, Object> paramMap = new HashMap<>(1);
		paramMap.put("id", jobId);
		String url = this.joinPath(XxlJobConstants.JOBINFO_START);
		Request request = this.buildRequestEntity(url, paramMap, false);
		return this.doRequest(request);
    }

	public Response<String> triggerJob(XxlJobInfo jobInfo) {
		if (Objects.isNull(jobInfo)) {
			return Response.ofFail("任务信息不能为空");
		}
		return this.triggerJob(jobInfo.getId(), jobInfo.getExecutorParam());
	}

    public Response<String> triggerJob(Integer jobInfoId, String executorParam) {
		if ( Objects.isNull(jobInfoId)) {
			return Response.ofFail("任务ID不能为空");
		}
		Map<String, Object> paramMap = new HashMap<>(2);
		paramMap.put("id", jobInfoId);
		paramMap.put("executorParam", executorParam);
		String url = this.joinPath(XxlJobConstants.JOBINFO_TRIGGER);
		Request request = this.buildRequestEntity(url, paramMap, false);
		return this.doRequest(request);
    }

	private Request buildRequestEntity(String url, Map<String, Object> paramMap) {
		return this.buildRequestEntity(url, paramMap, false);
	}

	private Request buildRequestEntity(String url, Map<String, Object> paramMap, boolean isLoginRequest) {
		Headers.Builder headers = new Headers.Builder()
				.add(XxlJobConstants.XXL_RPC_ACCESS_TOKEN, properties.getAccessToken());
		FormBody.Builder builder = new FormBody.Builder();
		for (String key : paramMap.keySet()) {
			Object obj = paramMap.get(key);
			if (obj != null) {
				builder.addEncoded(key, paramMap.get(key).toString());
			} else {
				builder.addEncoded(key, "");
			}
		}
		FormBody requestBody = builder.build();
		HttpUrl httpUrl = HttpUrl.parse(url);
		Request.Builder request = new Request.Builder().url(httpUrl).headers(headers.build()).post(requestBody);
		if(!isLoginRequest){
			this.loginIfNeed(httpUrl, headers, request);
		}
		return request.build();
	}

	private void loginIfNeed(HttpUrl httpUrl, Headers.Builder headers, Request.Builder request) {
		CookieJar cookieJar = okhttp3Client.cookieJar();
		List<Cookie> cookies = cookieJar.loadForRequest(httpUrl);
		if(CollectionUtils.isEmpty(cookies) || cookies.stream().noneMatch(cookie -> XxlJobConstants.XXL_RPC_COOKIE.equals(cookie.name()))){
			this.login(adminProperties.getUsername(), adminProperties.getPassword(), adminProperties.isRemember());
		}
	}

	private <T> Response<T> doRequest(Request request, Class<T> objectClass) {
		try {
			okhttp3.Response response = okhttp3Client.newCall(request).execute();
			if(response.isSuccessful()) {
				log.info("xxl-job request successful.");
				String body = response.body().string();
				log.debug("xxl-job response body: {} .", body);
				if(isResponseJson(response)){
					T rt = JSON.parseObject(body, objectClass);
					return Response.ofSuccess(rt);
				}
			}
			log.error("xxl-job request fail.");
			return Response.ofFail(response.toString());
		} catch (IOException e) {
			return Response.ofFail(e.getMessage());
		}
	}

	/**
	 * 处理 pageList 接口返回的非 Response 包装的响应（直接是 Map 格式）
	 * admin 的 /jobgroup/pageList 和 /jobinfo/pageList 返回 {recordsTotal, recordsFiltered, data}
	 */
	private <T> Response<T> doRequestForPageList(Request request, Class<T> objectClass) {
		try {
			okhttp3.Response response = okhttp3Client.newCall(request).execute();
			if(response.isSuccessful()) {
				log.info("xxl-job pageList request successful.");
				String body = response.body().string();
				log.debug("xxl-job pageList response body: {} .", body);
				if(isResponseJson(response)){
					T result = JSON.parseObject(body, objectClass);
					return Response.ofSuccess(result);
				}
			}
			log.error("xxl-job pageList request fail.");
			return Response.ofFail(response.toString());
		} catch (IOException e) {
			return Response.ofFail(e.getMessage());
		}
	}

	private <T> Response<T> doRequest(Request request) {
		try {
			okhttp3.Response response = okhttp3Client.newCall(request).execute();
			return this.parseResponseEntity(response);
		} catch (IOException e) {
			return Response.ofFail(e.getMessage());
		}
	}

	private <T> Response<T> parseResponseEntity(okhttp3.Response response) throws IOException {
		if(response.isSuccessful()) {
			log.info("xxl-job request successful.");
			String body = response.body().string();
			log.debug("xxl-job response body: {} .", body);
			if(isResponseJson(response)){
				// 3.4.0 admin 返回 Response{code, msg, data}，其中 data 包含实际内容
				JSONObject json = JSON.parseObject(body);
				int code = json.getIntValue("code", 0);
				String msg = json.getString("msg");
				// 兼容 3.4.0 (data字段) 和旧版 (content字段)
				String dataJson = json.getString("data");
				if (dataJson == null) {
					dataJson = json.getString("content");
				}
				if (code == 200 && dataJson != null) {
					T data = JSON.parseObject(dataJson, new TypeReference<T>() {});
					return Response.ofSuccess(data);
				}
				return Response.ofFail(msg != null ? msg : "request fail, code:" + code);
			}
		}
		log.error("xxl-job request fail.");
		return Response.ofFail(response.toString());
	}

	private String joinPath(String suffix) {
		String address = adminProperties.getAddresses();
		if (address.endsWith("/")) {
			address = address.substring(0, address.length() - 1);
		}
		return address + suffix;
	}

}
