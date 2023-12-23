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

import com.alibaba.nacos.core.remote.CommunicationType;

import java.util.List;

/**
 * Protocol negotiator builder.
 *
 * @author xiweng.yy
 */
public interface ProtocolNegotiatorBuilder {
    
    /**
     * Build new ProtocolNegotiator.
     *
     * @return ProtocolNegotiator, Nullable.
     */
    NacosGrpcProtocolNegotiator build();
    
    /**
     * Builder type of ProtocolNegotiator.
     *
     * @return type
     */
    String type();
    
    /**
     * Get a list of supported communication types by the ProtocolNegotiator.
     *
     * <p>The communication types represent the different types of communication
     * scenarios that the ProtocolNegotiator can handle.</p>
     *
     * @return List of supported CommunicationType values.
     */
    List<CommunicationType> supportCommunicationTypes();
}
