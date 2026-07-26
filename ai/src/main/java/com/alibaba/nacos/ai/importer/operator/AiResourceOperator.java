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

package com.alibaba.nacos.ai.importer.operator;

import com.alibaba.nacos.api.ai.model.importer.AiResourceImportResultItem;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportValidationItem;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportArtifact;

/**
 * Applies import artifacts to a Nacos AI resource type.
 *
 * @author xiweng.yy
 * @since 3.2.1
 */
public interface AiResourceOperator {
    
    /**
     * Resource type supported by this operator.
     *
     * @return resource type, for example {@code mcp} or {@code skill}
     */
    String resourceType();
    
    /**
     * Validate one import artifact against current Nacos state.
     *
     * @param namespaceId namespace id
     * @param artifact import artifact
     * @param overwriteExisting whether overwriting existing resources is allowed
     * @return validation item
     * @throws NacosException if validation cannot be completed
     */
    AiResourceImportValidationItem validate(String namespaceId, AiResourceImportArtifact artifact,
        boolean overwriteExisting) throws NacosException;
    
    /**
     * Import one artifact into the current resource implementation.
     *
     * @param namespaceId namespace id
     * @param artifact import artifact
     * @param overwriteExisting whether overwriting existing resources is allowed
     * @return import result item
     * @throws NacosException if import cannot be completed
     */
    AiResourceImportResultItem importResource(String namespaceId,
        AiResourceImportArtifact artifact,
        boolean overwriteExisting) throws NacosException;
}
