/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.plugin;

import com.alibaba.nacos.api.common.NodeState;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.cluster.remote.ClusterRpcClientProxy;
import com.alibaba.nacos.core.cluster.remote.request.PluginAvailabilityRequest;
import com.alibaba.nacos.core.cluster.remote.response.PluginAvailabilityResponse;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PluginAvailabilityMonitor} unit test.
 *
 * @author WangzJi
 */
@ExtendWith(MockitoExtension.class)
class PluginAvailabilityMonitorTest {

    @Mock
    private PluginManager pluginManager;

    @Mock
    private ServerMemberManager memberManager;

    @Mock
    private ClusterRpcClientProxy rpcClientProxy;

    private PluginAvailabilityMonitor monitor;

    private Member selfMember;

    @BeforeAll
    static void setUpAll() {
        MockEnvironment environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);
    }

    @BeforeEach
    void setUp() {
        monitor = new PluginAvailabilityMonitor(pluginManager, memberManager, rpcClientProxy);

        selfMember = new Member();
        selfMember.setIp("192.168.1.1");
        selfMember.setPort(8848);
        // Use lenient() to avoid UnnecessaryStubbingException for tests that don't use this
        lenient().when(memberManager.getSelf()).thenReturn(selfMember);
    }

    @AfterEach
    void tearDown() {
        monitor.shutdown();
    }

    @Test
    void shutdownTest() {
        monitor.shutdown();

        Boolean shutdown = (Boolean) ReflectionTestUtils.getField(monitor, "shutdown");
        assertTrue(shutdown);
    }

    @Test
    void checkClusterAvailabilityEmptyPluginsTest() throws Exception {
        when(pluginManager.getLocalPluginIds()).thenReturn(Collections.emptySet());

        invokeAvailabilityCheck();

        // No remote calls should be made
        verify(rpcClientProxy, never()).sendRequest(any(Member.class), any());
        verify(pluginManager, never()).updateNodeAvailability(any(), any());
    }

    @Test
    void checkClusterAvailabilityLocalOnlyTest() throws Exception {
        Set<String> pluginIds = new HashSet<>();
        pluginIds.add("trace:otel");

        when(pluginManager.getLocalPluginIds()).thenReturn(pluginIds);
        when(pluginManager.isPluginAvailable("trace:otel")).thenReturn(true);
        when(memberManager.allMembersWithoutSelf()).thenReturn(Collections.emptyList());

        invokeAvailabilityCheck();

        // Verify local availability was checked
        verify(pluginManager).isPluginAvailable("trace:otel");

        // Verify update was called with correct data
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Boolean>> captor = ArgumentCaptor.forClass(Map.class);
        verify(pluginManager).updateNodeAvailability(eq("trace:otel"), captor.capture());

        Map<String, Boolean> availability = captor.getValue();
        assertEquals(1, availability.size());
        assertTrue(availability.get(selfMember.getAddress()));
    }

    @Test
    void checkClusterAvailabilityWithRemoteMembersTest() throws Exception {
        Set<String> pluginIds = new HashSet<>();
        pluginIds.add("auth:custom");

        Member remoteMember = new Member();
        remoteMember.setIp("192.168.1.2");
        remoteMember.setPort(8848);
        remoteMember.setState(NodeState.UP);

        when(pluginManager.getLocalPluginIds()).thenReturn(pluginIds);
        when(pluginManager.isPluginAvailable("auth:custom")).thenReturn(true);
        when(memberManager.allMembersWithoutSelf()).thenReturn(Collections.singletonList(remoteMember));

        PluginAvailabilityResponse response = new PluginAvailabilityResponse();
        response.setPluginId("auth:custom");
        response.setAvailable(true);
        when(rpcClientProxy.sendRequest(eq(remoteMember), any(PluginAvailabilityRequest.class))).thenReturn(response);

        invokeAvailabilityCheck();

        // Verify RPC was called
        verify(rpcClientProxy).sendRequest(eq(remoteMember), any(PluginAvailabilityRequest.class));

        // Verify update was called with both local and remote data
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Boolean>> captor = ArgumentCaptor.forClass(Map.class);
        verify(pluginManager).updateNodeAvailability(eq("auth:custom"), captor.capture());

        Map<String, Boolean> availability = captor.getValue();
        assertEquals(2, availability.size());
        assertTrue(availability.get(selfMember.getAddress()));
        assertTrue(availability.get(remoteMember.getAddress()));
    }

    @Test
    void checkClusterAvailabilityRemoteMemberNotUpTest() throws Exception {
        Set<String> pluginIds = new HashSet<>();
        pluginIds.add("trace:otel");

        Member remoteMember = new Member();
        remoteMember.setIp("192.168.1.2");
        remoteMember.setPort(8848);
        remoteMember.setState(NodeState.DOWN);

        when(pluginManager.getLocalPluginIds()).thenReturn(pluginIds);
        when(pluginManager.isPluginAvailable("trace:otel")).thenReturn(true);
        when(memberManager.allMembersWithoutSelf()).thenReturn(Collections.singletonList(remoteMember));

        invokeAvailabilityCheck();

        // No RPC call for DOWN member
        verify(rpcClientProxy, never()).sendRequest(any(Member.class), any());

        // Verify update was called with remote marked as unavailable
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Boolean>> captor = ArgumentCaptor.forClass(Map.class);
        verify(pluginManager).updateNodeAvailability(eq("trace:otel"), captor.capture());

        Map<String, Boolean> availability = captor.getValue();
        assertEquals(2, availability.size());
        assertTrue(availability.get(selfMember.getAddress()));
        assertFalse(availability.get(remoteMember.getAddress()));
    }

    @Test
    void checkClusterAvailabilityRpcFailureTest() throws Exception {
        Set<String> pluginIds = new HashSet<>();
        pluginIds.add("trace:otel");

        Member remoteMember = new Member();
        remoteMember.setIp("192.168.1.2");
        remoteMember.setPort(8848);
        remoteMember.setState(NodeState.UP);

        when(pluginManager.getLocalPluginIds()).thenReturn(pluginIds);
        when(pluginManager.isPluginAvailable("trace:otel")).thenReturn(true);
        when(memberManager.allMembersWithoutSelf()).thenReturn(Collections.singletonList(remoteMember));
        when(rpcClientProxy.sendRequest(eq(remoteMember), any(PluginAvailabilityRequest.class)))
                .thenThrow(new NacosException(500, "Connection failed"));

        invokeAvailabilityCheck();

        // Verify update was called with remote marked as unavailable due to RPC failure
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Boolean>> captor = ArgumentCaptor.forClass(Map.class);
        verify(pluginManager).updateNodeAvailability(eq("trace:otel"), captor.capture());

        Map<String, Boolean> availability = captor.getValue();
        assertEquals(2, availability.size());
        assertTrue(availability.get(selfMember.getAddress()));
        assertFalse(availability.get(remoteMember.getAddress()));
    }

    @Test
    void checkClusterAvailabilityMultiplePluginsTest() throws Exception {
        Set<String> pluginIds = new HashSet<>(Arrays.asList("trace:otel", "auth:custom"));

        when(pluginManager.getLocalPluginIds()).thenReturn(pluginIds);
        when(pluginManager.isPluginAvailable("trace:otel")).thenReturn(true);
        when(pluginManager.isPluginAvailable("auth:custom")).thenReturn(false);
        when(memberManager.allMembersWithoutSelf()).thenReturn(Collections.emptyList());

        invokeAvailabilityCheck();

        // Verify both plugins were checked
        verify(pluginManager, times(2)).updateNodeAvailability(any(), any());
    }

    @Test
    void checkClusterAvailabilityLocalPluginUnavailableTest() throws Exception {
        Set<String> pluginIds = new HashSet<>();
        pluginIds.add("trace:otel");

        when(pluginManager.getLocalPluginIds()).thenReturn(pluginIds);
        when(pluginManager.isPluginAvailable("trace:otel")).thenReturn(false);
        when(memberManager.allMembersWithoutSelf()).thenReturn(Collections.emptyList());

        invokeAvailabilityCheck();

        // Verify update was called with local marked as unavailable
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Boolean>> captor = ArgumentCaptor.forClass(Map.class);
        verify(pluginManager).updateNodeAvailability(eq("trace:otel"), captor.capture());

        Map<String, Boolean> availability = captor.getValue();
        assertEquals(1, availability.size());
        assertFalse(availability.get(selfMember.getAddress()));
    }

    /**
     * Invoke the private checkClusterAvailability method via the inner task class.
     */
    private void invokeAvailabilityCheck() throws Exception {
        // Get all declared classes of PluginAvailabilityMonitor
        Class<?>[] declaredClasses = PluginAvailabilityMonitor.class.getDeclaredClasses();
        Class<?> taskClass = null;
        for (Class<?> clazz : declaredClasses) {
            if (clazz.getSimpleName().equals("AvailabilityCheckTask")) {
                taskClass = clazz;
                break;
            }
        }

        if (taskClass == null) {
            throw new IllegalStateException("Cannot find AvailabilityCheckTask inner class");
        }

        // Create instance of the inner class
        Constructor<?> constructor = taskClass.getDeclaredConstructor(PluginAvailabilityMonitor.class);
        constructor.setAccessible(true);
        Object task = constructor.newInstance(monitor);

        // Invoke the checkClusterAvailability method
        Method method = taskClass.getDeclaredMethod("checkClusterAvailability");
        method.setAccessible(true);
        method.invoke(task);
    }
}
