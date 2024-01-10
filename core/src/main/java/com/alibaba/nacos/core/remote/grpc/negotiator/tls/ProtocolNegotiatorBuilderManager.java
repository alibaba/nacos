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

package com.alibaba.nacos.core.remote.grpc.negotiator.tls;

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.common.remote.CommunicationType;
import com.alibaba.nacos.core.remote.grpc.negotiator.NacosGrpcProtocolNegotiator;
import com.alibaba.nacos.core.remote.grpc.negotiator.ProtocolNegotiatorBuilder;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.sys.env.EnvUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static com.alibaba.nacos.core.remote.grpc.negotiator.tls.ClusterDefaultTlsProtocolNegotiatorBuilder.CLUSTER_TYPE_DEFAULT_TLS;
import static com.alibaba.nacos.core.remote.grpc.negotiator.tls.SdkDefaultTlsProtocolNegotiatorBuilder.SDK_TYPE_DEFAULT_TLS;

/**
 * Manager for ProtocolNegotiatorBuilder instances, responsible for loading, managing, and providing
 * ProtocolNegotiatorBuilders.
 *
 * <p>{@code ProtocolNegotiatorBuilderManager} is a singleton class, and it initializes ProtocolNegotiatorBuilders
 * using the SPI mechanism. It also provides default ProtocolNegotiatorBuilders in case loading from SPI fails.
 * </p>
 *
 * <p>Usage:
 * <pre>{@code
 * ProtocolNegotiatorBuilderManager manager = ProtocolNegotiatorBuilderManager.getInstance();
 * NacosGrpcProtocolNegotiator negotiator = manager.get(CommunicationType.SDK);
 * }</pre>
 * </p>
 *
 * @author stone-98
 * @date 2023/12/23
 */
public class ProtocolNegotiatorBuilderManager {
    
    /**
     * Property key for configuring the ProtocolNegotiator type for cluster communication.
     */
    public static final String CLUSTER_TYPE_PROPERTY_KEY = "nacos.remote.cluster.server.rpc.protocol.negotiator.type";
    
    /**
     * Property key for configuring the ProtocolNegotiator type for SDK communication.
     */
    public static final String SDK_TYPE_PROPERTY_KEY = "nacos.remote.server.rpc.protocol.negotiator.type";
    
    /**
     * Singleton instance of ProtocolNegotiatorBuilderManager.
     */
    private static final ProtocolNegotiatorBuilderManager INSTANCE = new ProtocolNegotiatorBuilderManager();
    
    /**
     * Map to store ProtocolNegotiatorBuilders by their types.
     */
    private static Map<String, ProtocolNegotiatorBuilder> builderMap;
    
    /**
     * Map to store the actual ProtocolNegotiator types used for different CommunicationTypes.
     */
    private static Map<CommunicationType, String> actualTypeMap;
    
    /**
     * Private constructor to enforce singleton pattern.
     */
    private ProtocolNegotiatorBuilderManager() {
        builderMap = new HashMap<>();
        actualTypeMap = new HashMap<>();
        initActualTypeMap();
        try {
            initBuilders();
        } catch (Exception e) {
            Loggers.REMOTE.warn("Load ProtocolNegotiatorBuilder failed, use default ProtocolNegotiatorBuilder", e);
            initDefaultBuilder();
        }
    }
    
    /**
     * Initialize all ProtocolNegotiatorBuilders using the SPI mechanism.
     */
    private void initBuilders() {
        for (ProtocolNegotiatorBuilder each : NacosServiceLoader.load(ProtocolNegotiatorBuilder.class)) {
            builderMap.put(each.type(), each);
            Loggers.REMOTE.info("Load ProtocolNegotiatorBuilder {} for type {}", each.getClass().getCanonicalName(),
                    each.type());
        }
    }
    
    /**
     * Initialize the mapping of CommunicationType to actual ProtocolNegotiator type from configuration properties.
     */
    private void initActualTypeMap() {
        actualTypeMap.put(CommunicationType.SDK, EnvUtil.getProperty(SDK_TYPE_PROPERTY_KEY, SDK_TYPE_DEFAULT_TLS));
        actualTypeMap.put(CommunicationType.CLUSTER,
                EnvUtil.getProperty(CLUSTER_TYPE_PROPERTY_KEY, CLUSTER_TYPE_DEFAULT_TLS));
    }
    
    /**
     * Initialize default ProtocolNegotiatorBuilders in case loading from SPI fails.
     */
    private void initDefaultBuilder() {
        builderMap.put(SDK_TYPE_DEFAULT_TLS, new SdkDefaultTlsProtocolNegotiatorBuilder());
        builderMap.put(CLUSTER_TYPE_PROPERTY_KEY, new ClusterDefaultTlsProtocolNegotiatorBuilder());
    }
    
    /**
     * Get the singleton instance of ProtocolNegotiatorBuilderManager.
     *
     * @return The singleton instance.
     */
    public static ProtocolNegotiatorBuilderManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * Get the ProtocolNegotiator for the specified CommunicationType.
     *
     * @param communicationType The CommunicationType for which the ProtocolNegotiator is requested.
     * @return The ProtocolNegotiator instance.
     */
    public NacosGrpcProtocolNegotiator buildGrpcProtocolNegotiator(CommunicationType communicationType) {
        String actualType = actualTypeMap.get(communicationType);
        if (StringUtils.isBlank(actualType)) {
            Loggers.REMOTE.warn("Not found actualType for communicationType {}.", communicationType);
            return null;
        }
        ProtocolNegotiatorBuilder builder = builderMap.get(actualType);
        if (Objects.isNull(builder)) {
            Loggers.REMOTE.warn("Not found ProtocolNegotiatorBuilder for actualType {}.", actualType);
            return null;
        }
        return builder.build();
    }
}
