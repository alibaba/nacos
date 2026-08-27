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

package com.alibaba.nacos.ai.service.agent.watch;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.service.agent.AgentDiscoveryApplicationService;
import com.alibaba.nacos.ai.service.agent.identity.RadServiceNameComposer;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryCallInterface;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.utils.AgentDiscoveryCanonicalizer;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * Default current-fact Agent projection implementation.
 *
 * @author Nacos
 */
@Component
public class DefaultAgentProjectionProjector implements AgentProjectionProjector {
    
    private final AgentDiscoveryApplicationService discoveryService;
    
    private final LongSupplier clock;
    
    @Autowired
    public DefaultAgentProjectionProjector(AgentDiscoveryApplicationService discoveryService) {
        this(discoveryService, System::currentTimeMillis);
    }
    
    DefaultAgentProjectionProjector(AgentDiscoveryApplicationService discoveryService,
        LongSupplier clock) {
        this.discoveryService = discoveryService;
        this.clock = clock;
    }
    
    @Override
    public AgentProjectionState project(AgentProjectionKey key) {
        long computedAt = clock.getAsLong();
        try {
            AgentDiscoveryResult current = AgentDiscoveryCanonicalizer.canonicalizeResult(
                discoveryService.projectCurrentFact(key.getRequest()));
            return AgentProjectionState.available(
                AgentDiscoveryCanonicalizer.fingerprint(current), dependencies(current),
                computedAt);
        } catch (NacosException e) {
            return failure(e.getErrCode(), e.getMessage(), computedAt);
        } catch (NacosRuntimeException e) {
            return failure(e.getErrCode(), e.getMessage(), computedAt);
        } catch (RuntimeException e) {
            return AgentProjectionState.failure(AgentProjectionStatus.TRANSIENT_FAILURE,
                NacosException.SERVER_ERROR, e.getMessage(), computedAt);
        }
    }
    
    private Set<Service> dependencies(AgentDiscoveryResult result) {
        Set<Service> dependencies = new LinkedHashSet<Service>();
        for (AgentDiscoveryCallInterface callInterface : result.getCallInterfaces()) {
            // A declared protocol is also a prospective Runtime dependency. Retaining it before
            // the first Endpoint exists lets a Watch observe creation of that Naming service.
            dependencies.add(Service.newService(result.getNamespaceId(),
                Constants.Agent.AGENT_ENDPOINT_GROUP,
                RadServiceNameComposer.compose(result.getAgentName(),
                    callInterface.getProtocol())));
        }
        return dependencies;
    }
    
    private AgentProjectionState failure(int errorCode, String message, long computedAt) {
        AgentProjectionStatus status;
        switch (errorCode) {
            case NacosException.NOT_FOUND:
            case NacosException.RESOURCE_NOT_FOUND:
                status = AgentProjectionStatus.NOT_FOUND;
                break;
            case NacosException.NO_RIGHT:
                status = AgentProjectionStatus.ACCESS_UNCERTAIN;
                break;
            case NacosException.CONFLICT:
                status = AgentProjectionStatus.CONFLICT;
                break;
            default:
                status = AgentProjectionStatus.TRANSIENT_FAILURE;
                break;
        }
        return AgentProjectionState.failure(status, errorCode, message, computedAt);
    }
}
