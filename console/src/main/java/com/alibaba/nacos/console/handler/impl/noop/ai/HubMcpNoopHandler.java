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

package com.alibaba.nacos.console.handler.impl.noop.ai;

import com.alibaba.nacos.api.ai.model.hub.HubMcpImportRequest;
import com.alibaba.nacos.api.ai.model.hub.HubResourceTransferResult;

import com.alibaba.nacos.api.ai.model.hub.HubAdjustShareRequest;
import com.alibaba.nacos.api.ai.model.hub.HubForceUnshareRequest;
import com.alibaba.nacos.api.ai.model.hub.HubResourceShareState;
import com.alibaba.nacos.api.ai.model.hub.HubShareMcpRequest;
import com.alibaba.nacos.api.ai.model.hub.HubShareResult;
import com.alibaba.nacos.api.ai.model.hub.HubUnshareRequest;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.console.handler.ai.HubMcpHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/** Disabled AI mode returns a controlled unsupported result and never accepts mutations. */
@Component
@ConditionalOnMissingBean(value = HubMcpHandler.class, ignored = HubMcpNoopHandler.class)
public class HubMcpNoopHandler implements HubMcpHandler {
    
    @Override
    public HubShareResult share(HubShareMcpRequest request)
        throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED,
            ErrorCode.API_FUNCTION_DISABLED,
            "Nacos AI Hub resource management is unavailable");
    }
    
    @Override
    public HubShareResult adjust(HubAdjustShareRequest request)
        throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED,
            ErrorCode.API_FUNCTION_DISABLED,
            "Nacos AI Hub resource management is unavailable");
    }
    
    @Override
    public HubShareResult unshare(HubUnshareRequest request)
        throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED,
            ErrorCode.API_FUNCTION_DISABLED,
            "Nacos AI Hub resource management is unavailable");
    }
    
    @Override
    public HubShareResult forceUnshare(HubForceUnshareRequest request)
        throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED,
            ErrorCode.API_FUNCTION_DISABLED,
            "Nacos AI Hub resource management is unavailable");
    }
    
    @Override
    public HubResourceShareState sourceState(String namespaceId, String name)
        throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED,
            ErrorCode.API_FUNCTION_DISABLED,
            "Nacos AI Hub resource management is unavailable");
    }
    
    @Override
    public HubResourceShareState governanceState(Long entryId)
        throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED,
            ErrorCode.API_FUNCTION_DISABLED,
            "Nacos AI Hub resource management is unavailable");
    }
    
    /** Import the pinned Hub entry into an independent target draft. */
    @Override
    public HubResourceTransferResult importFromHub(HubMcpImportRequest request)
        throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED,
            ErrorCode.API_FUNCTION_DISABLED, "Nacos AI Hub import is unavailable");
    }
}
