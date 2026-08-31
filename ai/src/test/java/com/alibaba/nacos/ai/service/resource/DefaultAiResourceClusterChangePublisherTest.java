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

package com.alibaba.nacos.ai.service.resource;

import com.alibaba.nacos.ai.event.AiResourceChangeOperation;
import com.alibaba.nacos.ai.event.AiResourceChangedEvent;
import com.alibaba.nacos.api.ai.remote.request.cluster.AiResourceChangeClusterRequest;
import com.alibaba.nacos.api.ai.remote.response.cluster.AiResourceChangeClusterResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.cluster.remote.ClusterRpcClientProxy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultAiResourceClusterChangePublisherTest {
    
    @Test
    void testNullEventAndResourceKeyEqualityBoundaries() {
        ExecutorService executor = mock(ExecutorService.class);
        DefaultAiResourceClusterChangePublisher publisher =
            new DefaultAiResourceClusterChangePublisher(mock(ServerMemberManager.class),
                mock(ClusterRpcClientProxy.class), executor);
        publisher.publish(null);
        verify(executor, never()).execute(any(Runnable.class));
        
        publisher.publish(event(AiResourceChangeOperation.UPDATE, false));
        @SuppressWarnings("unchecked")
        Map<Object, AiResourceChangedEvent> pending =
            (Map<Object, AiResourceChangedEvent>) org.springframework.test.util.ReflectionTestUtils
                .getField(publisher, "pending");
        Object key = pending.keySet().iterator().next();
        assertTrue(key.equals(key));
        assertFalse(key.equals("not-a-resource-key"));
        publisher.shutdown();
    }
    
    @Test
    void testLogicalChangesAreCoalescedAndSentToEveryPeer() throws Exception {
        ServerMemberManager memberManager = mock(ServerMemberManager.class);
        ClusterRpcClientProxy clientProxy = mock(ClusterRpcClientProxy.class);
        ExecutorService executor = mock(ExecutorService.class);
        Member first = mock(Member.class);
        Member second = mock(Member.class);
        when(memberManager.allMembersWithoutSelf()).thenReturn(Arrays.asList(first, second));
        when(clientProxy.sendRequest(any(Member.class),
            any(AiResourceChangeClusterRequest.class)))
            .thenReturn(new AiResourceChangeClusterResponse());
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        DefaultAiResourceClusterChangePublisher publisher =
            new DefaultAiResourceClusterChangePublisher(memberManager, clientProxy, executor);
        publisher.publish(event(AiResourceChangeOperation.CREATE, true));
        publisher.publish(event(AiResourceChangeOperation.UPDATE, false));
        assertEquals(1, publisher.pendingCount());
        verify(executor).execute(task.capture());
        task.getValue().run();
        
        ArgumentCaptor<AiResourceChangeClusterRequest> request =
            ArgumentCaptor.forClass(AiResourceChangeClusterRequest.class);
        verify(clientProxy, times(2)).sendRequest(any(Member.class), request.capture());
        assertEquals("tenant", request.getValue().getNamespaceId());
        assertEquals("agent", request.getValue().getResourceType());
        assertEquals("agent-name", request.getValue().getResourceName());
        assertEquals(AiResourceChangeOperation.UPDATE.name(), request.getValue().getOperation());
        assertTrue(request.getValue().isStorageChanged());
        assertEquals(0, publisher.pendingCount());
        publisher.shutdown();
        publisher.shutdown();
        publisher.publish(event(AiResourceChangeOperation.DELETE, true));
        verify(executor).shutdownNow();
    }
    
    @Test
    void testPeerFailureDoesNotStopRemainingDelivery() throws Exception {
        ServerMemberManager memberManager = mock(ServerMemberManager.class);
        ClusterRpcClientProxy clientProxy = mock(ClusterRpcClientProxy.class);
        ExecutorService executor = mock(ExecutorService.class);
        Member first = mock(Member.class);
        Member second = mock(Member.class);
        when(memberManager.allMembersWithoutSelf()).thenReturn(Arrays.asList(first, second));
        doThrow(new NacosException()).when(clientProxy).sendRequest(eq(first),
            any(AiResourceChangeClusterRequest.class));
        when(clientProxy.sendRequest(eq(second), any(AiResourceChangeClusterRequest.class)))
            .thenReturn(new AiResourceChangeClusterResponse());
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(executor).execute(any(Runnable.class));
        DefaultAiResourceClusterChangePublisher publisher =
            new DefaultAiResourceClusterChangePublisher(memberManager, clientProxy, executor);
        publisher.publish(event(AiResourceChangeOperation.UPDATE, false));
        
        verify(clientProxy).sendRequest(eq(second), any(AiResourceChangeClusterRequest.class));
        publisher.shutdown();
    }
    
    @Test
    void testRejectedDrainRetainsPendingChange() {
        ServerMemberManager memberManager = mock(ServerMemberManager.class);
        ClusterRpcClientProxy clientProxy = mock(ClusterRpcClientProxy.class);
        ExecutorService executor = mock(ExecutorService.class);
        doThrow(new RejectedExecutionException()).when(executor).execute(any(Runnable.class));
        DefaultAiResourceClusterChangePublisher publisher =
            new DefaultAiResourceClusterChangePublisher(memberManager, clientProxy, executor);
        
        publisher.publish(event(AiResourceChangeOperation.UPDATE, false));
        
        assertEquals(1, publisher.pendingCount());
        publisher.shutdown();
        assertEquals(0, publisher.pendingCount());
    }
    
    @Test
    void testNoPeerCompletesDrainWithoutDelivery() throws Exception {
        ServerMemberManager memberManager = mock(ServerMemberManager.class);
        ClusterRpcClientProxy clientProxy = mock(ClusterRpcClientProxy.class);
        ExecutorService executor = mock(ExecutorService.class);
        when(memberManager.allMembersWithoutSelf()).thenReturn(Collections.emptyList());
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(executor).execute(any(Runnable.class));
        DefaultAiResourceClusterChangePublisher publisher =
            new DefaultAiResourceClusterChangePublisher(memberManager, clientProxy, executor);
        
        publisher.publish(event(AiResourceChangeOperation.UPDATE, false));
        
        assertEquals(0, publisher.pendingCount());
        verify(clientProxy, never()).sendRequest(any(Member.class),
            any(AiResourceChangeClusterRequest.class));
        publisher.shutdown();
    }
    
    @Test
    void testDrainCompletionReschedulesConcurrentChange() {
        ServerMemberManager memberManager = mock(ServerMemberManager.class);
        ClusterRpcClientProxy clientProxy = mock(ClusterRpcClientProxy.class);
        ExecutorService executor = mock(ExecutorService.class);
        DefaultAiResourceClusterChangePublisher publisher =
            new DefaultAiResourceClusterChangePublisher(memberManager, clientProxy, executor);
        publisher.publish(event(AiResourceChangeOperation.UPDATE, false));
        
        publisher.completeDrain();
        
        verify(executor, times(2)).execute(any(Runnable.class));
        assertEquals(1, publisher.pendingCount());
        publisher.shutdown();
    }
    
    @Test
    void testProductionExecutorNoopAndNonAgentMetricIsolation() {
        ServerMemberManager memberManager = mock(ServerMemberManager.class);
        when(memberManager.allMembersWithoutSelf()).thenReturn(Collections.emptyList());
        DefaultAiResourceClusterChangePublisher publisher =
            new DefaultAiResourceClusterChangePublisher(memberManager,
                mock(ClusterRpcClientProxy.class));
        AiResourceClusterChangePublisher.NOOP.publish(event(AiResourceChangeOperation.UPDATE,
            false));
        publisher.publish(new AiResourceChangedEvent("tenant", "skill", "skill-name",
            AiResourceChangeOperation.UPDATE, true));
        publisher.shutdown();
        assertEquals(0, publisher.pendingCount());
    }
    
    @Test
    void testSpringSelectsProductionConstructor() {
        try (AnnotationConfigApplicationContext context =
            new AnnotationConfigApplicationContext()) {
            context.getBeanFactory().registerSingleton("serverMemberManagerMock",
                mock(ServerMemberManager.class));
            context.getBeanFactory().registerSingleton("clusterRpcClientProxyMock",
                mock(ClusterRpcClientProxy.class));
            context.register(DefaultAiResourceClusterChangePublisher.class);
            context.refresh();
            assertEquals(DefaultAiResourceClusterChangePublisher.class,
                context.getBean(AiResourceClusterChangePublisher.class).getClass());
        }
    }
    
    private AiResourceChangedEvent event(AiResourceChangeOperation operation,
        boolean storageChanged) {
        return new AiResourceChangedEvent("tenant", "agent", "agent-name", operation,
            storageChanged);
    }
}
