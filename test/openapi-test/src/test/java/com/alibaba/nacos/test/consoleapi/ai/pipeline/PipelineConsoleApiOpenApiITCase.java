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

package com.alibaba.nacos.test.consoleapi.ai.pipeline;

import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.test.consoleapi.ai.AiConsoleApiBaseITCase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for pipeline console OpenAPI {@code /v3/console/ai/pipelines}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: the current list endpoint returns the page contract for a resource type with optional
 *     resourceName, namespaceId, and version filters.</li>
 *     <li>Boundary/validation: resourceType is required for list; pageNo and pageSize are validated by PageForm; the
 *     query-parameter detail endpoint requires pipelineId.</li>
 *     <li>Exception/error handling: unknown pipeline IDs return HTTP 404 with a wrapped RESOURCE_NOT_FOUND body. A
 *     successful detail query is not created here because pipeline rows require configured publish-pipeline plugins;
 *     in the default standalone IT environment list may legitimately be empty. Deprecated base-path list and
 *     path-variable detail endpoints return HTTP 410 by default.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class PipelineConsoleApiOpenApiITCase extends AiConsoleApiBaseITCase {
    
    @Test
    public void testListPipelinesCurrentAndLegacyCompatibilityGate() throws Exception {
        Query query = Query.newInstance().addParam("resourceType", "prompt")
                .addParam("resourceName", randomAiName("pipeline-resource"))
                .addParam("namespaceId", DEFAULT_NAMESPACE).addParam("version", "1.0.0")
                .addParam("pageNo", "1").addParam("pageSize", "10");
        
        JsonNode currentPage = getJsonOk(CONSOLE_PIPELINE_LIST_PATH, query).get("data");
        assertEmptyPageShape(currentPage);
        assertError(getRaw(CONSOLE_PIPELINE_PATH, query), 410, ErrorCode.API_DEPRECATED,
                "GET /v3/console/ai/pipelines/list");
    }
    
    @Test
    public void testListPipelinesValidationErrors() throws Exception {
        assertError(getRaw(CONSOLE_PIPELINE_LIST_PATH, Query.newInstance().addParam("pageNo", "1")
                .addParam("pageSize", "10")), 400, ErrorCode.PARAMETER_VALIDATE_ERROR, "resourceType");
        assertError(getRaw(CONSOLE_PIPELINE_LIST_PATH, Query.newInstance().addParam("resourceType", "prompt")
                .addParam("pageNo", "0").addParam("pageSize", "10")), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "pageNo");
    }
    
    @Test
    public void testPipelineDetailValidationAndNotFoundErrors() throws Exception {
        assertError(getRaw(CONSOLE_PIPELINE_DETAIL_PATH, Query.newInstance()), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "pipelineId");
        
        String absentPipelineId = "pipeline-" + randomAiName("absent");
        assertError(getRaw(CONSOLE_PIPELINE_DETAIL_PATH,
                Query.newInstance().addParam("pipelineId", absentPipelineId)), 404,
                ErrorCode.RESOURCE_NOT_FOUND, "Pipeline execution not found");
        assertError(getRaw(CONSOLE_PIPELINE_PATH + "/" + absentPipelineId), 410,
                ErrorCode.API_DEPRECATED,
                "GET /v3/console/ai/pipelines/detail?pipelineId={pipelineId}");
    }
}
