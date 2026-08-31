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

import com.alibaba.nacos.api.ai.remote.request.cluster.AgentProjectionChangeClusterRequest;
import com.alibaba.nacos.api.ai.remote.response.cluster.AgentProjectionChangeClusterResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.cluster.remote.ClusterRpcClientProxy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultAgentProjectionClusterChangePublisherTest {
    
    @AfterEach
    void tearDown() {
        AgentWatchMetrics.resetGaugesForTest();
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
            any(AgentProjectionChangeClusterRequest.class)))
            .thenReturn(new AgentProjectionChangeClusterResponse());
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        DefaultAgentProjectionClusterChangePublisher publisher =
            new DefaultAgentProjectionClusterChangePublisher(memberManager, clientProxy, executor);
        double successBefore = AgentWatchMetrics.eventCount(
            AgentWatchMetrics.Event.CLUSTER_HINT, AgentWatchMetrics.Result.SUCCESS);
        
        publisher.publish("tenant", "agent");
        publisher.publish("tenant", "agent");
        assertEquals(1, publisher.pendingCount());
        verify(executor).execute(task.capture());
        task.getValue().run();
        
        ArgumentCaptor<AgentProjectionChangeClusterRequest> request =
            ArgumentCaptor.forClass(AgentProjectionChangeClusterRequest.class);
        verify(clientProxy, times(2)).sendRequest(any(Member.class), request.capture());
        assertEquals("tenant", request.getValue().getNamespaceId());
        assertEquals("agent", request.getValue().getAgentName());
        assertEquals(0, publisher.pendingCount());
        assertEquals(successBefore + 2D, AgentWatchMetrics.eventCount(
            AgentWatchMetrics.Event.CLUSTER_HINT, AgentWatchMetrics.Result.SUCCESS));
        publisher.shutdown();
        publisher.shutdown();
        publisher.publish("tenant", "ignored");
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
            any(AgentProjectionChangeClusterRequest.class));
        when(clientProxy.sendRequest(eq(second),
            any(AgentProjectionChangeClusterRequest.class)))
            .thenReturn(new AgentProjectionChangeClusterResponse());
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(executor).execute(any(Runnable.class));
        DefaultAgentProjectionClusterChangePublisher publisher =
            new DefaultAgentProjectionClusterChangePublisher(memberManager, clientProxy, executor);
        double failedBefore = AgentWatchMetrics.eventCount(
            AgentWatchMetrics.Event.CLUSTER_HINT, AgentWatchMetrics.Result.FAILED);
        double successBefore = AgentWatchMetrics.eventCount(
            AgentWatchMetrics.Event.CLUSTER_HINT, AgentWatchMetrics.Result.SUCCESS);
        
        publisher.publish("tenant", "agent");
        
        verify(clientProxy).sendRequest(eq(second),
            any(AgentProjectionChangeClusterRequest.class));
        assertEquals(failedBefore + 1D, AgentWatchMetrics.eventCount(
            AgentWatchMetrics.Event.CLUSTER_HINT, AgentWatchMetrics.Result.FAILED));
        assertEquals(successBefore + 1D, AgentWatchMetrics.eventCount(
            AgentWatchMetrics.Event.CLUSTER_HINT, AgentWatchMetrics.Result.SUCCESS));
        publisher.shutdown();
    }
    
    @Test
    void testRejectedDrainRetainsPendingChange() {
        ServerMemberManager memberManager = mock(ServerMemberManager.class);
        ClusterRpcClientProxy clientProxy = mock(ClusterRpcClientProxy.class);
        ExecutorService executor = mock(ExecutorService.class);
        doThrow(new RejectedExecutionException()).when(executor).execute(any(Runnable.class));
        DefaultAgentProjectionClusterChangePublisher publisher =
            new DefaultAgentProjectionClusterChangePublisher(memberManager, clientProxy, executor);
        double failedBefore = AgentWatchMetrics.eventCount(
            AgentWatchMetrics.Event.CLUSTER_HINT, AgentWatchMetrics.Result.FAILED);
        
        publisher.publish("tenant", "agent");
        
        assertEquals(1, publisher.pendingCount());
        assertEquals(failedBefore + 1D, AgentWatchMetrics.eventCount(
            AgentWatchMetrics.Event.CLUSTER_HINT, AgentWatchMetrics.Result.FAILED));
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
        DefaultAgentProjectionClusterChangePublisher publisher =
            new DefaultAgentProjectionClusterChangePublisher(memberManager, clientProxy, executor);
        
        publisher.publish("tenant", "agent");
        
        assertEquals(0, publisher.pendingCount());
        verify(clientProxy, never()).sendRequest(any(Member.class),
            any(AgentProjectionChangeClusterRequest.class));
        publisher.shutdown();
    }
    
    @Test
    void testDrainCompletionReschedulesConcurrentChange() {
        ServerMemberManager memberManager = mock(ServerMemberManager.class);
        ClusterRpcClientProxy clientProxy = mock(ClusterRpcClientProxy.class);
        ExecutorService executor = mock(ExecutorService.class);
        DefaultAgentProjectionClusterChangePublisher publisher =
            new DefaultAgentProjectionClusterChangePublisher(memberManager, clientProxy, executor);
        publisher.publish("tenant", "agent");
        
        publisher.completeDrain();
        
        verify(executor, times(2)).execute(any(Runnable.class));
        assertEquals(1, publisher.pendingCount());
        publisher.shutdown();
    }
    
    @Test
    void testProductionExecutorAndNoopPublisherCanShutdown() {
        ServerMemberManager memberManager = mock(ServerMemberManager.class);
        when(memberManager.allMembersWithoutSelf()).thenReturn(Collections.emptyList());
        DefaultAgentProjectionClusterChangePublisher publisher =
            new DefaultAgentProjectionClusterChangePublisher(memberManager,
                mock(ClusterRpcClientProxy.class));
        AgentProjectionClusterChangePublisher.NOOP.publish("tenant", "agent");
        publisher.publish("tenant", "agent");
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
            context.register(DefaultAgentProjectionClusterChangePublisher.class);
            context.refresh();
            assertEquals(DefaultAgentProjectionClusterChangePublisher.class,
                context.getBean(AgentProjectionClusterChangePublisher.class).getClass());
        }
    }
}
