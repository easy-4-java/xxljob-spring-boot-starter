package com.xxl.job.spring.boot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 各 AdminVersion 路径与 Cookie 解析单元测试。
 */
@DisplayName("XxlJobConstants 版本路径测试")
class XxlJobConstantsVersionTest {

    @Test
    @DisplayName("V2_X 应使用 V2 登录与 CRUD 路径")
    void v2Paths() {
        assertThat(XxlJobConstants.loginPath(AdminVersion.V2_X)).isEqualTo("/login");
        assertThat(XxlJobConstants.logoutPath(AdminVersion.V2_X)).isEqualTo("/logout");
        assertThat(XxlJobConstants.jobGroupSavePath(AdminVersion.V2_X)).isEqualTo("/jobgroup/save");
        assertThat(XxlJobConstants.jobGroupRemovePath(AdminVersion.V2_X)).isEqualTo("/jobgroup/remove");
        assertThat(XxlJobConstants.jobInfoAddPath(AdminVersion.V2_X)).isEqualTo("/jobinfo/add");
        assertThat(XxlJobConstants.jobInfoRemovePath(AdminVersion.V2_X)).isEqualTo("/jobinfo/remove");
        assertThat(AdminVersion.V2_X.loginCookieName()).isEqualTo(XxlJobConstants.COOKIE_LOGIN_IDENTITY);
        assertThat(AdminVersion.V2_X.usesV3Login()).isFalse();
        assertThat(AdminVersion.V2_X.usesV3FullApi()).isFalse();
    }

    @Test
    @DisplayName("V3_2_X 应使用 V3 登录 + V2 CRUD 路径")
    void v32Paths() {
        assertThat(XxlJobConstants.loginPath(AdminVersion.V3_2_X)).isEqualTo("/auth/doLogin");
        assertThat(XxlJobConstants.logoutPath(AdminVersion.V3_2_X)).isEqualTo("/auth/logout");
        assertThat(XxlJobConstants.jobGroupSavePath(AdminVersion.V3_2_X)).isEqualTo("/jobgroup/save");
        assertThat(XxlJobConstants.jobGroupRemovePath(AdminVersion.V3_2_X)).isEqualTo("/jobgroup/remove");
        assertThat(XxlJobConstants.jobInfoAddPath(AdminVersion.V3_2_X)).isEqualTo("/jobinfo/add");
        assertThat(XxlJobConstants.jobInfoRemovePath(AdminVersion.V3_2_X)).isEqualTo("/jobinfo/remove");
        assertThat(AdminVersion.V3_2_X.loginCookieName()).isEqualTo(XxlJobConstants.COOKIE_LOGIN_TOKEN);
        assertThat(AdminVersion.V3_2_X.usesV3Login()).isTrue();
        assertThat(AdminVersion.V3_2_X.usesV3FullApi()).isFalse();
    }

    @Test
    @DisplayName("V3_X 应使用完整 V3 路径")
    void v3Paths() {
        assertThat(XxlJobConstants.loginPath(AdminVersion.V3_X)).isEqualTo("/auth/doLogin");
        assertThat(XxlJobConstants.logoutPath(AdminVersion.V3_X)).isEqualTo("/auth/logout");
        assertThat(XxlJobConstants.jobGroupSavePath(AdminVersion.V3_X)).isEqualTo("/jobgroup/insert");
        assertThat(XxlJobConstants.jobGroupRemovePath(AdminVersion.V3_X)).isEqualTo("/jobgroup/delete");
        assertThat(XxlJobConstants.jobInfoAddPath(AdminVersion.V3_X)).isEqualTo("/jobinfo/insert");
        assertThat(XxlJobConstants.jobInfoRemovePath(AdminVersion.V3_X)).isEqualTo("/jobinfo/delete");
        assertThat(AdminVersion.V3_X.loginCookieName()).isEqualTo(XxlJobConstants.COOKIE_LOGIN_TOKEN);
        assertThat(AdminVersion.V3_X.usesV3Login()).isTrue();
        assertThat(AdminVersion.V3_X.usesV3FullApi()).isTrue();
    }

}
