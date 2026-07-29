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

package com.alibaba.nacos.ai.service.visibility;

import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.visibility.model.VisibilityResource;
import com.alibaba.nacos.plugin.visibility.spi.VisibilityResourceLocator;
import org.springframework.stereotype.Service;

import java.util.Optional;

import static com.alibaba.nacos.api.common.Constants.DEFAULT_NAMESPACE_ID;

/**
 * AI implementation of {@link VisibilityResourceLocator}.
 *
 * @author Zhengcy05
 */
@Service
public class AiVisibilityResourceLocator implements VisibilityResourceLocator {
    
    private final AiResourcePersistService aiResourcePersistService;
    
    public AiVisibilityResourceLocator(AiResourcePersistService aiResourcePersistService) {
        this.aiResourcePersistService = aiResourcePersistService;
    }
    
    @Override
    public Optional<VisibilityResource> findResource(String namespaceId, String resourceType,
        String resourceName) {
        String resolvedNamespaceId =
            StringUtils.isBlank(namespaceId) ? DEFAULT_NAMESPACE_ID : namespaceId;
        // The auth plugin resolves resources by the same identity tuple used by AI persistence.
        AiResource resource =
            aiResourcePersistService.find(resolvedNamespaceId, resourceName, resourceType);
        return Optional.ofNullable(resource).map(each -> (VisibilityResource) each);
    }
}
