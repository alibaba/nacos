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
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.model.search.AiResourceSearchDocument;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.ai.storage.NacosConfigAiResourceStorage;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Search type handler for canonical AI resources stored through {@link AiResourceManager}.
 *
 * @author nacos
 */
@Service
@ConditionalOnAiResourceSearchEnabled
public class StoredAiResourceSearchTypeHandler implements AiResourceSearchTypeHandler {
    
    private static final Logger LOGGER =
        LoggerFactory.getLogger(StoredAiResourceSearchTypeHandler.class);
    
    private static final List<String> RESOURCE_TYPES = Collections.unmodifiableList(
        List.of(AiResourceConstants.RESOURCE_TYPE_SKILL,
            NacosConfigAiResourceStorage.RESOURCE_TYPE_PROMPT));
    
    private final AiResourceManager resourceManager;
    
    private final AiResourceIndexContentLoader contentLoader;
    
    private final AiResourceSearchDocumentBuilder documentBuilder =
        new AiResourceSearchDocumentBuilder();
    
    private final AiResourceIndexProjectionBuilder projectionBuilder =
        new AiResourceIndexProjectionBuilder();
    
    public StoredAiResourceSearchTypeHandler(AiResourceManager resourceManager,
        AiResourceIndexContentLoader contentLoader) {
        this.resourceManager = resourceManager;
        this.contentLoader = contentLoader == null ? AiResourceIndexContentLoader.NOOP
            : contentLoader;
    }
    
    @Override
    public Collection<String> resourceTypes() {
        return RESOURCE_TYPES;
    }
    
    @Override
    public AiResourceIndexProjection project(String namespaceId, String resourceType,
        String resourceName, String version) {
        if (!RESOURCE_TYPES.contains(resourceType)) {
            return null;
        }
        AiResource resource = resourceManager.findMeta(namespaceId, resourceName, resourceType);
        return projectResource(namespaceId, resourceType, resource, version);
    }
    
    @Override
    public AiResourceIndexSourcePage scan(String namespaceId, String resourceType, int pageNo,
        int pageSize) {
        if (!RESOURCE_TYPES.contains(resourceType)) {
            return new AiResourceIndexSourcePage(Collections.emptyList(), false);
        }
        Page<AiResource> page = resourceManager.listMetaByType(namespaceId, resourceType, null,
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
                    projectResource(namespaceId, resourceType, resource, null)));
            } catch (Exception e) {
                items.add(AiResourceIndexSource.failed(resourceName, e));
            }
        }
        return new AiResourceIndexSourcePage(items, resources.size() >= pageSize);
    }
    
    @Override
    public boolean isCurrent(AiResourceSearchDocument document) throws NacosException {
        if (document == null || !RESOURCE_TYPES.contains(document.getResourceType())) {
            return false;
        }
        AiResource resource = resourceManager.findMeta(document.getNamespaceId(),
            document.getResourceName(), document.getResourceType());
        if (resource == null
            || !AiResourceConstants.META_STATUS_ENABLE.equalsIgnoreCase(resource.getStatus())) {
            return false;
        }
        try {
            resourceManager.ensureReadableOrNotFound(resource,
                document.getResourceType() + " not found: " + document.getResourceName());
        } catch (NacosException e) {
            return false;
        }
        String latestVersion = AiResourceManager.resolveVersion(resource, null,
            AiResourceConstants.LABEL_LATEST);
        if (!document.getResourceVersion().equals(latestVersion)) {
            return false;
        }
        AiResourceVersion version = resourceManager.findVersion(document.getNamespaceId(),
            document.getResourceName(), document.getResourceType(), document.getResourceVersion());
        return version != null
            && AiResourceConstants.VERSION_STATUS_ONLINE.equalsIgnoreCase(version.getStatus());
    }
    
    @Override
    public boolean exists(String namespaceId, String resourceType, String resourceName) {
        return RESOURCE_TYPES.contains(resourceType)
            && resourceManager.findMeta(namespaceId, resourceName, resourceType) != null;
    }
    
    private AiResourceIndexProjection projectResource(String namespaceId, String resourceType,
        AiResource resource, String version) {
        if (resource == null) {
            return null;
        }
        if (StringUtils.isBlank(resource.getNamespaceId())) {
            resource.setNamespaceId(namespaceId);
        }
        if (StringUtils.isBlank(resource.getType())) {
            resource.setType(resourceType);
        }
        String resolvedVersion = StringUtils.isBlank(version)
            ? AiResourceManager.resolveVersion(resource, null, AiResourceConstants.LABEL_LATEST)
            : version;
        if (StringUtils.isBlank(resolvedVersion)) {
            return null;
        }
        AiResourceVersion resourceVersion = resourceManager.findVersion(namespaceId,
            resource.getName(), resourceType, resolvedVersion);
        if (!isIndexable(resource, resourceVersion)) {
            return null;
        }
        AiResourceSearchDocument document =
            documentBuilder.fromAiResource(resource, resourceVersion);
        List<AiResourceIndexEnhancementContent> contents = loadContents(document, resourceVersion);
        return projectionBuilder.build(document, contents, sourceChunkType(resourceType));
    }
    
    private List<AiResourceIndexEnhancementContent> loadContents(
        AiResourceSearchDocument document, AiResourceVersion resourceVersion) {
        try {
            return contentLoader.load(document, resourceVersion);
        } catch (Exception e) {
            LOGGER.warn("Failed to load AI resource index source content for {}:{}@{}",
                document.getResourceType(), document.getResourceName(),
                document.getResourceVersion(), e);
            return Collections.emptyList();
        }
    }
    
    private String sourceChunkType(String resourceType) {
        return AiResourceConstants.RESOURCE_TYPE_SKILL.equals(resourceType)
            ? AiResourceSearchConstants.CHUNK_TYPE_SKILL_CONTENT
            : AiResourceSearchConstants.CHUNK_TYPE_PROMPT_CONTENT;
    }
    
    private boolean isIndexable(AiResource resource, AiResourceVersion version) {
        return version != null
            && AiResourceConstants.META_STATUS_ENABLE.equalsIgnoreCase(resource.getStatus())
            && AiResourceConstants.VERSION_STATUS_ONLINE.equalsIgnoreCase(version.getStatus());
    }
}
