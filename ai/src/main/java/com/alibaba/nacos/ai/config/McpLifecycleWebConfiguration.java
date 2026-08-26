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
import com.alibaba.nacos.core.web.NacosWebBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Web integration for MCP lifecycle identity normalization.
 *
 * @author Nacos
 */
@Configuration
@NacosWebBean
public class McpLifecycleWebConfiguration {
    
    @Bean
    public McpLifecycleIdentityFilter mcpLifecycleIdentityFilter(
        McpLifecycleManagementStateService managementStateService,
        McpResourceLocator resourceLocator) {
        return new McpLifecycleIdentityFilter(managementStateService, resourceLocator);
    }
    
    @Bean
    public FilterRegistrationBean<McpLifecycleIdentityFilter> mcpLifecycleIdentityFilterRegistration(
        McpLifecycleIdentityFilter filter) {
        FilterRegistrationBean<McpLifecycleIdentityFilter> registration =
            new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns(Constants.MCP_ADMIN_PATH, Constants.MCP_ADMIN_PATH + "/*");
        registration.setName("mcpLifecycleIdentityFilter");
        registration.setOrder(5);
        return registration;
    }
}
