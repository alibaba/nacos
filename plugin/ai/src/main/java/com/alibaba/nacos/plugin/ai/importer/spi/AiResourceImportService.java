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
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportArtifact;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportCandidatePage;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportContext;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportItem;

import java.util.Set;

/**
 * AI resource import service SPI.
 *
 * <p>Implementations own only external source discovery and conversion to import artifacts. They
 * must not write Nacos resources directly.</p>
 *
 * @author xiweng.yy
 * @since 3.2.1
 */
public interface AiResourceImportService {
    
    /**
     * Importer implementation name, for example {@code mcp-registry}.
     *
     * @return importer type
     */
    String importerType();
    
    /**
     * Resource types supported by this importer, for example {@code mcp} or {@code skill}.
     *
     * @return supported resource type set
     */
    Set<String> supportedResourceTypes();
    
    /**
     * Search external candidates from the resolved source.
     *
     * <p>The returned candidates must contain metadata only. Full importable payloads must be
     * returned only from {@link #fetch(AiResourceImportContext, AiResourceImportItem)}.</p>
     *
     * @param context import context with resolved source and query options
     * @return candidate page
     * @throws NacosException if the source cannot be searched
     */
    AiResourceImportCandidatePage search(AiResourceImportContext context) throws NacosException;
    
    /**
     * Fetch a selected external item as an import artifact.
     *
     * @param context import context with resolved source and runtime limits
     * @param item selected external item
     * @return import artifact
     * @throws NacosException if the artifact cannot be fetched or converted
     */
    AiResourceImportArtifact fetch(AiResourceImportContext context, AiResourceImportItem item)
        throws NacosException;
}
