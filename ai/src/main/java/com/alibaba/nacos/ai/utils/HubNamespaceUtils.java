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
 *
 */

package com.alibaba.nacos.ai.utils;

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;

/**
 * Utilities for the internal Nacos AI Resource Hub namespace.
 */
public final class HubNamespaceUtils {
    
    private HubNamespaceUtils() {
    }
    
    /**
     * Checks whether the namespace is the reserved Agentic Resource Hub namespace.
     *
     * @param namespaceId namespace ID
     * @return true if this is the Hub namespace
     */
    public static boolean isHubNamespace(String namespaceId) {
        return AiConstants.Hub.NAMESPACE_ID.equals(namespaceId);
    }
    
    /**
     * Reject access to the internal Hub namespace from ordinary AI resource APIs.
     *
     * @param namespaceId namespace ID
     * @throws NacosApiException when the namespace is the reserved Hub namespace
     */
    public static void rejectIfHubNamespace(String namespaceId) throws NacosApiException {
        if (!isHubNamespace(namespaceId)) {
            return;
        }
        
        throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.ILLEGAL_NAMESPACE,
                "Namespace [" + namespaceId + "] is reserved for Nacos AI Resource Hub "
                        + "and cannot be accessed through ordinary AI resource APIs");
    }
}
