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

package com.alibaba.nacos.client.ai.remote;

import com.alibaba.nacos.api.ability.constant.AbilityKey;
import com.alibaba.nacos.api.ability.constant.AbilityStatus;
import com.alibaba.nacos.api.ai.AgentTransportMode;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.ai.remote.capability.A2aModeSelector;
import com.alibaba.nacos.common.remote.client.ConnectionEventListener;
import com.alibaba.nacos.common.remote.client.Connection;

/** Instance A2A selection, separate from refreshed transport capability evidence. */
public final class AgentCapabilityResolver {
    
    private final AgentTransportMode mode;
    private final AgentGrpcTransport grpc;
    private final AiGrpcClient client;
    private final AiHttpClientProxy http;
    private final A2aModeSelector selector = new A2aModeSelector();
    
    public AgentCapabilityResolver(AgentTransportMode mode, AgentGrpcTransport grpc,
        AiGrpcClient client, AiHttpClientProxy http) {
        this.mode = mode;
        this.grpc = grpc;
        this.client = client;
        this.http = http;
        client.registerConnectionListener(new ConnectionEventListener() {
            
            @Override
            public void onConnected(Connection connection) {
                http.invalidateCapabilities();
            }
            
            @Override
            public void onDisConnect(Connection connection) {
                http.invalidateCapabilities();
            }
        });
    }
    
    /** Capture positive RAD negotiation without an HTTP request during initialization. */
    public void initialize() {
        if (mode != AgentTransportMode.HTTP && client.isEnable()
            && ability(AbilityKey.SERVER_RAD_V1) == AbilityStatus.SUPPORTED) {
            selector.select(AbilityStatus.SUPPORTED, AbilityStatus.UNKNOWN);
        }
        // A legacy choice waits for the first A2A operation to check independent HTTP
        // evidence. Other AI resources do not acquire a new HTTP capability prerequisite.
    }
    
    /**
     * Choose the legacy method binding once; failures never fix an undecided mode.
     * @return whether all legacy methods must use RAD
     * @throws NacosException for unavailable or inconclusive capability evidence
     */
    public boolean useRad() throws NacosException {
        A2aModeSelector.Mode selected = current();
        if (selected != A2aModeSelector.Mode.UNDECIDED) {
            return selected == A2aModeSelector.Mode.RAD;
        }
        AbilityStatus rad = probe();
        if (ability(AbilityKey.SERVER_RAD_V1) == AbilityStatus.SUPPORTED) {
            // Positive protocol evidence chooses the API family, not HTTP reachability.
            rad = AbilityStatus.SUPPORTED;
        }
        selected = selector.select(rad, ability(AbilityKey.SERVER_AGENT_REGISTRY));
        if (selected == A2aModeSelector.Mode.UNDECIDED) {
            throw new NacosException(NacosException.SERVER_ERROR,
                "Cannot determine RAD capability: HTTP capability evidence and legacy gRPC "
                    + "negotiation are unavailable. Check the server address and exposed ports.");
        }
        return selected == A2aModeSelector.Mode.RAD;
    }
    
    /** Return the fixed mode for local cleanup, without probing any server. */
    public A2aModeSelector.Mode current() {
        return selector.select(AbilityStatus.UNKNOWN, AbilityStatus.UNKNOWN);
    }
    
    /**
     * Check fresh evidence for a native operation; cleanup does not call this method.
     * @param operation public operation name for diagnostics
     * @throws NacosException when RAD is unsupported or the selected transport is unavailable
     */
    public void requireRad(String operation) throws NacosException {
        AbilityStatus status;
        if (mode == AgentTransportMode.GRPC) {
            if (!client.isEnable()) {
                throw new NacosException(NacosException.CLIENT_DISCONNECT,
                    operation + ": the selected server's gRPC endpoint is unreachable.");
            }
            status = ability(AbilityKey.SERVER_RAD_V1);
            if (status == AbilityStatus.UNKNOWN
                && ability(AbilityKey.SERVER_AGENT_REGISTRY) == AbilityStatus.SUPPORTED) {
                status = AbilityStatus.NOT_SUPPORTED;
            }
        } else {
            status = probe();
            if (status == AbilityStatus.UNKNOWN
                && ability(AbilityKey.SERVER_AGENT_REGISTRY) == AbilityStatus.SUPPORTED
                && ability(AbilityKey.SERVER_RAD_V1) != AbilityStatus.SUPPORTED
                && http.isOnlyServer(client.getCurrentServerAddress())) {
                // Only a single matching configured target can use legacy negotiation as
                // its native-operation guard. Never cache this as HTTP/cluster false.
                status = AbilityStatus.NOT_SUPPORTED;
            }
        }
        if (status == AbilityStatus.NOT_SUPPORTED) {
            throw new NacosException(NacosException.SERVER_NOT_IMPLEMENTED,
                operation + " requires RAD. The selected server does not support RAD; "
                    + "upgrade the server before using this API.");
        }
    }
    
    private AbilityStatus probe() throws NacosException {
        if (mode != AgentTransportMode.HTTP && client.isEnable()
            && ability(AbilityKey.SERVER_RAD_V1) == AbilityStatus.SUPPORTED) {
            return AbilityStatus.SUPPORTED;
        }
        // HTTP is independent: an old or unavailable gRPC binding is not a negative HTTP bit.
        AbilityStatus httpRad = http.getCapabilities().get(AiConstants.Capability.RAD_V1);
        if (httpRad != AbilityStatus.UNKNOWN) {
            return httpRad;
        }
        if (!client.isEnable()) {
            grpc.acquireProtocolNeutralClient();
        }
        // A successful old binding can select legacy A2A, but cannot turn a missing HTTP
        // capability into a negative native RAD result for another binding.
        if (mode == AgentTransportMode.GRPC) {
            AbilityStatus rad = ability(AbilityKey.SERVER_RAD_V1);
            if (rad == AbilityStatus.UNKNOWN
                && ability(AbilityKey.SERVER_AGENT_REGISTRY) == AbilityStatus.SUPPORTED) {
                return AbilityStatus.NOT_SUPPORTED;
            }
            return rad;
        }
        return httpRad;
    }
    
    private AbilityStatus ability(AbilityKey key) {
        if (!client.isEnable()) {
            return AbilityStatus.UNKNOWN;
        }
        AbilityStatus status = client.getServerAbility(key);
        return status == null ? AbilityStatus.UNKNOWN : status;
    }
}
