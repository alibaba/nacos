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
import com.alibaba.nacos.core.remote.grpc.negotiator.tls.SdkDefaultTlsProtocolNegotiatorBuilder;

/**
 * Manages ProtocolNegotiatorBuilders for the interaction between Nacos and SDK. Provides a singleton instance of
 * ProtocolNegotiatorBuilder configured for this interaction. Defaults to TLS protocol negotiation but can be overridden
 * via system properties.
 *
 *
 * <p>Property key for configuring the ProtocolNegotiator type for Nacos and SDK interaction.
 *
 * @author stone-98
 * @date 2024/2/21
 */
public class SdkProtocolNegotiatorBuilderSingleton extends AbstractProtocolNegotiatorBuilderSingleton {
    
    /**
     * Property key to retrieve the type of ProtocolNegotiatorBuilder.
     */
    public static final String TYPE_PROPERTY_KEY = "nacos.remote.server.rpc.protocol.negotiator.type";
    
    /**
     * Singleton instance of SdkProtocolNegotiatorBuilderSingleton.
     */
    private static final SdkProtocolNegotiatorBuilderSingleton SINGLETON = new SdkProtocolNegotiatorBuilderSingleton();
    
    /**
     * Constructs a new instance of SdkProtocolNegotiatorBuilderSingleton. Sets up the type property key for
     * ProtocolNegotiatorBuilder.
     */
    public SdkProtocolNegotiatorBuilderSingleton() {
        super(TYPE_PROPERTY_KEY);
    }
    
    /**
     * Retrieves the singleton instance of SdkProtocolNegotiatorBuilderSingleton.
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
        return Pair.with(TYPE_PROPERTY_KEY, new SdkDefaultTlsProtocolNegotiatorBuilder());
    }
    
    /**
     * Retrieves the type of ProtocolNegotiatorBuilder configured for the SDK.
     *
     * @return the type of ProtocolNegotiatorBuilder
     */
    @Override
    public String type() {
        return super.actualType;
    }
}
