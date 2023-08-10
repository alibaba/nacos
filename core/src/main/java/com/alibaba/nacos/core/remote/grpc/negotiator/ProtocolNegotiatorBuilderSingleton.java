/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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
import com.alibaba.nacos.core.remote.grpc.negotiator.tls.DefaultTlsProtocolNegotiatorBuilder;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.sys.env.EnvUtil;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.alibaba.nacos.core.remote.grpc.negotiator.tls.DefaultTlsProtocolNegotiatorBuilder.TYPE_DEFAULT_TLS;

/**
 * Protocol Negotiator Builder Singleton.
 *
 * @author xiweng.yy
 */
public class ProtocolNegotiatorBuilderSingleton implements ProtocolNegotiatorBuilder {
    
    private static final String TYPE_PROPERTY_KEY = "nacos.remote.server.rpc.protocol.negotiator.type";
    
    private static final ProtocolNegotiatorBuilderSingleton SINGLETON = new ProtocolNegotiatorBuilderSingleton();
    
    private final Map<String, ProtocolNegotiatorBuilder> builderMap;
    
    private String actualType;
    
    private ProtocolNegotiatorBuilderSingleton() {
        actualType = EnvUtil.getProperty(TYPE_PROPERTY_KEY, TYPE_DEFAULT_TLS);
        builderMap = new ConcurrentHashMap<>();
        loadAllBuilders();
    }
    
    private void loadAllBuilders() {
        try {
            for (ProtocolNegotiatorBuilder each : NacosServiceLoader.load(ProtocolNegotiatorBuilder.class)) {
                builderMap.put(each.type(), each);
                Loggers.REMOTE.info("Load ProtocolNegotiatorBuilder {} for type {}", each.getClass().getCanonicalName(),
                        each.type());
            }
        } catch (Exception e) {
            Loggers.REMOTE.warn("Load ProtocolNegotiatorBuilder failed, use default ProtocolNegotiatorBuilder", e);
            builderMap.put(TYPE_DEFAULT_TLS, new DefaultTlsProtocolNegotiatorBuilder());
            actualType = TYPE_DEFAULT_TLS;
        }
    }
    
    public static ProtocolNegotiatorBuilderSingleton getSingleton() {
        return SINGLETON;
    }
    
    @Override
    public NacosGrpcProtocolNegotiator build() {
        ProtocolNegotiatorBuilder actualBuilder = builderMap.get(actualType);
        if (null == actualBuilder) {
            Loggers.REMOTE.warn("Not found ProtocolNegotiatorBuilder for type {}, will use default", actualType);
            return builderMap.get(TYPE_DEFAULT_TLS).build();
        }
        return actualBuilder.build();
    }
    
    @Override
    public String type() {
        return actualType;
    }
}
