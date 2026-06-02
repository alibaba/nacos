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

package com.alibaba.nacos.test.adminapi.ai.pipeline;

import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.test.adminapi.ai.AiAdminApiBaseITCase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration tests for pipeline admin OpenAPI {@code /nacos/v3/admin/ai/pipelines}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: current and legacy list endpoints return the page contract for a resource type with
 *     optional resourceName, namespaceId, and version filters.</li>
 *     <li>Boundary/validation: resourceType is required for list; pageNo and pageSize are validated by PageForm; the
 *     query-parameter detail endpoint requires pipelineId. The path-variable detail endpoint is kept as deprecated
 *     compatibility and shares the not-found contract.</li>
 *     <li>Exception/error handling: unknown pipeline IDs return HTTP 404 with a wrapped RESOURCE_NOT_FOUND body. A
 *     successful detail query is not created here because pipeline rows require configured publish-pipeline plugins;
 *     in the default standalone IT environment list may legitimately be empty.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class PipelineAdminApiOpenApiITCase extends AiAdminApiBaseITCase {
    
    @Test
    public void testListPipelinesCurrentAndLegacyReturnPageContract() throws Exception {
        Query query = Query.newInstance().addParam("resourceType", "prompt")
                .addParam("resourceName", randomAiName("pipeline-resource"))
                .addParam("namespaceId", DEFAULT_NAMESPACE).addParam("version", "1.0.0")
                .addParam("pageNo", "1").addParam("pageSize", "10");
        
        JsonNode currentPage = getJsonOk(ADMIN_PIPELINE_LIST_PATH, query).get("data");
        assertEmptyPageShape(currentPage);
        JsonNode legacyPage = getJsonOk(ADMIN_PIPELINE_PATH, query).get("data");
        assertEquals(currentPage.get("totalCount").asInt(), legacyPage.get("totalCount").asInt(),
                legacyPage.toString());
        assertEmptyPageShape(legacyPage);
    }
    
    @Test
    public void testListPipelinesValidationErrors() throws Exception {
        assertError(getRaw(ADMIN_PIPELINE_LIST_PATH, Query.newInstance().addParam("pageNo", "1")
                .addParam("pageSize", "10")), 400, ErrorCode.PARAMETER_VALIDATE_ERROR, "resourceType");
        assertError(getRaw(ADMIN_PIPELINE_LIST_PATH, Query.newInstance().addParam("resourceType", "prompt")
                .addParam("pageNo", "0").addParam("pageSize", "10")), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "pageNo");
        assertError(getRaw(ADMIN_PIPELINE_PATH, Query.newInstance().addParam("pageNo", "1")
                .addParam("pageSize", "10")), 400, ErrorCode.PARAMETER_VALIDATE_ERROR, "resourceType");
    }
    
    @Test
    public void testPipelineDetailValidationAndNotFoundErrors() throws Exception {
        assertError(getRaw(ADMIN_PIPELINE_DETAIL_PATH, Query.newInstance()), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "pipelineId");
        
        String absentPipelineId = "pipeline-" + randomAiName("absent");
        assertError(getRaw(ADMIN_PIPELINE_DETAIL_PATH,
                Query.newInstance().addParam("pipelineId", absentPipelineId)), 404,
                ErrorCode.RESOURCE_NOT_FOUND, "Pipeline execution not found");
        assertError(getRaw(ADMIN_PIPELINE_PATH + "/" + absentPipelineId), 404,
                ErrorCode.RESOURCE_NOT_FOUND, "Pipeline execution not found");
    }
}
