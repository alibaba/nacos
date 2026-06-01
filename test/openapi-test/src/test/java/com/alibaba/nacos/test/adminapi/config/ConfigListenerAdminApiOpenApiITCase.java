/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.test.adminapi.config;

import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.http.param.Query;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration tests for config listener admin OpenAPIs.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: {@code /config/listener} returns config-scoped listener state and
 *     {@code /listener} returns ip-scoped listener state with their documented {@code queryType} values.</li>
 *     <li>Boundary/validation: omitted namespace uses public for config listener lookup, {@code aggregation=false},
 *     {@code all=true}, and namespace filtering are accepted even when no listeners are registered.</li>
 *     <li>Exception/error handling: required identity fields and required {@code ip} return HTTP 400 with the v3
 *     {@code Result} error envelope instead of HTTP 500.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class ConfigListenerAdminApiOpenApiITCase extends ConfigAdminApiBaseITCase {
    
    @Test
    public void testConfigListenerQueryReturnsConfigQueryType() throws Exception {
        String dataId = randomDataId("listener");
        String groupName = randomGroupName("listener");
        publishConfig(dataId, groupName, "", "listener-content");
        addCleanup(() -> deleteConfigQuietly(dataId, groupName, ""));
        
        JsonNode defaultAggregation = getJsonOk(ADMIN_CONFIG_LISTENER_PATH,
                configQuery(dataId, groupName, null)).get("data");
        assertListenerInfo(defaultAggregation, "config");
        assertEquals(0, defaultAggregation.get("listenersStatus").size(), defaultAggregation.toString());
        
        JsonNode noAggregation = getJsonOk(ADMIN_CONFIG_LISTENER_PATH,
                configQuery(dataId, groupName, "").addParam("aggregation", "false")).get("data");
        assertListenerInfo(noAggregation, "config");
        assertEquals(0, noAggregation.get("listenersStatus").size(), noAggregation.toString());
    }
    
    @Test
    public void testIpListenerQueryAcceptsAllAndNamespaceFilter() throws Exception {
        JsonNode defaultQuery = getJsonOk(ADMIN_LISTENER_PATH,
                Query.newInstance().addParam("ip", "127.0.0.1")).get("data");
        assertListenerInfo(defaultQuery, "ip");
        
        JsonNode allWithNamespace = getJsonOk(ADMIN_LISTENER_PATH,
                Query.newInstance().addParam("ip", "127.0.0.1").addParam("all", "true")
                        .addParam("namespaceId", DEFAULT_NAMESPACE).addParam("aggregation", "false")).get("data");
        assertListenerInfo(allWithNamespace, "ip");
    }
    
    @Test
    public void testListenerRequiredParametersReturnBadRequest() throws Exception {
        String dataId = randomDataId("listener-required");
        String groupName = randomGroupName("listener-required");
        assertError(getRaw(ADMIN_CONFIG_LISTENER_PATH, Query.newInstance().addParam("groupName", groupName)),
                400, ErrorCode.PARAMETER_MISSING, "dataId");
        assertError(getRaw(ADMIN_CONFIG_LISTENER_PATH, Query.newInstance().addParam("dataId", dataId)),
                400, ErrorCode.PARAMETER_MISSING, "groupName");
        assertError(getRaw(ADMIN_LISTENER_PATH), 400, ErrorCode.PARAMETER_MISSING, "ip");
    }
}
