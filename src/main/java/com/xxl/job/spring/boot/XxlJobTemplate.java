package com.xxl.job.spring.boot;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
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

	private String buildUrl(String suffix) {
		String address = adminProperties.getAddresses();
		if (address.endsWith("/")) address = address.substring(0, address.length() - 1);
		return address + suffix;
	}

	// 3.x branches only support V3_X admin
	private boolean isV3() { return true; }

	// ======================== 认证 ========================

	public Response<String> login(String userName, String password, boolean remember) {
		return doLogin(userName, password, remember) ? Response.ofSuccess("ok") : Response.ofFail("login failed");
	}

	public Response<String> logout() {
		try {
			String url = buildUrl(XxlJobConstants.logoutPath(AdminVersion.V3_X));
			HttpResponse<String> r = unirestInstance.post(url)
					.header(XxlJobConstants.XXL_RPC_ACCESS_TOKEN, properties.getAccessToken()).asString();
			if (r.isSuccess()) { log.info("xxl-job logout ok"); authenticated = false; return Response.ofSuccess(); }
			log.error("xxl-job logout fail"); return Response.ofFail(r.getStatusText());
		} catch (Exception e) { return Response.ofFail(e.getMessage()); }
	}

	private void loginIfNeed() {
		if (authenticated) return;
		synchronized (loginLock) { if (authenticated) return; doLogin(adminProperties.getUsername(), adminProperties.getPassword(), adminProperties.isRemember()); }
	}

	private boolean doLogin(String userName, String password, boolean remember) {
		try {
			String url = buildUrl(XxlJobConstants.loginPath(AdminVersion.V3_X));
			log.info("xxl-job login POST {}", url);
			HttpResponse<String> r = unirestInstance.post(url)
					.header(XxlJobConstants.XXL_RPC_ACCESS_TOKEN, properties.getAccessToken())
					.field("userName", userName).field("password", password).field("ifRemember", remember ? "on" : "off").asString();
			log.info("xxl-job login response: status={}, body={}", r.getStatus(), safeBody(r));
			if (r.isSuccess()) { log.info("xxl-job login SUCCESS"); authenticated = true; return true; }
			log.error("xxl-job login FAIL: status={}", r.getStatus()); authenticated = false; return false;
		} catch (Exception e) { log.error("xxl-job login ERROR: {}", e.getMessage(), e); authenticated = false; return false; }
	}

	private String safeBody(HttpResponse<String> r) {
		try { String b = r.getBody(); return b != null ? b.substring(0, Math.min(b.length(), 200)) : "null"; } catch (Exception e) { return "err"; }
	}

	// ======================== HTTP ========================

	private HttpResponse<String> doPost(String suffix, Map<String, Object> paramMap) {
		String url = buildUrl(suffix); loginIfNeed();
		HttpResponse<String> r = executePost(url, paramMap);
		if (r.isSuccess() && !isResponseJson(r)) {
			log.warn("xxl-job session expired, re-login. body:{}", safeBody(r));
			authenticated = false; loginIfNeed(); return executePost(url, paramMap);
		}
		return r;
	}

	private HttpResponse<String> executePost(String url, Map<String, Object> paramMap) {
		return unirestInstance.post(url).header(XxlJobConstants.XXL_RPC_ACCESS_TOKEN, properties.getAccessToken()).fields(paramMap).asString();
	}

	private boolean isResponseJson(HttpResponse<String> r) {
		String ct = r.getHeaders().getFirst("Content-Type"); return ct != null && ct.contains("application/json");
	}

	private String errorMsg(HttpResponse<String> r) {
		String st = r.getStatusText(); if (st != null && !st.isEmpty()) return "HTTP " + r.getStatus() + " " + st;
		String b = null; try { b = r.getBody(); } catch (Exception ignored) {}
		if (b != null && !b.isEmpty()) return "HTTP " + r.getStatus() + ", body: " + b.substring(0, Math.min(b.length(), 200));
		return "HTTP " + r.getStatus();
	}

	// ======================== 3.x 参数: ids[] ========================

	private void putIdParam(Map<String, Object> p, Integer id) { p.put("ids[]", Collections.singletonList(id)); }

	// ======================== 响应解析 ========================

	private <T> Response<T> parseReturnT(String body) {
		JSONObject json = JSON.parseObject(body);
		int code = json.getIntValue("code", 500);
		String msg = json.getString("msg");
		String inner = json.getString("data");
		if (inner == null) inner = json.getString("content");
		if (inner != null) { T data = JSON.parseObject(inner, new TypeReference<T>() {}); return Response.ofSuccess(data); }
		return code == 200 ? Response.ofSuccess(null) : Response.of(500, msg);
	}

	private <T> Response<T> parsePageListResult(String body, Class<T> clazz) {
		JSONObject json = JSON.parseObject(body);
		if (json.containsKey("code") && json.containsKey("data")) {
			String inner = json.getString("data");
			if (inner != null) { T result = JSON.parseObject(inner, clazz); return Response.ofSuccess(result); }
		}
		T result = JSON.parseObject(body, clazz); return Response.ofSuccess(result);
	}

	// ======================== 业务 API ========================

	public Response<XxlJobGroupList> jobInfoGroupList(int start, int length) { return this.jobInfoGroupList(start, length, EMPTY, EMPTY); }
	public Response<XxlJobGroupList> jobInfoGroupList(int start, int length, String appname, String title) {
		Map<String, Object> p = new HashMap<>(4);
		p.put("start", Math.max(0, start)); p.put("length", Math.min(length, 5)); p.put("appname", appname); p.put("title", title);
		try {
			HttpResponse<String> r = doPost(XxlJobConstants.JOBGROUP_PAGELIST, p);
			if (r.isSuccess() && isResponseJson(r)) return parsePageListResult(r.getBody(), XxlJobGroupList.class);
			return Response.ofFail(errorMsg(r));
		} catch (Exception e) { return Response.ofFail(e.getMessage()); }
	}

	public Response<XxlJobGroup> jobInfoGroup(Integer id) {
		if (Objects.isNull(id)) return Response.ofFail("任务执行器主键ID不能为空");
		Map<String, Object> p = new HashMap<>(1); p.put("id", id);
		return doRequest(XxlJobConstants.JOBGROUP_GET, p);
	}

	public Response<String> addJobGroup(XxlJobGroup g) {
		if (Objects.isNull(g)) return Response.ofFail("任务执行器信息不能为空");
		return doRequest(XxlJobConstants.jobGroupSavePath(AdminVersion.V3_X), JSON.parseObject(JSON.toJSONString(g), Map.class));
	}
	public Response<String> updateJobGroup(XxlJobGroup g) { return doRequest(XxlJobConstants.JOBGROUP_UPDATE, JSON.parseObject(JSON.toJSONString(g), Map.class)); }
	public Response<String> removeJobGroup(Integer id) {
		if (Objects.isNull(id)) return Response.ofFail("任务执行器主键ID不能为空");
		Map<String, Object> p = new HashMap<>(1); putIdParam(p, id);
		return doRequest(XxlJobConstants.jobGroupRemovePath(AdminVersion.V3_X), p);
	}

	public Response<XxlJobInfoList> jobInfoList(int s, int l, Integer g) { return this.jobInfoList(s, l, g, -1, EMPTY, EMPTY, EMPTY); }
	public Response<XxlJobInfoList> jobInfoList(int s, int l, Integer g, Integer ts) { return this.jobInfoList(s, l, g, ts, EMPTY, EMPTY, EMPTY); }
	public Response<XxlJobInfoList> jobInfoList(int start, int length, Integer jobGroup, Integer triggerStatus, String jobDesc, String executorHandler, String author) {
		if (Objects.isNull(jobGroup)) return Response.ofFail("任务执行器主键ID不能为空");
		Map<String, Object> p = new HashMap<>(7);
		p.put("start", Math.max(0, start)); p.put("length", Math.max(length, 5));
		p.put("jobGroup", jobGroup); p.put("triggerStatus", triggerStatus);
		p.put("jobDesc", jobDesc); p.put("executorHandler", executorHandler); p.put("author", author);
		try {
			HttpResponse<String> r = doPost(XxlJobConstants.JOBINFO_PAGELIST, p);
			if (r.isSuccess() && isResponseJson(r)) return parsePageListResult(r.getBody(), XxlJobInfoList.class);
			return Response.ofFail(errorMsg(r));
		} catch (Exception e) { return Response.ofFail(e.getMessage()); }
	}

	public Response<String> addUniqueJob(XxlJobInfo j) {
		if (Objects.isNull(j)) return Response.ofFail("任务信息不能为空");
		if (Objects.isNull(j.getJobGroup())) return Response.ofFail("任务执行器主键ID不能为空");
		Response<XxlJobInfoList> r1 = this.jobInfoList(0, Integer.MAX_VALUE, j.getJobGroup());
		if (!r1.isSuccess()) return Response.ofFail("获取任务列表失败，" + r1.getMsg());
		XxlJobInfoList list = r1.getData();
		if (Objects.isNull(list) || CollectionUtils.isEmpty(list.getData())) return this.addJob(j);
		StringUtils.trimWhitespace(j.getJobDesc());
		if (list.getData().stream().anyMatch(x -> StringUtils.trimWhitespace(x.getJobDesc()).equals(StringUtils.trimWhitespace(j.getJobDesc()))))
			return Response.ofFail("任务组内已存在相同描述的任务");
		return this.addJob(j);
	}

	public Response<String> addJob(XxlJobInfo j) {
		if (Objects.isNull(j)) return Response.ofFail("任务信息不能为空");
		if (Objects.isNull(j.getJobGroup())) return Response.ofFail("任务执行器主键ID不能为空");
		if (Objects.isNull(j.getJobDesc())) return Response.ofFail("任务描述不能为空");
		return doRequest(XxlJobConstants.jobInfoAddPath(AdminVersion.V3_X), JSON.parseObject(JSON.toJSONString(j), Map.class));
	}
	public Response<String> updateJob(XxlJobInfo j) {
		if (Objects.isNull(j)) return Response.ofFail("任务信息不能为空");
		if (Objects.isNull(j.getId())) return Response.ofFail("任务ID不能为空");
		if (Objects.isNull(j.getJobDesc())) return Response.ofFail("任务描述不能为空");
		return doRequest(XxlJobConstants.JOBINFO_UPDATE, JSON.parseObject(JSON.toJSONString(j), Map.class));
	}

	public Response<String> removeJob(Integer id) {
		if (Objects.isNull(id)) return Response.ofFail("任务ID不能为空");
		Map<String, Object> p = new HashMap<>(1); putIdParam(p, id);
		return doRequest(XxlJobConstants.jobInfoRemovePath(AdminVersion.V3_X), p);
	}
	public Response<String> stopJob(Integer id) {
		if (Objects.isNull(id)) return Response.ofFail("任务ID不能为空");
		Map<String, Object> p = new HashMap<>(1); putIdParam(p, id);
		return doRequest(XxlJobConstants.JOBINFO_STOP, p);
	}
	public Response<String> startJob(Integer id) {
		if (Objects.isNull(id)) return Response.ofFail("任务ID不能为空");
		Map<String, Object> p = new HashMap<>(1); putIdParam(p, id);
		return doRequest(XxlJobConstants.JOBINFO_START, p);
	}
	public Response<String> triggerJob(XxlJobInfo j) {
		if (Objects.isNull(j)) return Response.ofFail("任务信息不能为空");
		return this.triggerJob(j.getId(), j.getExecutorParam());
	}
	public Response<String> triggerJob(Integer id, String executorParam) {
		if (Objects.isNull(id)) return Response.ofFail("任务ID不能为空");
		Map<String, Object> p = new HashMap<>(3);
		p.put("id", id); p.put("executorParam", executorParam); p.put("addressList", "");
		return doRequest(XxlJobConstants.JOBINFO_TRIGGER, p);
	}

	// ======================== 内部 ========================

	private <T> Response<T> doRequest(String suffix, Map<String, Object> paramMap) {
		try {
			HttpResponse<String> r = doPost(suffix, paramMap);
			if (r.isSuccess()) {
				String body = r.getBody(); log.debug("xxl-job response: {}", body);
				if (isResponseJson(r)) return parseReturnT(body);
				log.error("xxl-job non-JSON suffix:{}, body:{}", suffix, safeBody(r));
			}
			log.error("xxl-job fail. suffix:{} {}", suffix, errorMsg(r));
			return Response.ofFail(errorMsg(r));
		} catch (Exception e) { return Response.ofFail(e.getMessage()); }
	}
}
