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
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.ai.remote.capability.A2aModeSelector;
import com.alibaba.nacos.client.ai.remote.capability.AiCapabilitySnapshot;
import com.alibaba.nacos.common.remote.client.ConnectionEventListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;

class AgentCapabilityResolverTest {
    
    private AiGrpcClient client;
    private AgentGrpcTransport grpc;
    private AiHttpClientProxy http;
    
    @BeforeEach
    void setUp() throws Exception {
        client = mock(AiGrpcClient.class);
        grpc = mock(AgentGrpcTransport.class);
        http = mock(AiHttpClientProxy.class);
        when(http.getCapabilities()).thenReturn(AiCapabilitySnapshot.unknown());
        when(client.getServerAbility(AbilityKey.SERVER_RAD_V1)).thenReturn(AbilityStatus.UNKNOWN);
        when(client.getServerAbility(AbilityKey.SERVER_AGENT_REGISTRY))
            .thenReturn(AbilityStatus.UNKNOWN);
    }
    
    @ParameterizedTest
    @EnumSource(AgentTransportMode.class)
    void httpRadAllowsA2aWithoutStartingGrpc(AgentTransportMode mode) throws Exception {
        when(http.getCapabilities()).thenReturn(snapshot(true));
        AgentCapabilityResolver resolver = resolver(mode);
        assertTrue(resolver.useRad());
        verifyNoInteractions(grpc);
        if (mode == AgentTransportMode.GRPC) {
            assertEquals(NacosException.CLIENT_DISCONNECT, assertThrows(NacosException.class,
                () -> resolver.requireRad("discoverAgent")).getErrCode());
        } else {
            resolver.requireRad("discoverAgent");
        }
    }
    
    @ParameterizedTest
    @EnumSource(value = AgentTransportMode.class, names = {"GRPC", "AUTO"})
    void oldBindingRemainsLegacyAcrossReconnect(AgentTransportMode mode) throws Exception {
        connected(AbilityStatus.UNKNOWN, AbilityStatus.SUPPORTED);
        AgentCapabilityResolver resolver = resolver(mode);
        resolver.initialize();
        assertFalse(resolver.useRad());
        connected(AbilityStatus.SUPPORTED, AbilityStatus.SUPPORTED);
        ArgumentCaptor<ConnectionEventListener> listener =
            ArgumentCaptor.forClass(ConnectionEventListener.class);
        verify(client).registerConnectionListener(listener.capture());
        listener.getValue().onDisConnect(null);
        listener.getValue().onConnected(null);
        verify(http, times(2)).invalidateCapabilities();
        assertFalse(resolver.useRad());
        resolver.requireRad("publishAgent");
        assertTrue(resolver(mode).useRad());
        verify(http, times(1)).getCapabilities();
    }
    
    @ParameterizedTest
    @EnumSource(AgentTransportMode.class)
    void positiveHttpRadWinsOverOldGrpcForUndecidedInstance(AgentTransportMode mode)
        throws Exception {
        connected(AbilityStatus.UNKNOWN, AbilityStatus.SUPPORTED);
        when(http.getCapabilities()).thenReturn(snapshot(true));
        assertTrue(resolver(mode).useRad());
    }
    
    @Test
    void explicitHttpDoesNotLatchOldGrpcDuringInitialization() throws Exception {
        connected(AbilityStatus.UNKNOWN, AbilityStatus.SUPPORTED);
        AgentCapabilityResolver resolver = resolver(AgentTransportMode.HTTP);
        resolver.initialize();
        doReturn(snapshot(true)).when(http).getCapabilities();
        assertTrue(resolver.useRad());
    }
    
    @ParameterizedTest
    @EnumSource(AgentTransportMode.class)
    void httpFalseFixesLegacyButDoesNotAuthorizeLegacyConnection(AgentTransportMode mode)
        throws Exception {
        when(http.getCapabilities()).thenReturn(snapshot(false));
        AgentCapabilityResolver resolver = resolver(mode);
        assertFalse(resolver.useRad());
        verifyNoInteractions(grpc);
        int expected = mode == AgentTransportMode.GRPC ? NacosException.CLIENT_DISCONNECT
            : NacosException.SERVER_NOT_IMPLEMENTED;
        assertEquals(expected, assertThrows(NacosException.class,
            () -> resolver.requireRad("publishAgent")).getErrCode());
    }
    
