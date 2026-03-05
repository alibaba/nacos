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

package com.alibaba.nacos.ai.service.prompt;

import com.alibaba.nacos.ai.utils.PromptDataIdUtils;
import com.alibaba.nacos.api.ai.model.prompt.PromptLabelVersionMapping;
import com.alibaba.nacos.api.ai.model.prompt.PromptMetaInfo;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionInfo;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.service.ConfigCacheService;
import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

import static com.alibaba.nacos.ai.constant.Constants.Prompt.PROMPT_GROUP;

/**
 * Prompt client operation service implementation.
 *
 * @author nacos
 */
@Service
public class PromptClientOperationServiceImpl implements PromptClientOperationService {
    
    private static final int DEFAULT_META_CACHE_MAX_SIZE = 10000;
    
    private static final long DEFAULT_META_CACHE_EXPIRE_SECONDS = 60;
    
    private final ConfigQueryChainService configQueryChainService;
    
    private final ConcurrentHashMap<String, MetaCacheEntry> metaCache = new ConcurrentHashMap<>();
    
    private final int metaCacheMaxSize;
    
    private final long metaCacheExpireMs;
    
    public PromptClientOperationServiceImpl(ConfigQueryChainService configQueryChainService) {
        this.configQueryChainService = configQueryChainService;
        this.metaCacheMaxSize = Integer.parseInt(
                System.getProperty("nacos.prompt.meta.cache.maxSize", String.valueOf(DEFAULT_META_CACHE_MAX_SIZE)));
        this.metaCacheExpireMs = 1000L * Long.parseLong(
                System.getProperty("nacos.prompt.meta.cache.expireSeconds", String.valueOf(DEFAULT_META_CACHE_EXPIRE_SECONDS)));
    }
    
    @Override
    public PromptVersionInfo queryPrompt(String namespaceId, String promptKey, String version, String label, String md5)
            throws NacosException {
        if (StringUtils.isBlank(promptKey)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "Required parameter `promptKey` not present");
        }
        PromptLabelVersionMapping mapping = getPromptLabelVersionMapping(namespaceId, promptKey);
        if (mapping == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    String.format("prompt `%s` not found", promptKey));
        }
        String targetVersion = PromptMetaUtils.resolveTargetVersion(mapping, version, label);
        String versionDataId = PromptDataIdUtils.buildVersionDataId(promptKey, targetVersion);
        if (StringUtils.isNotBlank(md5)) {
            String groupKey = GroupKey2.getKey(versionDataId, PROMPT_GROUP, namespaceId);
            if (ConfigCacheService.isUptodate(groupKey, md5)) {
                throw new NacosException(NacosException.NOT_MODIFIED, "prompt data is up to date");
            }
        }
        ConfigQueryChainResponse response = queryConfig(namespaceId, versionDataId);
        if (!isFound(response)) {
            invalidateMetaCache(namespaceId, promptKey);
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    String.format("prompt `%s` version `%s` not found", promptKey, targetVersion));
        }
        PromptVersionInfo versionInfo = JacksonUtils.toObj(response.getContent(), PromptVersionInfo.class);
        versionInfo.setPromptKey(promptKey);
        versionInfo.setMd5(response.getMd5());
        return versionInfo;
    }
    
    @Override
    public void invalidateMetaCache(String namespaceId, String promptKey) {
        if (StringUtils.isBlank(promptKey)) {
            return;
        }
        metaCache.remove(buildMetaCacheKey(namespaceId, promptKey));
    }
    
    @Override
    public PromptMetaInfo getPromptMeta(String namespaceId, String promptKey) throws NacosException {
        PromptLabelVersionMapping mapping = getPromptLabelVersionMapping(namespaceId, promptKey);
        if (mapping == null) {
            return null;
        }
        return PromptMetaUtils.composeMetaInfo(promptKey, mapping, null);
    }
    
    private PromptLabelVersionMapping getPromptLabelVersionMapping(String namespaceId, String promptKey) {
        if (StringUtils.isBlank(promptKey)) {
            return null;
        }
        String cacheKey = buildMetaCacheKey(namespaceId, promptKey);
        MetaCacheEntry cacheEntry = metaCache.get(cacheKey);
        long now = System.currentTimeMillis();
        if (cacheEntry != null && cacheEntry.expireAtMs > now) {
            return PromptMetaUtils.cloneLabelVersionMapping(cacheEntry.snapshot.getMapping());
        }
        PromptLabelVersionMappingSnapshot loaded = loadLabelVersionMappingSnapshot(namespaceId, promptKey);
        if (loaded.getMapping() != null) {
            putMetaCache(cacheKey, loaded, now + metaCacheExpireMs);
            return PromptMetaUtils.cloneLabelVersionMapping(loaded.getMapping());
        }
        return null;
    }
    
    private PromptLabelVersionMappingSnapshot loadLabelVersionMappingSnapshot(String namespaceId, String promptKey) {
        String dataId = PromptDataIdUtils.buildLabelVersionMappingDataId(promptKey);
        ConfigQueryChainResponse response = queryConfig(namespaceId, dataId);
        if (!isFound(response)) {
            return PromptLabelVersionMappingSnapshot.empty();
        }
        PromptLabelVersionMapping mapping = PromptMetaUtils.normalizeLabelVersionMapping(
                JacksonUtils.toObj(response.getContent(), PromptLabelVersionMapping.class));
        return new PromptLabelVersionMappingSnapshot(mapping, response.getMd5());
    }
    
    private ConfigQueryChainResponse queryConfig(String namespaceId, String dataId) {
        ConfigQueryChainRequest request = ConfigQueryChainRequest.buildConfigQueryChainRequest(dataId, PROMPT_GROUP, namespaceId);
        return configQueryChainService.handle(request);
    }
    
    private boolean isFound(ConfigQueryChainResponse response) {
        return response.getStatus() == ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL
                || response.getStatus() == ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_GRAY;
    }
    
    private String buildMetaCacheKey(String namespaceId, String promptKey) {
        return namespaceId + "::" + promptKey;
    }
    
    private void putMetaCache(String cacheKey, PromptLabelVersionMappingSnapshot snapshot, long expireAtMs) {
        if (metaCache.size() >= metaCacheMaxSize) {
            String removingKey = metaCache.keySet().stream().findFirst().orElse(null);
            if (removingKey != null) {
                metaCache.remove(removingKey);
            }
        }
        metaCache.put(cacheKey, new MetaCacheEntry(snapshot, expireAtMs));
    }
    
    private static class MetaCacheEntry {
        
        private final PromptLabelVersionMappingSnapshot snapshot;
        
        private final long expireAtMs;
        
        MetaCacheEntry(PromptLabelVersionMappingSnapshot snapshot, long expireAtMs) {
            this.snapshot = snapshot;
            this.expireAtMs = expireAtMs;
        }
    }
}
