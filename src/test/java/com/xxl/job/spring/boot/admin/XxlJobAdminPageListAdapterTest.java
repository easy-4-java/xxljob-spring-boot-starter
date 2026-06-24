package com.xxl.job.spring.boot.admin;

import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.spring.boot.AdminVersion;
import com.xxl.job.spring.boot.model.XxlJobGroupList;
import com.xxl.job.spring.boot.model.XxlJobInfoList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * pageList 参数与响应解析单元测试。
 */
@DisplayName("XxlJobAdminPageListAdapter 单元测试")
class XxlJobAdminPageListAdapterTest {

    @Test
    @DisplayName("V2 分页参数应使用 start/length")
    void v2PageParams() {
        Map<String, Object> p = new HashMap<>();
        XxlJobAdminPageListAdapter.putPageParams(p, 10, 20, AdminVersion.V2_X);
        assertThat(p).containsEntry("start", 10).containsEntry("length", 20);
        assertThat(p).doesNotContainKey("offset").doesNotContainKey("pagesize");
    }

    @Test
    @DisplayName("V3 分页参数应使用 offset/pagesize")
    void v3PageParams() {
        Map<String, Object> p = new HashMap<>();
        XxlJobAdminPageListAdapter.putPageParams(p, 10, 20, AdminVersion.V3_X);
        assertThat(p).containsEntry("offset", 10).containsEntry("pagesize", 20);
        assertThat(p).doesNotContainKey("start").doesNotContainKey("length");
    }

    @Test
    @DisplayName("V3_2 分页参数应仍使用 start/length")
    void v32PageParams() {
        Map<String, Object> p = new HashMap<>();
        XxlJobAdminPageListAdapter.putPageParams(p, 0, 5, AdminVersion.V3_2_X);
        assertThat(p).containsEntry("start", 0).containsEntry("length", 5);
    }

    @Test
    @DisplayName("V2 id 参数应为 id")
    void v2IdParam() {
        Map<String, Object> p = new HashMap<>();
        XxlJobAdminPageListAdapter.putIdParam(p, 42, AdminVersion.V2_X);
        assertThat(p).containsEntry("id", 42);
    }

    @Test
    @DisplayName("V3 id 参数应为 ids[]")
    void v3IdParam() {
        Map<String, Object> p = new HashMap<>();
        XxlJobAdminPageListAdapter.putIdParam(p, 42, AdminVersion.V3_X);
        assertThat(p).containsEntry("ids[]", "42");
    }

    @Test
    @DisplayName("应解析 V2 DataTables 响应")
    void parseV2Response() {
        String body = "{\"recordsTotal\":2,\"recordsFiltered\":2,\"data\":[{\"id\":1},{\"id\":2}]}";
        ReturnT<XxlJobInfoList> result = XxlJobAdminPageListAdapter.parseJobInfoList(body, AdminVersion.V2_X);
        assertThat(result.getCode()).isEqualTo(ReturnT.SUCCESS_CODE);
        assertThat(result.getContent().getRecordsTotal()).isEqualTo(2);
        assertThat(result.getContent().getData()).hasSize(2);
    }

    @Test
    @DisplayName("应解析 V3 PageModel 响应")
    void parseV3Response() {
        String body = "{\"code\":200,\"data\":{\"total\":3,\"data\":[{\"id\":1,\"jobDesc\":\"a\"},"
                + "{\"id\":2,\"jobDesc\":\"b\"},{\"id\":3,\"jobDesc\":\"c\"}]}}";
        ReturnT<XxlJobInfoList> result = XxlJobAdminPageListAdapter.parseJobInfoList(body, AdminVersion.V3_X);
        assertThat(result.getCode()).isEqualTo(ReturnT.SUCCESS_CODE);
        assertThat(result.getContent().getRecordsTotal()).isEqualTo(3);
        assertThat(result.getContent().getData()).hasSize(3);
    }

    @Test
    @DisplayName("应解析 V3 jobgroup PageModel 响应")
    void parseV3GroupResponse() {
        String body = "{\"code\":200,\"data\":{\"total\":1,\"data\":[{\"id\":10,\"appname\":\"test\"}]}}";
        ReturnT<XxlJobGroupList> result = XxlJobAdminPageListAdapter.parseJobGroupList(body, AdminVersion.V3_X);
        assertThat(result.getContent().getRecordsTotal()).isEqualTo(1);
        assertThat(result.getContent().getData().get(0).getAppName()).isEqualTo("test");
    }

}
