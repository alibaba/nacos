/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.test.openapi.auth;

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verify that Hub governance is reserved for existing global administrators. */
public class HubAdministratorITCase extends AuthITCase {

    private static final String ROLE_PATH = CONTEXT_PATH + "/v3/auth/role";

    private static final String HUB_PATH = CONTEXT_PATH + "/v3/admin/ai/hub/skills";

    @Test
    void explicitAclsCannotReplaceGlobalAdministrator() throws Exception {
        TestIdentity identity = createIdentityWithoutPermission("hub-admin-boundary");
        String statePath = HUB_PATH + "/governance-state?hubEntryId=" + Long.MAX_VALUE;
        Map<String, String> takedown = params("hubEntryId", Long.toString(Long.MAX_VALUE),
                "expectedMetaVersion", "1", "reason", "Administrator boundary test");

        assertResourceNotFound(get(SERVER_BASE_URL, statePath, adminToken()));
        assertResourceNotFound(postForm(SERVER_BASE_URL, HUB_PATH + "/force-unshare", adminToken(), takedown));
        assertDenied(get(SERVER_BASE_URL, statePath, null));
        assertDenied(get(SERVER_BASE_URL, statePath, identity.token()));
        for (String resource : new String[] {AiConstants.Hub.ADMIN_RESOURCE, "*"}) {
            grantPermission(identity, resource, "rw");
        }
        assertDenied(get(SERVER_BASE_URL, statePath, identity.token()));
        assertDenied(postForm(SERVER_BASE_URL, HUB_PATH + "/force-unshare", identity.token(), takedown));
        assertDenied(get(SERVER_BASE_URL, ROLE_PATH + "/list?pageNo=1&pageSize=20", identity.token()));
    }

    private void assertResourceNotFound(Response response) {
        assertEquals(404, response.status(), response.body());
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND.getCode(),
                JacksonUtils.toObj(response.body()).get("code").asInt(), response.body());
    }
}
