/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.remote.grpc.negotiator;

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.common.utils.Pair;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.sys.env.EnvUtil;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abstract base class for ProtocolNegotiatorBuilder singletons. This class provides a common implementation for
 * building ProtocolNegotiator instances based on a given type. Subclasses should provide implementations for loading
 * ProtocolNegotiatorBuilder instances via SPI and defining default builders.
 *
 * @author stone-98
 * @date 2024/2/21
 */
public abstract class AbstractProtocolNegotiatorBuilderSingleton implements ProtocolNegotiatorBuilder {
    
    /**
     * Map to store ProtocolNegotiatorBuilders based on their types.
     */
    protected static final Map<String, ProtocolNegotiatorBuilder> BUILDER_MAP = new ConcurrentHashMap<>();
    
    static {
        try {
            for (ProtocolNegotiatorBuilder each : NacosServiceLoader.load(ProtocolNegotiatorBuilder.class)) {
                BUILDER_MAP.put(each.type(), each);
                Loggers.REMOTE.info("Load ProtocolNegotiatorBuilder {} for type {}", each.getClass().getCanonicalName(),
                        each.type());
            }
        } catch (Exception e) {
            Loggers.REMOTE.warn("Load ProtocolNegotiatorBuilder failed.", e);
        }
    }
    
    /**
     * The property key to retrieve the actual type of ProtocolNegotiatorBuilder.
     */
    protected final String typePropertyKey;
    
    /**
     * The actual type of ProtocolNegotiatorBuilder, retrieved from system properties.
     */
    protected String actualType;
    
    /**
     * Constructs an instance of AbstractProtocolNegotiatorBuilderSingleton with the specified type property key.
     *
     * @param typePropertyKey the property key to retrieve the actual type
     */
    public AbstractProtocolNegotiatorBuilderSingleton(String typePropertyKey) {
        this.typePropertyKey = typePropertyKey;
        this.actualType = EnvUtil.getProperty(typePropertyKey, defaultBuilderPair().getFirst());
    }
    
    /**
     * Builds a ProtocolNegotiator instance based on the actual type.
     *
     * @return a ProtocolNegotiator instance
     */
    @Override
    public NacosGrpcProtocolNegotiator build() {
        ProtocolNegotiatorBuilder actualBuilder = BUILDER_MAP.get(actualType);
        if (null == actualBuilder) {
            Loggers.REMOTE.warn("Not found ProtocolNegotiatorBuilder for type {}, will use default type {}", actualType,
                    defaultBuilderPair().getFirst());
            return defaultBuilderPair().getSecond().build();
        }
        return actualBuilder.build();
    }
    
    /**
     * Declare default ProtocolNegotiatorBuilders in case loading from SPI fails.
     *
     * @return a Pair of String and ProtocolNegotiatorBuilder representing the default builder
     */
    protected abstract Pair<String, ProtocolNegotiatorBuilder> defaultBuilderPair();
}
