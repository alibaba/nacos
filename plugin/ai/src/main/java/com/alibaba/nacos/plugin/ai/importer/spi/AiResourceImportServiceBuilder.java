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

package com.alibaba.nacos.plugin.ai.importer.spi;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.plugin.PluginConfigSpec;

import java.util.Set;

/**
 * Managed plugin SPI for creating request-scoped {@link AiResourceImportService} instances.
 *
 * <p>One builder represents one external source. The builder is a stable process-level plugin
 * instance managed by the unified plugin manager, while each built service is scoped to one import
 * API request.</p>
 *
 * @author xiweng.yy
 * @since 3.2.1
 */
public interface AiResourceImportServiceBuilder extends PluginConfigSpec {
    
    /**
     * Managed plugin name and import source identifier.
     *
     * @return unique plugin name
     */
    String pluginName();
    
    /**
     * Importer protocol name retained for API metadata compatibility.
     *
     * @return importer type, for example {@code mcp-registry}
     */
    String importerType();
    
    /**
     * Current display name from the applied configuration snapshot.
     *
     * @return display name
     */
    String displayName();
    
    /**
     * Current description from the applied configuration snapshot.
     *
     * @return description
     */
    String description();
    
    /**
     * Resource types supported by this source.
     *
     * @return supported resource type set
     */
    Set<String> supportedResourceTypes();
    
    /**
     * Build an import service from the current immutable configuration snapshot.
     *
     * @return request-scoped import service
     * @throws NacosException if the current configuration cannot create a service
     */
    AiResourceImportService build() throws NacosException;
}
