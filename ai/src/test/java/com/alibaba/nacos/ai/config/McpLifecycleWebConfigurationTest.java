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

package com.alibaba.nacos.ai.config;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.service.mcp.McpLifecycleManagementStateService;
import com.alibaba.nacos.ai.service.mcp.McpResourceLocator;
import com.alibaba.nacos.ai.web.McpLifecycleIdentityFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class McpLifecycleWebConfigurationTest {
    
    @Test
    void testFilterRunsBeforeAuthorizationOnAdminMcpPaths() {
        McpLifecycleWebConfiguration configuration = new McpLifecycleWebConfiguration();
        McpLifecycleIdentityFilter filter = configuration.mcpLifecycleIdentityFilter(
            mock(McpLifecycleManagementStateService.class), mock(McpResourceLocator.class));
        
        FilterRegistrationBean<McpLifecycleIdentityFilter> registration =
            configuration.mcpLifecycleIdentityFilterRegistration(filter);
        
        assertSame(filter, registration.getFilter());
        assertEquals("mcpLifecycleIdentityFilter", registration.getFilterName());
        assertEquals(5, registration.getOrder());
        assertEquals(2, registration.getUrlPatterns().size());
        org.junit.jupiter.api.Assertions.assertTrue(registration.getUrlPatterns()
            .contains(Constants.MCP_ADMIN_PATH));
        org.junit.jupiter.api.Assertions.assertTrue(registration.getUrlPatterns()
            .contains(Constants.MCP_ADMIN_PATH + "/*"));
    }
}
