/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.paramcheck.impl;

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.remote.request.QueryMcpServerRequest;
import com.alibaba.nacos.api.ai.remote.request.ReleaseMcpServerRequest;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.paramcheck.ParamInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

class McpServerRequestParamExtractorTest {
    
    McpServerRequestParamExtractor extractor;
    
    @BeforeEach
    void setUp() {
        extractor = new McpServerRequestParamExtractor();
    }
    
    @AfterEach
    void tearDown() {
    }
    
    @Test
    void extractParam() throws NacosException {
        QueryMcpServerRequest request = new QueryMcpServerRequest();
        request.setMcpName("test");
        request.setNamespaceId(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE);
        request.setMcpId("");
        List<ParamInfo> paramInfos = extractor.extractParam(request);
        Assertions.assertEquals(1, paramInfos.size());
        Assertions.assertEquals(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, paramInfos.get(0).getNamespaceId());
        Assertions.assertEquals("test", paramInfos.get(0).getMcpName());
        Assertions.assertEquals("", paramInfos.get(0).getMcpId());
    }
    
    @Test
    void extractParamForReleaseMcpServerRequest() throws NacosException {
        ReleaseMcpServerRequest request = new ReleaseMcpServerRequest();
        request.setMcpName("test");
        request.setNamespaceId(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE);
        request.setMcpId("");
        List<ParamInfo> paramInfos = extractor.extractParam(request);
        Assertions.assertEquals(1, paramInfos.size());
        Assertions.assertEquals(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, paramInfos.get(0).getNamespaceId());
        Assertions.assertEquals("test", paramInfos.get(0).getMcpName());
        Assertions.assertEquals("", paramInfos.get(0).getMcpId());
        request.setServerSpecification(new McpServerBasicInfo());
        request.getServerSpecification().setName("innerName");
        paramInfos = extractor.extractParam(request);
        Assertions.assertEquals(1, paramInfos.size());
        Assertions.assertEquals(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, paramInfos.get(0).getNamespaceId());
        Assertions.assertEquals("innerName", paramInfos.get(0).getMcpName());
        Assertions.assertEquals("", paramInfos.get(0).getMcpId());
    }
}