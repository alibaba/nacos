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

import com.alibaba.nacos.common.utils.Pair;
import com.alibaba.nacos.core.remote.grpc.negotiator.tls.ClusterDefaultTlsProtocolNegotiatorBuilder;

/**
 * Manages ProtocolNegotiatorBuilders for cluster communication. Provides a singleton instance of
 * ProtocolNegotiatorBuilder configured for this purpose. Defaults to TLS protocol negotiation but can be overridden via
 * system properties.
 *
 *
 * <p>Property key for configuring the ProtocolNegotiator type for cluster communication.
 *
 * @author stone-98
 * @date 2024/2/21
 */
public class ClusterProtocolNegotiatorBuilderSingleton extends AbstractProtocolNegotiatorBuilderSingleton {
    
    /**
     * Property key for configuring the ProtocolNegotiator type for cluster communication.
     */
    public static final String TYPE_PROPERTY_KEY = "nacos.remote.cluster.server.rpc.protocol.negotiator.type";
    
    /**
     * Singleton instance of ClusterProtocolNegotiatorBuilderSingleton.
     */
    private static final ClusterProtocolNegotiatorBuilderSingleton SINGLETON = new ClusterProtocolNegotiatorBuilderSingleton();
    
    /**
     * Constructs a new instance of ClusterProtocolNegotiatorBuilderSingleton. Sets up the type property key for
     * ProtocolNegotiatorBuilder.
     */
    public ClusterProtocolNegotiatorBuilderSingleton() {
        super(TYPE_PROPERTY_KEY);
    }
    
    /**
     * Retrieves the singleton instance of ClusterProtocolNegotiatorBuilderSingleton.
     *
     * @return the singleton instance
     */
    public static AbstractProtocolNegotiatorBuilderSingleton getSingleton() {
        return SINGLETON;
    }
    
    /**
     * Provides the default ProtocolNegotiatorBuilder pair.
     *
     * @return a Pair containing the default type and builder instance
     */
    @Override
    protected Pair<String, ProtocolNegotiatorBuilder> defaultBuilderPair() {
        return Pair.with(TYPE_PROPERTY_KEY, new ClusterDefaultTlsProtocolNegotiatorBuilder());
    }
    
    /**
     * Retrieves the type of ProtocolNegotiatorBuilder configured for cluster communication.
     *
     * @return the type of ProtocolNegotiatorBuilder
     */
    @Override
    public String type() {
        return super.actualType;
    }
}
