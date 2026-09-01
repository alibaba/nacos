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

package com.alibaba.nacos.ai.service.a2a.migration;

import com.alibaba.nacos.ai.service.a2a.CanonicalA2aEndpointOperationService;
import com.alibaba.nacos.ai.service.a2a.LegacyA2aEndpointOperationService;
import com.alibaba.nacos.ai.service.agent.runtime.AgentRuntimePublicationCapacityGate;
import com.alibaba.nacos.ai.service.agent.runtime.AgentRuntimePublicationCapacityGate.PublicationOperation;
import com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.remote.RemoteConstants;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.lang.reflect.Constructor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class A2aMigrationEndpointRouterTest {
    
    private static final String CLIENT = "client-1";
    
    @Mock
    private A2aMigrationStateService stateService;
    
    @Mock
    private LegacyA2aEndpointOperationService legacyService;
    
    @Mock
    private CanonicalA2aEndpointOperationService canonicalService;
    
    @Mock
    private AgentRuntimePublicationCapacityGate capacityGate;
    
    @Mock
    private ScheduledExecutorService executor;
    
    @Mock
    private Connection connection;
    
    @Mock
    private ConnectionMeta connectionMeta;
    
    private A2aMigrationEndpointRouter router;
    
    @BeforeEach
    void setUp() throws NacosException {
        router = router(4);
        lenient().doAnswer(invocation -> {
            invocation.<PublicationOperation>getArgument(3).run();
            return null;
        }).when(capacityGate).registerLogical(anyString(), anyString(), anyInt(),
            any(PublicationOperation.class));
        lenient().doAnswer(invocation -> {
            invocation.<PublicationOperation>getArgument(2).run();
            return null;
        }).when(capacityGate).deregisterLogical(anyString(), anyString(),
            any(PublicationOperation.class));
    }
    
    @AfterEach
    void tearDown() {
        router.destroy();
    }
    
    @Test
    void shouldResolveConfiguredState() {
        when(stateService.resolveConfigured()).thenReturn(A2aMigrationState.SYNCING);
        assertEquals(A2aMigrationState.SYNCING, router.resolveState());
    }
    
    @Test
    void shouldConstructAndDestroyDefaultManagedRouter() {
        A2aMigrationEndpointRouter defaultRouter = new A2aMigrationEndpointRouter(stateService,
            legacyService, canonicalService, capacityGate);
        defaultRouter.destroy();
    }
    
    @Test
    void shouldValidateThenWriteLegacyPrimaryAndCanonicalMirror() throws NacosException {
        AgentEndpoint endpoint = endpoint("1.0.0", 8080);
        
        router.register(CLIENT, "public", "demo", endpoint, "source",
            A2aMigrationState.SYNCING);
        
        InOrder order = inOrder(legacyService, canonicalService, capacityGate);
        order.verify(legacyService).validate(any(AgentEndpoint.class));
        order.verify(canonicalService).validate(eq("public"), eq("demo"), any(Collection.class));
        order.verify(capacityGate).registerLogical(eq(CLIENT), anyString(), eq(1),
            any(PublicationOperation.class));
        order.verify(legacyService).registerChild(eq(CLIENT), eq("public"), eq("demo"),
            any(AgentEndpoint.class), eq("source"));
        order.verify(canonicalService).register(eq(CLIENT), eq("public"), eq("demo"),
            any(Collection.class));
        assertEquals(1, router.publicationCount(CLIENT));
        assertFalse(router.hasPendingRetries());
    }
    
    @Test
    void shouldUseSameRouteWhileQuiescingAndSupportCompleteBatchReplace()
        throws NacosException {
        Collection<AgentEndpoint> endpoints = Arrays.asList(endpoint("1.0.0", 8080),
            endpoint("1.0.0", 8081));
        
        router.register(CLIENT, "public", "demo", endpoints, "source",
            A2aMigrationState.QUIESCING);
        
        verify(legacyService).validate(any(Collection.class));
        verify(legacyService).registerChild(CLIENT, "public", "demo", endpoints, "source");
        verify(canonicalService).register(eq(CLIENT), eq("public"), eq("demo"),
            any(Collection.class));
        verify(capacityGate).registerLogical(eq(CLIENT), anyString(), eq(2),
            any(PublicationOperation.class));
        assertEquals(2, router.publicationCount(CLIENT));
    }
    
    @Test
    void shouldNotMirrorOrCacheWhenPrimaryFails() throws NacosException {
        AgentEndpoint endpoint = endpoint("1.0.0", 8080);
        doThrow(new NacosException(NacosException.SERVER_ERROR, "primary failed"))
            .when(legacyService).registerChild(eq(CLIENT), eq("public"), eq("demo"),
                any(AgentEndpoint.class), eq("source"));
        
        assertThrows(NacosException.class,
            () -> router.register(CLIENT, "public", "demo", endpoint, "source",
                A2aMigrationState.SYNCING));
        
        verify(canonicalService, never()).register(anyString(), anyString(), anyString(),
            any(Collection.class));
        assertEquals(0, router.publicationCount(CLIENT));
        assertEquals(0, router.pendingRetryCount(CLIENT));
    }
    
    @Test
    void shouldQueueCoalesceAndRecoverCanonicalMirrorWithCopiedSnapshot()
        throws NacosException {
        AgentEndpoint endpoint = endpoint("1.0.0", 8080);
        doThrow(new NacosException(NacosException.SERVER_ERROR, "mirror failed"))
            .doThrow(new NacosException(NacosException.SERVER_ERROR, "mirror failed again"))
            .doNothing().when(canonicalService).register(eq(CLIENT), eq("public"), eq("demo"),
                any(Collection.class));
        
        router.register(CLIENT, "public", "demo", endpoint, "source",
            A2aMigrationState.SYNCING);
        endpoint.setPort(9090);
        router.register(CLIENT, "public", "demo", endpoint, "source",
            A2aMigrationState.SYNCING);
        endpoint.setPort(10000);
        
        assertEquals(1, router.pendingRetryCount(CLIENT));
        router.retryPendingNow();
        
        assertEquals(0, router.pendingRetryCount(CLIENT));
        ArgumentCaptor<Collection<AgentEndpoint>> snapshots = ArgumentCaptor.forClass(
            Collection.class);
        verify(canonicalService, times(3)).register(eq(CLIENT), eq("public"), eq("demo"),
            snapshots.capture());
        assertEquals(9090, snapshots.getAllValues().get(2).iterator().next().getPort());
    }
    
    @Test
    void shouldRejectNewRetryIdentityWhenBoundedQueueIsFull() throws NacosException {
        router.destroy();
        router = router(1);
        doThrow(new NacosException(NacosException.SERVER_ERROR, "mirror failed"))
            .when(canonicalService).register(anyString(), anyString(), anyString(),
                any(Collection.class));
        
        router.register(CLIENT, "public", "demo", endpoint("1.0.0", 8080), "source",
            A2aMigrationState.SYNCING);
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> router.register(CLIENT, "public", "demo", endpoint("2.0.0", 8081),
                "source", A2aMigrationState.SYNCING));
        
        assertEquals(ErrorCode.AGENT_ENDPOINT_PUBLICATION_OVER_LIMIT.getCode(),
            exception.getDetailErrCode());
        verify(legacyService, times(1)).registerChild(eq(CLIENT), eq("public"), eq("demo"),
            any(AgentEndpoint.class), eq("source"));
    }
    
    @Test
    void shouldUseCanonicalOnlyAfterCutoverWhenShadowDisabled() throws NacosException {
        AgentEndpoint endpoint = endpoint("1.0.0", 8080);
        when(stateService.isLegacyNamingShadowEnabled()).thenReturn(false);
        
        router.register(CLIENT, "public", "demo", endpoint, "source",
            A2aMigrationState.CANONICAL);
        
        verify(canonicalService).validate(eq("public"), eq("demo"), any(Collection.class));
        verify(canonicalService).register(eq(CLIENT), eq("public"), eq("demo"),
            any(Collection.class));
        router.deregister(CLIENT, "public", "demo", endpoint, "source",
            A2aMigrationState.CANONICAL);
        verify(canonicalService).deregister(CLIENT, "public", "demo", "1.0.0");
        verifyNoInteractions(legacyService);
        assertFalse(router.hasPendingRetries());
    }
    
    @Test
    void shouldDeregisterBothLayoutsWithoutRetryWhenMirrorSucceeds() throws NacosException {
        AgentEndpoint endpoint = endpoint("1.0.0", 8080);
        router.register(CLIENT, "public", "demo", endpoint, "source",
            A2aMigrationState.SYNCING);
        
        router.deregister(CLIENT, "public", "demo", endpoint, "source",
            A2aMigrationState.SYNCING);
        
        verify(legacyService).deregisterChild(CLIENT, "public", "demo", endpoint, "source");
        verify(canonicalService).deregister(CLIENT, "public", "demo", "1.0.0");
        assertFalse(router.hasPendingRetries());
    }
    
    @Test
    void shouldWriteOptionalLegacyShadowAfterCanonicalPrimaryAndRetryFailure()
        throws NacosException {
        AgentEndpoint endpoint = endpoint("1.0.0", 8080);
        when(stateService.isLegacyNamingShadowEnabled()).thenReturn(true);
        doThrow(new NacosException(NacosException.SERVER_ERROR, "shadow failed"))
            .doNothing().when(legacyService).registerChild(eq(CLIENT), eq("public"), eq("demo"),
                any(AgentEndpoint.class), eq("source"));
        
        router.register(CLIENT, "public", "demo", endpoint, "source",
            A2aMigrationState.CANONICAL);
        
        InOrder order = inOrder(canonicalService, legacyService);
        order.verify(canonicalService).register(eq(CLIENT), eq("public"), eq("demo"),
            any(Collection.class));
        order.verify(legacyService).registerChild(eq(CLIENT), eq("public"), eq("demo"),
            any(AgentEndpoint.class), eq("source"));
        assertTrue(router.hasPendingRetries());
        router.retryPendingNow();
        assertFalse(router.hasPendingRetries());
        verify(legacyService, times(2)).registerChild(eq(CLIENT), eq("public"), eq("demo"),
            any(AgentEndpoint.class), eq("source"));
    }
    
    @Test
    void shouldNotWriteShadowWhenCanonicalPrimaryFails() throws NacosException {
        when(stateService.isLegacyNamingShadowEnabled()).thenReturn(true);
        doThrow(new NacosException(NacosException.SERVER_ERROR, "canonical failed"))
            .when(canonicalService).register(anyString(), anyString(), anyString(),
                any(Collection.class));
        
        assertThrows(NacosException.class,
            () -> router.register(CLIENT, "public", "demo", endpoint("1.0.0", 8080),
                "source", A2aMigrationState.CANONICAL));
        
        verify(legacyService, never()).registerChild(anyString(), anyString(), anyString(),
            any(AgentEndpoint.class), anyString());
        assertEquals(0, router.publicationCount(CLIENT));
    }
    
    @Test
    void shouldDeregisterPrimaryThenMirrorAndRetryMirrorFailure() throws NacosException {
        AgentEndpoint endpoint = endpoint("1.0.0", 8080);
        router.register(CLIENT, "public", "demo", endpoint, "source",
            A2aMigrationState.SYNCING);
        doThrow(new IllegalStateException("mirror delete failed"))
            .doNothing().when(canonicalService).deregister(CLIENT, "public", "demo", "1.0.0");
        
        router.deregister(CLIENT, "public", "demo", endpoint, "source",
            A2aMigrationState.SYNCING);
        
        verify(legacyService).deregisterChild(CLIENT, "public", "demo", endpoint, "source");
        assertEquals(0, router.publicationCount(CLIENT));
        assertEquals(1, router.pendingRetryCount(CLIENT));
        router.retryPendingNow();
        assertEquals(0, router.pendingRetryCount(CLIENT));
        verify(canonicalService, times(2)).deregister(CLIENT, "public", "demo", "1.0.0");
    }
    
    @Test
    void shouldKeepReservationWhenPrimaryDeregisterFails() throws NacosException {
        AgentEndpoint endpoint = endpoint("1.0.0", 8080);
        router.register(CLIENT, "public", "demo", endpoint, "source",
            A2aMigrationState.SYNCING);
        doThrow(new NacosException(NacosException.SERVER_ERROR, "delete failed"))
            .when(legacyService).deregisterChild(CLIENT, "public", "demo", endpoint, "source");
        
        assertThrows(NacosException.class,
            () -> router.deregister(CLIENT, "public", "demo", endpoint, "source",
                A2aMigrationState.SYNCING));
        
        assertEquals(1, router.publicationCount(CLIENT));
        verify(canonicalService, never()).deregister(anyString(), anyString(), anyString(),
            anyString());
    }
    
    @Test
    void shouldValidateAllLayoutsBeforePrimaryAndRejectInvalidInput() throws NacosException {
        AgentEndpoint endpoint = endpoint("1.0.0", 8080);
        doThrow(new NacosApiException(NacosException.INVALID_PARAM,
            ErrorCode.PARAMETER_VALIDATE_ERROR, "canonical invalid"))
            .when(canonicalService).validate(eq("public"), eq("demo"), any(Collection.class));
        
        assertThrows(NacosApiException.class,
            () -> router.register(CLIENT, "public", "demo", endpoint, "source",
                A2aMigrationState.SYNCING));
        verify(legacyService).validate(any(AgentEndpoint.class));
        verify(legacyService, never()).registerChild(anyString(), anyString(), anyString(),
            any(AgentEndpoint.class), anyString());
        verifyNoInteractions(capacityGate);
        
        assertThrows(NacosApiException.class,
            () -> router.register(CLIENT, "public", "demo", (AgentEndpoint) null, "source",
                A2aMigrationState.SYNCING));
        assertThrows(NacosApiException.class,
            () -> router.register(CLIENT, "public", "demo", (Collection<AgentEndpoint>) null,
                "source", A2aMigrationState.SYNCING));
        assertThrows(NacosApiException.class,
            () -> router.register(CLIENT, "public", "demo", Collections.emptyList(), "source",
                A2aMigrationState.SYNCING));
        assertThrows(NacosApiException.class,
            () -> router.register(CLIENT, "public", "demo", Collections.singletonList(null),
                "source", A2aMigrationState.SYNCING));
        assertThrows(NacosApiException.class,
            () -> router.deregister(CLIENT, "public", "demo", endpoint(null, 8080), "source",
                A2aMigrationState.CANONICAL));
        assertThrows(IllegalArgumentException.class,
            () -> router.register(CLIENT, "public", "demo", endpoint, "source", null));
    }
    
    @Test
    void shouldReleaseConnectionStateAndCapacityOnlyForAiConnections()
        throws NacosException {
        router.register(CLIENT, "public", "demo", endpoint("1.0.0", 8080), "source",
            A2aMigrationState.SYNCING);
        when(connection.getMetaInfo()).thenReturn(connectionMeta);
        when(connectionMeta.getConnectionId()).thenReturn(CLIENT);
        when(connectionMeta.getLabel(RemoteConstants.LABEL_MODULE)).thenReturn("naming");
        
        router.clientConnected(connection);
        router.clientDisConnected(connection);
        assertEquals(1, router.publicationCount(CLIENT));
        
        when(connectionMeta.getLabel(RemoteConstants.LABEL_MODULE))
            .thenReturn(RemoteConstants.LABEL_MODULE_AI);
        router.clientDisConnected(connection);
        assertEquals(0, router.publicationCount(CLIENT));
        verify(capacityGate).clearLogicalPublications(CLIENT);
    }
    
    @Test
    void shouldExecuteScheduledRetryAndRescheduleWhileStillFailing() throws NacosException {
        doThrow(new NacosException(NacosException.SERVER_ERROR, "failed"))
            .doThrow(new NacosException(NacosException.SERVER_ERROR, "failed again"))
            .doNothing().when(canonicalService).register(anyString(), anyString(), anyString(),
                any(Collection.class));
        ArgumentCaptor<Runnable> tasks = ArgumentCaptor.forClass(Runnable.class);
        
        router.register(CLIENT, "public", "demo", endpoint("1.0.0", 8080), "source",
            A2aMigrationState.SYNCING);
        verify(executor).schedule(tasks.capture(), anyLong(), eq(TimeUnit.MILLISECONDS));
        
        tasks.getValue().run();
        verify(executor, times(2)).schedule(tasks.capture(), anyLong(),
            eq(TimeUnit.MILLISECONDS));
        assertTrue(router.hasPendingRetries());
        
        tasks.getValue().run();
        assertFalse(router.hasPendingRetries());
    }
    
    @Test
    void shouldIsolateRetrySchedulingRejectionAndStopSchedulingAfterDestroy()
        throws NacosException {
        doThrow(new RejectedExecutionException("rejected")).when(executor)
            .schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS));
        doThrow(new NacosException(NacosException.SERVER_ERROR, "failed"))
            .when(canonicalService).register(anyString(), anyString(), anyString(),
                any(Collection.class));
        
        router.register(CLIENT, "public", "demo", endpoint("1.0.0", 8080), "source",
            A2aMigrationState.SYNCING);
        assertTrue(router.hasPendingRetries());
        
        router.destroy();
        router.register("client-2", "public", "demo", endpoint("1.0.0", 8080), "source",
            A2aMigrationState.SYNCING);
        verify(executor, times(1)).schedule(any(Runnable.class), anyLong(),
            eq(TimeUnit.MILLISECONDS));
        verify(capacityGate).clearLogicalPublications(CLIENT);
    }
    
    @Test
    void shouldRejectInvalidRetryConfiguration() {
        assertThrows(IllegalArgumentException.class,
            () -> new A2aMigrationEndpointRouter(stateService, legacyService, canonicalService,
                capacityGate, executor, 0, 1));
        assertThrows(IllegalArgumentException.class,
            () -> new A2aMigrationEndpointRouter(stateService, legacyService, canonicalService,
                capacityGate, executor, 1, 0));
    }
    
    @Test
    void shouldSafelyEncodeNullMigrationIdentityInRetryDiagnostics() throws NacosException {
        doThrow(new NacosException(NacosException.SERVER_ERROR, "mirror failed"))
            .when(canonicalService).register(eq(CLIENT), isNull(), eq("demo"),
                any(Collection.class));
        
        router.register(CLIENT, null, "demo", endpoint("1.0.0", 8080), "source",
            A2aMigrationState.SYNCING);
        
        assertEquals(1, router.pendingRetryCount(CLIENT));
    }
    
    @Test
    void shouldKeepPrivateRetryKeysValueBased() throws Exception {
        Class<?> publicationKeyClass = Class.forName(
            A2aMigrationEndpointRouter.class.getName() + "$PublicationKey");
        Constructor<?> publicationConstructor = publicationKeyClass.getDeclaredConstructor(
            String.class, String.class, String.class);
        publicationConstructor.setAccessible(true);
        Object first = publicationConstructor.newInstance("public", "demo", "1.0.0");
        Object same = publicationConstructor.newInstance("public", "demo", "1.0.0");
        Object different = publicationConstructor.newInstance("public", "demo", "2.0.0");
        assertTrue(first.equals(first));
        assertFalse(first.equals(null));
        assertFalse(first.equals("other"));
        assertTrue(first.equals(same));
        assertFalse(first.equals(different));
        assertEquals(first.hashCode(), same.hashCode());
        
        Class<?> targetClass = Class.forName(
            A2aMigrationEndpointRouter.class.getName() + "$Target");
        Object legacy = targetClass.getEnumConstants()[0];
        Object canonical = targetClass.getEnumConstants()[1];
        Class<?> retryKeyClass = Class.forName(
            A2aMigrationEndpointRouter.class.getName() + "$RetryKey");
        Constructor<?> retryConstructor = retryKeyClass.getDeclaredConstructor(
            publicationKeyClass, targetClass);
        retryConstructor.setAccessible(true);
        Object retry = retryConstructor.newInstance(first, legacy);
        Object sameRetry = retryConstructor.newInstance(same, legacy);
        Object differentRetry = retryConstructor.newInstance(first, canonical);
        assertTrue(retry.equals(retry));
        assertFalse(retry.equals(null));
        assertFalse(retry.equals("other"));
        assertTrue(retry.equals(sameRetry));
        assertFalse(retry.equals(differentRetry));
        assertEquals(retry.hashCode(), sameRetry.hashCode());
    }
    
    private A2aMigrationEndpointRouter router(int maxPending) {
        return new A2aMigrationEndpointRouter(stateService, legacyService, canonicalService,
            capacityGate, executor, maxPending, 1000L);
    }
    
    private AgentEndpoint endpoint(String version, int port) {
        AgentEndpoint result = new AgentEndpoint();
        result.setAddress("127.0.0.1");
        result.setPort(port);
        result.setPath("rpc");
        result.setSupportTls(true);
        result.setVersion(version);
        result.setProtocolVersion("0.3");
        result.setTenant("tenant-a");
        result.setProtocol("HTTP");
        result.setTransport("HTTP+JSON");
        result.setQuery("a=b");
        return result;
    }
}
