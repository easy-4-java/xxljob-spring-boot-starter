package com.xxl.job.spring.boot.admin;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.xxl.job.spring.boot.model.ReturnT;
import com.xxl.job.spring.boot.AdminVersion;
import com.xxl.job.spring.boot.model.XxlJobGroup;
import com.xxl.job.spring.boot.model.XxlJobGroupList;
import com.xxl.job.spring.boot.model.XxlJobInfo;
import com.xxl.job.spring.boot.model.XxlJobInfoList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * pageList 请求参数与响应体的版本适配器。
 * <p>
 * V2/V3_2：start + length → recordsTotal/recordsFiltered/data<br>
 * V3：offset + pagesize → {code, data: {total, data: [...]}}
 * </p>
 */
public final class XxlJobAdminPageListAdapter {

    private XxlJobAdminPageListAdapter() {
    }

    /**
     * 按版本写入分页查询参数。
     *
     * @param paramMap 目标参数 Map
     * @param start    V2 语义下的起始偏移（V3 映射为 offset）
     * @param length   V2 语义下的页大小（V3 映射为 pagesize）
     * @param version  admin 协议版本
     */
    public static void putPageParams(Map<String, Object> paramMap, int start, int length, AdminVersion version) {
        if (version != null && version.usesV3FullApi()) {
            paramMap.put("offset", Math.max(0, start));
            paramMap.put("pagesize", length);
        } else {
            paramMap.put("start", Math.max(0, start));
            paramMap.put("length", length);
        }
    }

    /**
     * 解析 jobgroup/pageList 响应为统一 DTO。
     */
    public static ReturnT<XxlJobGroupList> parseJobGroupList(String body, AdminVersion version) {
        return parsePageList(body, version, XxlJobGroupList.class, XxlJobGroup.class);
    }

    /**
     * 解析 jobinfo/pageList 响应为统一 DTO。
     */
    public static ReturnT<XxlJobInfoList> parseJobInfoList(String body, AdminVersion version) {
        return parsePageList(body, version, XxlJobInfoList.class, XxlJobInfo.class);
    }

    /**
     * 通用 pageList 响应解析：V3 包装 Response+PageModel，V2 直接 DataTables 格式。
     */
    private static <L, I> ReturnT<L> parsePageList(String body, AdminVersion version,
                                                    Class<L> listClass, Class<I> itemClass) {
        JSONObject json = JSON.parseObject(body);
        if (version != null && version.usesV3FullApi()) {
            return parseV3PageList(json, listClass, itemClass);
        }
        return parseV2PageList(body, listClass);
    }

    /**
     * V3 响应：{"code":200,"data":{"total":N,"data":[...]}}
     */
    private static <L, I> ReturnT<L> parseV3PageList(JSONObject json, Class<L> listClass, Class<I> itemClass) {
        int code = json.getIntValue("code", ReturnT.FAIL_CODE);
        if (code != 200) {
            String msg = json.getString("msg");
            return new ReturnT<>(code, msg);
        }
        JSONObject pageModel = json.getJSONObject("data");
        if (pageModel == null) {
            return new ReturnT<>(ReturnT.FAIL_CODE, "pageList data is null");
        }
        Integer total = pageModel.getInteger("total");
        JSONArray dataArray = pageModel.getJSONArray("data");
        List<I> items = dataArray != null ? dataArray.toList(itemClass) : null;

        try {
            L result = listClass.getDeclaredConstructor().newInstance();
            if (result instanceof XxlJobGroupList) {
                XxlJobGroupList gl = (XxlJobGroupList) result;
                gl.setRecordsTotal(total);
                gl.setRecordsFiltered(total);
                @SuppressWarnings("unchecked")
                List<XxlJobGroup> groups = (List<XxlJobGroup>) items;
                gl.setData(groups);
            } else if (result instanceof XxlJobInfoList) {
                XxlJobInfoList il = (XxlJobInfoList) result;
                il.setRecordsTotal(total);
                il.setRecordsFiltered(total);
                @SuppressWarnings("unchecked")
                List<XxlJobInfo> infos = (List<XxlJobInfo>) items;
                il.setData(infos);
            }
            return new ReturnT<>(result);
        } catch (Exception e) {
            return new ReturnT<>(ReturnT.FAIL_CODE, e.getMessage());
        }
    }

    /**
     * V2 响应：直接 DataTables 格式或外层 Response 包裹的 DataTables 格式。
     */
    private static <L> ReturnT<L> parseV2PageList(String body, Class<L> listClass) {
        JSONObject json = JSON.parseObject(body);
        // 兼容 V3 风格包裹但使用 V2 内部字段（Mock 服务器场景）
        if (json.containsKey("code") && json.containsKey("data") && json.get("data") instanceof JSONObject) {
            JSONObject inner = json.getJSONObject("data");
            if (inner.containsKey("recordsTotal")) {
                L result = JSON.parseObject(inner.toJSONString(), listClass);
                return new ReturnT<>(result);
            }
        }
        L result = JSON.parseObject(body, listClass);
        return new ReturnT<>(result);
    }

    /**
     * 按版本写入主键参数：V3 用 ids[]，V2/V3_2 用 id。
     */
    public static void putIdParam(Map<String, Object> paramMap, Integer id, AdminVersion version) {
        if (version != null && version.usesV3FullApi()) {
            paramMap.put("ids[]", String.valueOf(id));
        } else {
            paramMap.put("id", id);
        }
    }

    /**
     * 构建 jobgroup pageList 查询参数。
     */
    public static Map<String, Object> buildJobGroupPageParams(int start, int length, String appname, String title,
                                                               AdminVersion version) {
        Map<String, Object> p = new HashMap<>(4);
        putPageParams(p, start, length, version);
        p.put("appname", appname);
        p.put("title", title);
        return p;
    }

    /**
     * 构建 jobinfo pageList 查询参数。
     */
    public static Map<String, Object> buildJobInfoPageParams(int start, int length, Integer jobGroup,
                                                              Integer triggerStatus, String jobDesc,
                                                              String executorHandler, String author,
                                                              AdminVersion version) {
        Map<String, Object> p = new HashMap<>(7);
        putPageParams(p, start, length, version);
        p.put("jobGroup", jobGroup);
        p.put("triggerStatus", triggerStatus);
        p.put("jobDesc", jobDesc);
        p.put("executorHandler", executorHandler);
        p.put("author", author);
        return p;
    }

}
