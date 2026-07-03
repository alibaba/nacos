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

package com.alibaba.nacos.ai.service.ard.vector;

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.ai.ard.vector.AiResourceVectorDocument;
import com.alibaba.nacos.plugin.ai.ard.vector.AiResourceVectorHit;
import com.alibaba.nacos.plugin.ai.ard.vector.spi.AiResourceVectorIndex;
import com.alibaba.nacos.plugin.ai.ard.vector.spi.AiResourceVectorIndexBuilder;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Routes ARD vector operations to the configured vector index plugin.
 *
 * @author nacos
 */
@Service
public class ArdVectorIndexRouter implements AiResourceVectorIndex, DisposableBean {

    public static final String KEY_VECTOR_PROVIDER = "nacos.ai.ard.vector.provider";

    public static final String DEFAULT_VECTOR_PROVIDER = "postgresql";

    private static final Logger LOGGER = LoggerFactory.getLogger(ArdVectorIndexRouter.class);

    private final Map<String, AiResourceVectorIndexBuilder> builders;

    private final String providerOverride;

    private volatile AiResourceVectorIndex delegate;

    public ArdVectorIndexRouter() {
        this(NacosServiceLoader.load(AiResourceVectorIndexBuilder.class), null);
    }

    ArdVectorIndexRouter(Collection<AiResourceVectorIndexBuilder> builders,
        String providerOverride) {
        this.builders = Collections.unmodifiableMap(loadBuilders(builders));
        this.providerOverride = providerOverride;
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
    public List<AiResourceVectorHit> search(String namespaceId, String embeddingModel,
        double[] queryVector, List<String> resourceTypes, int limit) {
        return delegate().search(namespaceId, embeddingModel, queryVector, resourceTypes, limit);
    }

    @Override
    public void destroy() throws Exception {
        AiResourceVectorIndex selected = delegate;
        if (selected != null) {
            selected.close();
        }
    }

    AiResourceVectorIndex delegate() {
        AiResourceVectorIndex selected = delegate;
        if (selected != null) {
            return selected;
        }
        synchronized (this) {
            if (delegate == null) {
                delegate = buildDelegate();
            }
            return delegate;
        }
    }

    /**
     * Return the configured vector index plugin for plugin manager listing.
     *
     * @return selected vector index plugin, empty when the provider is not installed
     */
    public Map<String, AiResourceVectorIndex> selectedIndex() {
        String provider = resolveProvider();
        if (!builders.containsKey(provider)) {
            return Collections.emptyMap();
        }
        return Collections.singletonMap(provider, delegate());
    }

    private AiResourceVectorIndex buildDelegate() {
        String provider = resolveProvider();
        AiResourceVectorIndexBuilder builder = builders.get(provider);
        if (builder == null) {
            LOGGER.warn("ARD vector index provider `{}` not found, vector retrieval disabled",
                provider);
            return NoopAiResourceVectorIndex.INSTANCE;
        }
        AiResourceVectorIndex index = builder.build();
        if (index == null) {
            LOGGER.warn("ARD vector index provider `{}` returned null, vector retrieval disabled",
                provider);
            return NoopAiResourceVectorIndex.INSTANCE;
        }
        LOGGER.info("Using ARD vector index provider: {}", provider);
        return index;
    }

    private String resolveProvider() {
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

    private Map<String, AiResourceVectorIndexBuilder> loadBuilders(
        Collection<AiResourceVectorIndexBuilder> builders) {
        Map<String, AiResourceVectorIndexBuilder> result = new LinkedHashMap<>();
        if (builders == null) {
            return result;
        }
        for (AiResourceVectorIndexBuilder builder : builders) {
            if (builder == null || StringUtils.isBlank(builder.type())) {
                throw new IllegalStateException("ARD vector index provider type must not be empty.");
            }
            String type = builder.type().trim();
            if (result.containsKey(type)) {
                throw new IllegalStateException("Duplicate ARD vector index provider type: " + type);
            }
            result.put(type, builder);
        }
        return result;
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
