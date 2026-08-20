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

package com.alibaba.nacos.ai.service.search;

import com.alibaba.nacos.ai.config.ConditionalOnAiResourceSearchEnabled;
import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.model.search.AiResourceSearchDocument;
import com.alibaba.nacos.ai.service.agentspecs.AgentSpecStorageReader;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.common.utils.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Search type handler for canonical AgentSpec packages and their latest online Version.
 *
 * @author Nacos
 */
@Service
@ConditionalOnAiResourceSearchEnabled
public class AgentSpecAiResourceSearchTypeHandler implements AiResourceSearchTypeHandler {
    
    private static final String RESOURCE_TYPE = Constants.AgentSpecs.RESOURCE_TYPE_AGENTSPEC;
    
    private final AiResourceManager resourceManager;
    
    private final AgentSpecStorageReader storageReader;
    
    private final AgentSpecSearchIndexProjector projector;
    
    @Autowired
    public AgentSpecAiResourceSearchTypeHandler(AiResourceManager resourceManager,
        AgentSpecStorageReader storageReader) {
        this(resourceManager, storageReader, new AgentSpecSearchIndexProjector());
    }
    
    AgentSpecAiResourceSearchTypeHandler(AiResourceManager resourceManager,
        AgentSpecStorageReader storageReader, AgentSpecSearchIndexProjector projector) {
        this.resourceManager = resourceManager;
        this.storageReader = storageReader;
        this.projector = projector;
    }
    
    @Override
    public int projectionVersion() {
        return AgentSpecSearchIndexProjector.PROJECTION_VERSION;
    }
    
    @Override
    public Collection<String> resourceTypes() {
        return Collections.singletonList(RESOURCE_TYPE);
    }
    
    @Override
    public AiResourceIndexProjection project(String namespaceId, String resourceType,
        String resourceName, String version) throws NacosException {
        if (!RESOURCE_TYPE.equals(resourceType)) {
            return null;
        }
        AiResource meta = resourceManager.findMeta(namespaceId, resourceName, RESOURCE_TYPE);
        return projectAgentSpec(namespaceId, resourceName, meta, version);
    }
    
    @Override
    public AiResourceIndexSourcePage scan(String namespaceId, String resourceType, int pageNo,
        int pageSize) {
        if (!RESOURCE_TYPE.equals(resourceType)) {
            return new AiResourceIndexSourcePage(Collections.emptyList(), false);
        }
        Page<AiResource> page = resourceManager.listMetaByType(namespaceId, RESOURCE_TYPE, null,
            null, pageNo, pageSize);
        List<AiResource> resources = page == null ? null : page.getPageItems();
        if (resources == null || resources.isEmpty()) {
            return new AiResourceIndexSourcePage(Collections.emptyList(), false);
        }
        List<AiResourceIndexSource> items = new ArrayList<>();
        for (AiResource resource : resources) {
            String resourceName = resource == null ? null : resource.getName();
            try {
                items.add(AiResourceIndexSource.success(resourceName,
                    projectAgentSpec(namespaceId, resourceName, resource, null)));
            } catch (Exception e) {
                items.add(AiResourceIndexSource.failed(resourceName, e));
            }
        }
        return new AiResourceIndexSourcePage(items, resources.size() >= pageSize);
    }
    
    @Override
    public boolean isCurrent(AiResourceSearchDocument document) throws NacosException {
        if (document == null || !RESOURCE_TYPE.equals(document.getResourceType())) {
            return false;
        }
        AiResource meta = resourceManager.findMeta(document.getNamespaceId(),
            document.getResourceName(), RESOURCE_TYPE);
        if (!isEnabled(meta)) {
            return false;
        }
        try {
            resourceManager.ensureReadableOrNotFound(meta,
                "AgentSpec not found: " + document.getResourceName());
            AiResourceIndexProjection expected = projectAgentSpec(document.getNamespaceId(),
                document.getResourceName(), meta, document.getResourceVersion());
            return expected != null && matchesProjection(document, expected.getDocument());
        } catch (NacosException e) {
            return false;
        }
    }
    
    @Override
    public boolean exists(String namespaceId, String resourceType, String resourceName) {
        return RESOURCE_TYPE.equals(resourceType)
            && resourceManager.findMeta(namespaceId, resourceName, RESOURCE_TYPE) != null;
    }
    
    private AiResourceIndexProjection projectAgentSpec(String namespaceId, String resourceName,
        AiResource meta, String requestedVersion) throws NacosException {
        if (!isEnabled(meta) || StringUtils.isBlank(resourceName)) {
            return null;
        }
        String latestVersion = AiResourceManager.resolveVersion(meta, null,
            AiResourceConstants.LABEL_LATEST);
        if (StringUtils.isBlank(latestVersion) || StringUtils.isNotBlank(requestedVersion)
            && !latestVersion.equals(requestedVersion)) {
            return null;
        }
        AiResourceVersion version = resourceManager.findVersion(namespaceId, resourceName,
            RESOURCE_TYPE, latestVersion);
        if (version == null || !AiResourceConstants.VERSION_STATUS_ONLINE.equalsIgnoreCase(
            version.getStatus())) {
            return null;
        }
        AgentSpec agentSpec = storageReader.readMeta(namespaceId, resourceName, latestVersion,
            version.getStorage());
        return projector.project(meta, version, agentSpec);
    }
    
    private boolean isEnabled(AiResource resource) {
        return resource != null && AiResourceConstants.META_STATUS_ENABLE.equalsIgnoreCase(
            resource.getStatus());
    }
    
    private boolean matchesProjection(AiResourceSearchDocument actual,
        AiResourceSearchDocument expected) {
        return Objects.equals(actual.getNamespaceId(), expected.getNamespaceId())
            && Objects.equals(actual.getResourceType(), expected.getResourceType())
            && Objects.equals(actual.getResourceName(), expected.getResourceName())
            && Objects.equals(actual.getResourceVersion(), expected.getResourceVersion())
            && Objects.equals(actual.getDisplayName(), expected.getDisplayName())
            && Objects.equals(actual.getDescription(), expected.getDescription())
            && Objects.equals(actual.getTags(), expected.getTags())
            && Objects.equals(actual.getCapabilities(), expected.getCapabilities())
            && Objects.equals(actual.getRepresentativeQueries(),
                expected.getRepresentativeQueries())
            && Objects.equals(actual.getMetadata(), expected.getMetadata())
            && Objects.equals(actual.getSourceDigest(), expected.getSourceDigest())
            && Objects.equals(actual.getStatus(), expected.getStatus())
            && Objects.equals(actual.getGenerateMode(), expected.getGenerateMode());
    }
}
