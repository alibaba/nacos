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

package com.alibaba.nacos.ai.service.search.vector;

import com.alibaba.nacos.ai.config.ConditionalOnAiResourceSearchEnabled;
import com.alibaba.nacos.api.plugin.PluginStateCheckerHolder;
import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.ai.vector.AiResourceVectorDocument;
import com.alibaba.nacos.plugin.ai.vector.AiResourceVectorHit;
import com.alibaba.nacos.plugin.ai.vector.AiResourceVectorIndexRegistry;
import com.alibaba.nacos.plugin.ai.vector.spi.AiResourceVectorIndex;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Routes AI resource vector operations to the configured vector index plugin.
 *
 * @author nacos
 */
@Service
@ConditionalOnAiResourceSearchEnabled
public class AiResourceVectorIndexRouter implements AiResourceVectorIndex, DisposableBean {
    
    public static final String KEY_VECTOR_PROVIDER = "nacos.ai.resource.search.vector.provider";
    
    public static final String DEFAULT_VECTOR_PROVIDER = "postgresql";
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AiResourceVectorIndexRouter.class);
    
    private final Map<String, AiResourceVectorIndex> indexes;
    
    private final String provider;
    
    public AiResourceVectorIndexRouter() {
        this(AiResourceVectorIndexRegistry.getInstance().getAllIndexes(), null);
    }
    
    AiResourceVectorIndexRouter(Map<String, AiResourceVectorIndex> indexes,
        String providerOverride) {
        this.indexes = indexes;
        this.provider = resolveProvider(providerOverride);
        if (indexes.containsKey(provider)) {
            LOGGER.info("Using AI resource vector index provider: {}", provider);
        } else {
            LOGGER.warn(
                "AI resource vector index provider `{}` not found, vector retrieval disabled",
                provider);
        }
    }
    
    @Override
    public boolean available() {
        return delegate().available();
    }
    
    @Override
    public void replaceResourceVersion(String namespaceId, String resourceType,
        String resourceName, String resourceVersion,
        Collection<AiResourceVectorDocument> documents) {
        delegate().replaceResourceVersion(namespaceId, resourceType, resourceName,
            resourceVersion, documents);
    }
    
    @Override
    public void addDocuments(Collection<AiResourceVectorDocument> documents) {
        delegate().addDocuments(documents);
    }
    
    @Override
    public void deleteByResource(String namespaceId, String resourceType, String resourceName) {
        delegate().deleteByResource(namespaceId, resourceType, resourceName);
    }
    
    @Override
    public void deleteByResourceVersion(String namespaceId, String resourceType,
        String resourceName, String resourceVersion) {
        delegate().deleteByResourceVersion(namespaceId, resourceType, resourceName,
            resourceVersion);
    }
    
    @Override
    public boolean isResourceVersionReady(String namespaceId, String resourceType,
        String resourceName, String resourceVersion, String embeddingModel,
        int expectedDocumentCount) {
        return delegate().isResourceVersionReady(namespaceId, resourceType, resourceName,
            resourceVersion, embeddingModel, expectedDocumentCount);
    }
    
    @Override
    public boolean isResourceVersionReady(String namespaceId, String resourceType,
        String resourceName, String resourceVersion, String embeddingModel,
        long expectedDocumentId, int expectedDocumentCount) {
        return delegate().isResourceVersionReady(namespaceId, resourceType, resourceName,
            resourceVersion, embeddingModel, expectedDocumentId, expectedDocumentCount);
    }
    
    @Override
    public List<AiResourceVectorHit> search(String namespaceId, String embeddingModel,
        double[] queryVector, List<String> resourceTypes, int limit) {
        return delegate().search(namespaceId, embeddingModel, queryVector, resourceTypes, limit);
    }
    
    @Override
    public void destroy() throws Exception {
        for (AiResourceVectorIndex index : indexes.values()) {
            index.close();
        }
    }
    
    AiResourceVectorIndex delegate() {
        if (!PluginStateCheckerHolder.isPluginEnabled(PluginType.AI_VECTOR.getType(), provider)) {
            return NoopAiResourceVectorIndex.INSTANCE;
        }
        return indexes.getOrDefault(provider, NoopAiResourceVectorIndex.INSTANCE);
    }
    
    private String resolveProvider(String providerOverride) {
        if (StringUtils.isNotBlank(providerOverride)) {
            return providerOverride;
        }
        String systemValue = System.getProperty(KEY_VECTOR_PROVIDER);
        if (StringUtils.isNotBlank(systemValue)) {
            return systemValue.trim();
        }
        try {
            return EnvUtil.getProperty(KEY_VECTOR_PROVIDER, DEFAULT_VECTOR_PROVIDER).trim();
        } catch (Exception ignored) {
            return DEFAULT_VECTOR_PROVIDER;
        }
    }
    
    private static class NoopAiResourceVectorIndex implements AiResourceVectorIndex {
        
        private static final NoopAiResourceVectorIndex INSTANCE = new NoopAiResourceVectorIndex();
        
        @Override
        public boolean available() {
            return false;
        }
        
        @Override
        public void replaceResourceVersion(String namespaceId, String resourceType,
            String resourceName, String resourceVersion,
            Collection<AiResourceVectorDocument> documents) {
        }
        
        @Override
        public void addDocuments(Collection<AiResourceVectorDocument> documents) {
        }
        
        @Override
        public void deleteByResource(String namespaceId, String resourceType,
            String resourceName) {
        }
        
        @Override
        public void deleteByResourceVersion(String namespaceId, String resourceType,
            String resourceName, String resourceVersion) {
        }
        
        @Override
        public List<AiResourceVectorHit> search(String namespaceId, String embeddingModel,
            double[] queryVector, List<String> resourceTypes, int limit) {
            return Collections.emptyList();
        }
    }
}
