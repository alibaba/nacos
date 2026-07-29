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

package com.alibaba.nacos.plugin.ai.vector;

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.ai.vector.spi.AiResourceVectorIndex;
import com.alibaba.nacos.plugin.ai.vector.spi.AiResourceVectorIndexBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Loads and retains AI resource vector index plugin instances.
 *
 * @author nacos
 */
public class AiResourceVectorIndexRegistry {
    
    private static final Logger LOGGER =
        LoggerFactory.getLogger(AiResourceVectorIndexRegistry.class);
    
    private static final AiResourceVectorIndexRegistry INSTANCE =
        new AiResourceVectorIndexRegistry(NacosServiceLoader.load(
            AiResourceVectorIndexBuilder.class));
    
    private final Map<String, AiResourceVectorIndex> indexes;
    
    AiResourceVectorIndexRegistry(Collection<AiResourceVectorIndexBuilder> builders) {
        this.indexes = Collections.unmodifiableMap(loadIndexes(builders));
    }
    
    public static AiResourceVectorIndexRegistry getInstance() {
        return INSTANCE;
    }
    
    /**
     * Return all installed vector index plugins by provider type.
     *
     * @return installed vector index plugins
     */
    public Map<String, AiResourceVectorIndex> getAllIndexes() {
        return indexes;
    }
    
    private Map<String, AiResourceVectorIndex> loadIndexes(
        Collection<AiResourceVectorIndexBuilder> builders) {
        Map<String, AiResourceVectorIndex> result = new LinkedHashMap<>();
        Set<String> providerTypes = new LinkedHashSet<>();
        if (builders == null) {
            return result;
        }
        for (AiResourceVectorIndexBuilder builder : builders) {
            if (builder == null || StringUtils.isBlank(builder.type())) {
                throw new IllegalStateException(
                    "AI resource vector index provider type must not be empty.");
            }
            String type = builder.type().trim();
            if (!providerTypes.add(type)) {
                throw new IllegalStateException(
                    "Duplicate AI resource vector index provider type: " + type);
            }
            AiResourceVectorIndex index;
            try {
                index = builder.build();
            } catch (RuntimeException e) {
                LOGGER.warn(
                    "Failed to build AI resource vector index provider `{}` and it was ignored",
                    type, e);
                continue;
            }
            if (index == null) {
                LOGGER.warn("AI resource vector index provider `{}` returned null and was ignored",
                    type);
                continue;
            }
            result.put(type, index);
        }
        return result;
    }
}