    @Test
    void missingHttpAndSuccessfulLegacyNegotiationSelectsLegacy() throws Exception {
        doAnswer(call -> {
            connected(AbilityStatus.UNKNOWN, AbilityStatus.SUPPORTED);
            return client;
        }).when(grpc).acquireProtocolNeutralClient();
        AgentCapabilityResolver resolver = resolver(AgentTransportMode.HTTP);
        assertFalse(resolver.useRad());
        verify(grpc).acquireProtocolNeutralClient();
    }
    
    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {401, 403, 500, 50105})
    void failedProbeDoesNotFixModeOrFallback(int code) throws Exception {
        NacosException error = new NacosException(code, "capability request failed");
        when(http.getCapabilities()).thenThrow(error);
        AgentCapabilityResolver resolver = resolver(AgentTransportMode.HTTP);
        assertSame(error, assertThrows(NacosException.class, resolver::useRad));
        assertEquals(A2aModeSelector.Mode.UNDECIDED, resolver.current());
        verifyNoInteractions(grpc);
        doReturn(snapshot(true)).when(http).getCapabilities();
        assertTrue(resolver.useRad());
    }
    
    @Test
    void unknownIsNotUnsupportedAndCleanupDoesNotProbe() throws Exception {
        AgentCapabilityResolver resolver = resolver(AgentTransportMode.HTTP);
        assertEquals(A2aModeSelector.Mode.UNDECIDED, resolver.current());
        verify(http, never()).getCapabilities();
        assertEquals(NacosException.SERVER_ERROR,
            assertThrows(NacosException.class, resolver::useRad).getErrCode());
        resolver.requireRad("discoverAgent");
        assertEquals(A2aModeSelector.Mode.UNDECIDED, resolver.current());
    }
    
    @Test
    void disconnectedGrpcDoesNotInheritStalePositiveEvidence() {
        when(client.getServerAbility(AbilityKey.SERVER_RAD_V1)).thenReturn(AbilityStatus.SUPPORTED);
        AgentCapabilityResolver resolver = resolver(AgentTransportMode.GRPC);
        resolver.initialize();
        assertEquals(A2aModeSelector.Mode.UNDECIDED, resolver.current());
        assertEquals(NacosException.CLIENT_DISCONNECT, assertThrows(NacosException.class,
            () -> resolver.requireRad("searchAgents")).getErrCode());
    }
    
    @ParameterizedTest
    @EnumSource(value = AgentTransportMode.class, names = {"HTTP", "AUTO"})
    void oldNegotiationRejectsNativeOnlyForMatchingSoleTarget(AgentTransportMode mode)
        throws Exception {
        connected(AbilityStatus.UNKNOWN, AbilityStatus.SUPPORTED);
        when(client.getCurrentServerAddress()).thenReturn("127.0.0.1:8848");
        when(http.isOnlyServer("127.0.0.1:8848")).thenReturn(true);
        AgentCapabilityResolver resolver = resolver(mode);
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, assertThrows(NacosException.class,
            () -> resolver.requireRad("publishAgent")).getErrCode());
        when(http.isOnlyServer("127.0.0.1:8848")).thenReturn(false);
        resolver.requireRad("publishAgent");
        when(http.getCapabilities()).thenReturn(snapshot(true));
        when(http.isOnlyServer("127.0.0.1:8848")).thenReturn(true);
        resolver.requireRad("publishAgent");
    }
    
    @ParameterizedTest
    @EnumSource(value = AgentTransportMode.class, names = {"GRPC", "AUTO"})
    void initialNegativeOrMissingGrpcRadStillChecksIndependentHttp(AgentTransportMode mode)
        throws Exception {
        for (AbilityStatus status : new AbilityStatus[] {AbilityStatus.UNKNOWN,
            AbilityStatus.NOT_SUPPORTED}) {
            connected(status, AbilityStatus.SUPPORTED);
            when(http.getCapabilities()).thenReturn(snapshot(true));
            AgentCapabilityResolver resolver = resolver(mode);
            resolver.initialize();
            assertEquals(A2aModeSelector.Mode.UNDECIDED, resolver.current());
            assertTrue(resolver.useRad());
        }
    }
    
    @Test
    void positiveGrpcRadNeverChoosesLegacyForMissingOrNegativeHttpEvidence() throws Exception {
        connected(AbilityStatus.SUPPORTED, AbilityStatus.SUPPORTED);
        AgentCapabilityResolver missing = resolver(AgentTransportMode.HTTP);
        assertTrue(missing.useRad());
        missing.requireRad("getAgentCard");
        when(http.getCapabilities()).thenReturn(snapshot(false));
        AgentCapabilityResolver negative = resolver(AgentTransportMode.HTTP);
        assertTrue(negative.useRad());
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, assertThrows(NacosException.class,
            () -> negative.requireRad("getAgentCard")).getErrCode());
        assertEquals(A2aModeSelector.Mode.RAD, negative.current());
    }
    
    @ParameterizedTest
    @EnumSource(value = AgentTransportMode.class, names = {"GRPC", "AUTO"})
    void positiveInitializationFixesRadWithoutHttpAndSurvivesDisconnect(AgentTransportMode mode)
        throws Exception {
        connected(AbilityStatus.SUPPORTED, AbilityStatus.SUPPORTED);
        AgentCapabilityResolver resolver = resolver(mode);
        resolver.initialize();
        assertEquals(A2aModeSelector.Mode.RAD, resolver.current());
        when(client.isEnable()).thenReturn(false);
        assertTrue(resolver.useRad());
        verify(http, never()).getCapabilities();
    }
    
    @ParameterizedTest
    @EnumSource(value = AbilityStatus.class, names = {"SUPPORTED", "NOT_SUPPORTED", "UNKNOWN"})
    void explicitGrpcNativeGuardUsesLegacyEvidenceOnlyWhenRadIsUnknown(AbilityStatus legacy)
        throws Exception {
        connected(AbilityStatus.UNKNOWN, legacy);
        AgentCapabilityResolver resolver = resolver(AgentTransportMode.GRPC);
        if (legacy == AbilityStatus.SUPPORTED) {
            assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, assertThrows(NacosException.class,
                () -> resolver.requireRad("publishAgent")).getErrCode());
        } else {
            resolver.requireRad("publishAgent");
            assertEquals(NacosException.SERVER_ERROR,
                assertThrows(NacosException.class, resolver::useRad).getErrCode());
        }
        assertEquals(A2aModeSelector.Mode.UNDECIDED, resolver.current());
    }
    
    @Test
    void nullGrpcAbilityIsUnknownAndNegativeRadSelectsLegacy() throws Exception {
        connected(null, null);
        AgentCapabilityResolver resolver = resolver(AgentTransportMode.GRPC);
        assertThrows(NacosException.class, resolver::useRad);
        assertEquals(A2aModeSelector.Mode.UNDECIDED, resolver.current());
        connected(AbilityStatus.NOT_SUPPORTED, AbilityStatus.UNKNOWN);
        assertFalse(resolver.useRad());
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, assertThrows(NacosException.class,
            () -> resolver.requireRad("searchAgents")).getErrCode());
    }
    
    private AgentCapabilityResolver resolver(AgentTransportMode mode) {
        return new AgentCapabilityResolver(mode, grpc, client, http);
    }
    
    private void connected(AbilityStatus rad, AbilityStatus legacy) {
        when(client.isEnable()).thenReturn(true);
        when(client.getServerAbility(AbilityKey.SERVER_RAD_V1)).thenReturn(rad);
        when(client.getServerAbility(AbilityKey.SERVER_AGENT_REGISTRY)).thenReturn(legacy);
    }
    
    private AiCapabilitySnapshot snapshot(boolean supported) throws NacosException {
        return AiCapabilitySnapshot.parse("{\"code\":0,\"data\":{\"schemaVersion\":1,"
            + "\"capabilities\":{\"radV1\":" + supported + "}}}");
    }
}
