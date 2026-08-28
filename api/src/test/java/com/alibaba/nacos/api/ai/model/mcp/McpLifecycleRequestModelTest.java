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

package com.alibaba.nacos.api.ai.model.mcp;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;

class McpLifecycleRequestModelTest {
    
    @Test
    void testDraftRequestAccessors() {
        McpServerBasicInfo server = new McpServerBasicInfo();
        McpToolSpecification tools = new McpToolSpecification();
        McpResourceSpecification resources = new McpResourceSpecification();
        McpEndpointSpec endpoint = new McpEndpointSpec();
        McpLifecycleDraftRequest request = new McpLifecycleDraftRequest();
        
        request.setServerSpecification(server);
        request.setToolSpecification(tools);
        request.setResourceSpecification(resources);
        request.setEndpointSpecification(endpoint);
        
        assertSame(server, request.getServerSpecification());
        assertSame(tools, request.getToolSpecification());
        assertSame(resources, request.getResourceSpecification());
        assertSame(endpoint, request.getEndpointSpecification());
    }
    
    @Test
    void testVersionCommandAndLabelsRequestAccessors() {
        String mcpName = "weather";
        String version = "1.0.0";
        Map<String, String> labels = Collections.singletonMap("stable", version);
        McpLifecycleVersionCommand command = new McpLifecycleVersionCommand();
        McpLifecycleLabelsUpdateRequest labelsRequest = new McpLifecycleLabelsUpdateRequest();
        
        command.setMcpName(mcpName);
        command.setVersion(version);
        labelsRequest.setMcpName(mcpName);
        labelsRequest.setLabels(labels);
        
        assertSame(mcpName, command.getMcpName());
        assertSame(version, command.getVersion());
        assertSame(mcpName, labelsRequest.getMcpName());
        assertSame(labels, labelsRequest.getLabels());
    }
}
