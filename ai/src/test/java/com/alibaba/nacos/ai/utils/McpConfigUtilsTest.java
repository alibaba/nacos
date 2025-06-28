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

package com.alibaba.nacos.ai.utils;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpConfigUtilsTest {
    
    private static final String TEST_SERVER_ID = "test-server-id";
    
    private static final String TEST_VERSION = "v1.0.0";
    
    private static final String TEST_SERVER_NAME = "my-mcp-server";
    
    @Test
    void testFormatServerVersionInfoDataId() {
        String result = McpConfigUtils.formatServerVersionInfoDataId(TEST_SERVER_ID);
        assertEquals("%s-mcp-versions.json", Constants.SERVER_VERSION_CONFIG_DATA_ID_TEMPLATE);
        assertEquals("test-server-id-mcp-versions.json", result);
    }
    
    @Test
    void testFormatServerSpecInfoDataId() {
        String result = McpConfigUtils.formatServerSpecInfoDataId(TEST_SERVER_ID, TEST_VERSION);
        assertEquals("%s-%s-mcp-server.json", Constants.SERVER_SPECIFICATION_CONFIG_DATA_ID_TEMPLATE);
        assertEquals("test-server-id-v1.0.0-mcp-server.json", result);
    }
    
    @Test
    void testFormatServerToolSpecDataId() {
        String result = McpConfigUtils.formatServerToolSpecDataId(TEST_SERVER_ID, TEST_VERSION);
        assertEquals("%s-%s-mcp-tools.json", Constants.SERVER_TOOLS_SPEC_CONFIG_DATA_ID_TEMPLATE);
        assertEquals("test-server-id-v1.0.0-mcp-tools.json", result);
    }
    
    @Test
    void testFormatServerNameTagBlurSearchValue() {
        String result = McpConfigUtils.formatServerNameTagBlurSearchValue(TEST_SERVER_NAME);
        assertEquals("mcpServerName=*my-mcp-server*", result);
    }
    
    @Test
    void testFormatServerNameTagAccurateSearchValue() {
        String result = McpConfigUtils.formatServerNameTagAccurateSearchValue(TEST_SERVER_NAME);
        assertEquals("mcpServerName=my-mcp-server", result);
    }
    
    @Test
    void testIsConfigFoundPositive() {
        assertTrue(McpConfigUtils.isConfigFound(
                ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL));
    }
    
    @Test
    void testIsConfigFoundNegative() {
        assertFalse(McpConfigUtils.isConfigFound(
                ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND));
    }
    
    @Test
    void testIsConfigNotFoundPositive() {
        assertTrue(McpConfigUtils.isConfigNotFound(
                ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND));
    }
    
    @Test
    void testIsConfigNotFoundNegative() {
        assertFalse(McpConfigUtils.isConfigNotFound(
                ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL));
    }
    
    @Test
    void testBuildMcpServerVersionConfigTagsWithValidName() {
        String result = McpConfigUtils.buildMcpServerVersionConfigTags(TEST_SERVER_NAME);
        assertEquals("nacos.internal.config=mcp,mcpServerName=my-mcp-server", result);
    }
    
    @Test
    void testBuildMcpServerVersionConfigTagsWithEmptyName() {
        String result = McpConfigUtils.buildMcpServerVersionConfigTags("");
        assertEquals("nacos.internal.config=mcp,mcpServerName=", result);
    }
    
    @Test
    void testBuildMcpServerVersionConfigTagsWithNullName() {
        String result = McpConfigUtils.buildMcpServerVersionConfigTags(null);
        assertEquals("nacos.internal.config=mcp,mcpServerName=null", result);
    }
}