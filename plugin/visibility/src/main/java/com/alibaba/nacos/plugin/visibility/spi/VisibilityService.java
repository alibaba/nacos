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

package com.alibaba.nacos.plugin.visibility.spi;

import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import com.alibaba.nacos.plugin.visibility.model.VisibilityQueryContext;
import com.alibaba.nacos.plugin.visibility.model.VisibilityResource;

import java.util.Properties;

/**
 * SPI for resource visibility service.
 *
 * @author xiweng.yy
 */
public interface VisibilityService {
    
    /**
     * Initialize service with external properties.
     *
     * <p>Property source is managed by {@link VisibilityPluginManager}. Default no-op keeps backward compatibility
     * for existing SPI implementations.</p>
     *
     * @param properties service-specific properties
     */
    default void init(Properties properties) {
    }
    
    /**
     * Resolve default scope for a newly created resource.
     *
     * <p>Default implementation keeps backward compatibility for existing SPI implementations.</p>
     *
     * @param identity     current identity
     * @param apiType      current api type
     * @param resourceType resource type, such as skill / agentspec
     * @return default scope for new resource
     */
    default String resolveDefaultScopeForCreate(String identity, String apiType,
        String resourceType) {
        return VisibilityConstants.SCOPE_PRIVATE;
    }
    
    ValidationResult validateVisibility(String identity, String action, String apiType,
        VisibilityResource resource);
    
    QueryAdvisor adviseQuery(String identity, String action, String apiType,
        VisibilityQueryContext context);
    
    String getVisibilityServiceName();
}
