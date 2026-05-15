/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.console.handler.impl.inner.core;

import com.alibaba.nacos.api.common.NodeState;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.cluster.remote.ClusterRpcClientProxy;
import com.alibaba.nacos.core.cluster.remote.response.PluginAvailabilityResponse;
import com.alibaba.nacos.core.plugin.PluginManager;
import com.alibaba.nacos.core.plugin.model.PluginInfo;
import com.alibaba.nacos.core.plugin.model.vo.PluginDetailVO;
import com.alibaba.nacos.core.plugin.model.vo.PluginInfoVO;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PluginInnerHandlerTest {
    
    @Mock
    private PluginManager pluginManager;
    
    @Mock
    private ServerMemberManager memberManager;
    
    @Mock
    private ClusterRpcClientProxy rpcClientProxy;
    
    private PluginInnerHandler pluginInnerHandler;
    
    private ConfigurableEnvironment cachedEnvironment;
    
    @BeforeEach
    void setUp() {
        cachedEnvironment = EnvUtil.getEnvironment();
        EnvUtil.setEnvironment(new MockEnvironment());
        pluginInnerHandler = new PluginInnerHandler(pluginManager, memberManager, rpcClientProxy);
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(cachedEnvironment);
    }
    
    @Test
    void testListPluginsSuccessTest() throws NacosException {
        PluginInfo pluginInfo = createMockPluginInfo("auth:test", PluginType.AUTH, "test", true);
        List<PluginInfo> pluginList = Collections.singletonList(pluginInfo);
        
        Member selfMember = new Member();
        selfMember.setIp("127.0.0.1");
        selfMember.setPort(8848);
        
        when(pluginManager.listAllPlugins()).thenReturn(pluginList);
        when(memberManager.allMembers()).thenReturn(Collections.singletonList(selfMember));
        when(memberManager.getSelf()).thenReturn(selfMember);
        
        List<PluginInfoVO> result = pluginInnerHandler.listPlugins(null);
        
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("auth:test", result.get(0).getPluginId());
        assertEquals("auth", result.get(0).getPluginType());
        assertEquals("test", result.get(0).getPluginName());
        assertTrue(result.get(0).getEnabled());
    }
    
    @Test
    void testListPluginsWithTypeFilterTest() throws NacosException {
        PluginInfo authPlugin = createMockPluginInfo("auth:test", PluginType.AUTH, "test", true);
        PluginInfo tracePlugin = createMockPluginInfo("trace:test", PluginType.TRACE, "test", true);
        List<PluginInfo> pluginList = new ArrayList<>();
        pluginList.add(authPlugin);
        pluginList.add(tracePlugin);
        
        Member selfMember = new Member();
        selfMember.setIp("127.0.0.1");
        selfMember.setPort(8848);
        
        when(pluginManager.listAllPlugins()).thenReturn(pluginList);
        when(memberManager.allMembers()).thenReturn(Collections.singletonList(selfMember));
        when(memberManager.getSelf()).thenReturn(selfMember);
        
        List<PluginInfoVO> result = pluginInnerHandler.listPlugins("auth");
        
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("auth:test", result.get(0).getPluginId());
    }
    
    @Test
    void testGetPluginDetailSuccessTest() throws NacosException {
        PluginInfo pluginInfo = createMockPluginInfo("auth:test", PluginType.AUTH, "test", true);
        pluginInfo.setConfigurable(true);
        Map<String, String> config = new HashMap<>();
        config.put("key1", "value1");
        pluginInfo.setConfig(config);
        
        when(pluginManager.getPlugin("auth:test")).thenReturn(Optional.of(pluginInfo));
        
        PluginDetailVO result = pluginInnerHandler.getPluginDetail("auth", "test");
        
        assertNotNull(result);
        assertEquals("auth:test", result.getPluginId());
        assertEquals("auth", result.getPluginType());
        assertEquals("test", result.getPluginName());
        assertTrue(result.getConfigurable());
        assertNotNull(result.getConfig());
        assertEquals("value1", result.getConfig().get("key1"));
    }
    
    @Test
    void testGetPluginDetailNotFoundTest() {
        when(pluginManager.getPlugin("auth:notexist")).thenReturn(Optional.empty());
        
        assertThrows(NacosApiException.class,
            () -> pluginInnerHandler.getPluginDetail("auth", "notexist"));
    }
    
    @Test
    void testUpdatePluginStatusTest() throws NacosException {
        doNothing().when(pluginManager).setPluginEnabled(eq("auth:test"), eq(false), eq(false));
        
        pluginInnerHandler.updatePluginStatus("auth", "test", false, false);
        
        verify(pluginManager).setPluginEnabled("auth:test", false, false);
    }
    
    @Test
    void testUpdatePluginConfigTest() throws NacosException {
        Map<String, String> config = new HashMap<>();
        config.put("key1", "value1");
        
        doNothing().when(pluginManager).updatePluginConfig(eq("auth:test"), any(), eq(false));
        
        pluginInnerHandler.updatePluginConfig("auth", "test", config, false);
        
        verify(pluginManager).updatePluginConfig(eq("auth:test"), eq(config), eq(false));
    }
    
    @Test
    void testGetPluginAvailabilitySuccessTest() throws NacosException {
        Member selfMember = new Member();
        selfMember.setIp("127.0.0.1");
        selfMember.setPort(8848);
        
        when(pluginManager.isPluginAvailable("auth:test")).thenReturn(true);
        when(memberManager.allMembers()).thenReturn(Collections.singletonList(selfMember));
        when(memberManager.getSelf()).thenReturn(selfMember);
        
        Map<String, Boolean> result = pluginInnerHandler.getPluginAvailability("auth", "test");
        
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.containsKey("127.0.0.1:8848"));
    }
    
    @Test
    void testGetPluginAvailabilityPluginNotFoundTest() {
        when(pluginManager.isPluginAvailable("auth:notexist")).thenReturn(false);
        
        assertThrows(NacosApiException.class,
            () -> pluginInnerHandler.getPluginAvailability("auth", "notexist"));
    }
    
    @Test
    void testExclusivePluginTypeTest() throws NacosException {
        PluginInfo authPlugin = createMockPluginInfo("auth:test", PluginType.AUTH, "test", true);
        PluginInfo tracePlugin = createMockPluginInfo("trace:test", PluginType.TRACE, "test", true);
        List<PluginInfo> pluginList = new ArrayList<>();
        pluginList.add(authPlugin);
        pluginList.add(tracePlugin);
        
        Member selfMember = new Member();
        selfMember.setIp("127.0.0.1");
        selfMember.setPort(8848);
        
        when(pluginManager.listAllPlugins()).thenReturn(pluginList);
        when(memberManager.allMembers()).thenReturn(Collections.singletonList(selfMember));
        when(memberManager.getSelf()).thenReturn(selfMember);
        
        List<PluginInfoVO> result = pluginInnerHandler.listPlugins(null);
        
        assertEquals(2, result.size());
        PluginInfoVO authVo =
            result.stream().filter(v -> "auth".equals(v.getPluginType())).findFirst().orElse(null);
        PluginInfoVO traceVo =
            result.stream().filter(v -> "trace".equals(v.getPluginType())).findFirst().orElse(null);
        
        assertNotNull(authVo);
        assertNotNull(traceVo);
        assertTrue(authVo.getExclusive());
        assertTrue(traceVo.getExclusive());
    }
    
    @Test
    void testListPluginsWithRemoteMember() throws NacosException {
        PluginInfo pluginInfo = createMockPluginInfo("auth:test", PluginType.AUTH, "test", true);
        List<PluginInfo> pluginList = Collections.singletonList(pluginInfo);
        
        Member selfMember = new Member();
        selfMember.setIp("127.0.0.1");
        selfMember.setPort(8848);
        
        Member remoteMember = new Member();
        remoteMember.setIp("192.168.1.2");
        remoteMember.setPort(8848);
        remoteMember.setState(NodeState.UP);
        
        when(pluginManager.listAllPlugins()).thenReturn(pluginList);
        when(memberManager.allMembers()).thenReturn(Arrays.asList(selfMember, remoteMember));
        when(memberManager.getSelf()).thenReturn(selfMember);
        
        PluginAvailabilityResponse response = new PluginAvailabilityResponse();
        Map<String, Boolean> availMap = new HashMap<>();
        availMap.put("auth:test", true);
        response.setPluginAvailabilityMap(availMap);
        when(rpcClientProxy.sendRequest(eq(remoteMember), any())).thenReturn(response);
        
        List<PluginInfoVO> result = pluginInnerHandler.listPlugins(null);
        
        assertEquals(1, result.size());
        assertEquals(2, result.get(0).getTotalNodeCount());
        assertEquals(2, result.get(0).getAvailableNodeCount());
    }
    
    @Test
    void testListPluginsWithRemoteMemberDown() throws NacosException {
        PluginInfo pluginInfo = createMockPluginInfo("auth:test", PluginType.AUTH, "test", true);
        
        Member selfMember = new Member();
        selfMember.setIp("127.0.0.1");
        selfMember.setPort(8848);
        
        Member remoteMember = new Member();
        remoteMember.setIp("192.168.1.2");
        remoteMember.setPort(8848);
        remoteMember.setState(NodeState.DOWN);
        
        when(pluginManager.listAllPlugins()).thenReturn(Collections.singletonList(pluginInfo));
        when(memberManager.allMembers()).thenReturn(Arrays.asList(selfMember, remoteMember));
        when(memberManager.getSelf()).thenReturn(selfMember);
        
        List<PluginInfoVO> result = pluginInnerHandler.listPlugins(null);
        
        assertEquals(1, result.size());
        assertEquals(2, result.get(0).getTotalNodeCount());
        assertEquals(1, result.get(0).getAvailableNodeCount());
    }
    
    @Test
    void testListPluginsWithRemoteMemberNullResponse() throws NacosException {
        PluginInfo pluginInfo = createMockPluginInfo("auth:test", PluginType.AUTH, "test", true);
        
        Member selfMember = new Member();
        selfMember.setIp("127.0.0.1");
        selfMember.setPort(8848);
        
        Member remoteMember = new Member();
        remoteMember.setIp("192.168.1.2");
        remoteMember.setPort(8848);
        remoteMember.setState(NodeState.UP);
        
        when(pluginManager.listAllPlugins()).thenReturn(Collections.singletonList(pluginInfo));
        when(memberManager.allMembers()).thenReturn(Arrays.asList(selfMember, remoteMember));
        when(memberManager.getSelf()).thenReturn(selfMember);
        when(rpcClientProxy.sendRequest(eq(remoteMember), any())).thenReturn(null);
        
        List<PluginInfoVO> result = pluginInnerHandler.listPlugins(null);
        
        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getAvailableNodeCount());
    }
    
    @Test
    void testListPluginsWithRemoteMemberException() throws NacosException {
        PluginInfo pluginInfo = createMockPluginInfo("auth:test", PluginType.AUTH, "test", true);
        
        Member selfMember = new Member();
        selfMember.setIp("127.0.0.1");
        selfMember.setPort(8848);
        
        Member remoteMember = new Member();
        remoteMember.setIp("192.168.1.2");
        remoteMember.setPort(8848);
        remoteMember.setState(NodeState.UP);
        
        when(pluginManager.listAllPlugins()).thenReturn(Collections.singletonList(pluginInfo));
        when(memberManager.allMembers()).thenReturn(Arrays.asList(selfMember, remoteMember));
        when(memberManager.getSelf()).thenReturn(selfMember);
        when(rpcClientProxy.sendRequest(eq(remoteMember), any()))
            .thenThrow(new NacosException(500, "rpc failed"));
        
        List<PluginInfoVO> result = pluginInnerHandler.listPlugins(null);
        
        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getAvailableNodeCount());
    }
    
    @Test
    void testGetPluginAvailabilityWithRemoteMember() throws NacosException {
        Member selfMember = new Member();
        selfMember.setIp("127.0.0.1");
        selfMember.setPort(8848);
        
        Member remoteMember = new Member();
        remoteMember.setIp("192.168.1.2");
        remoteMember.setPort(8848);
        remoteMember.setState(NodeState.UP);
        
        when(pluginManager.isPluginAvailable("auth:test")).thenReturn(true);
        when(memberManager.allMembers()).thenReturn(Arrays.asList(selfMember, remoteMember));
        when(memberManager.getSelf()).thenReturn(selfMember);
        
        PluginAvailabilityResponse response = new PluginAvailabilityResponse();
        response.setAvailable(true);
        when(rpcClientProxy.sendRequest(eq(remoteMember), any())).thenReturn(response);
        
        Map<String, Boolean> result = pluginInnerHandler.getPluginAvailability("auth", "test");
        
        assertEquals(2, result.size());
        assertTrue(result.get("127.0.0.1:8848"));
        assertTrue(result.get("192.168.1.2:8848"));
    }
    
    @Test
    void testGetPluginAvailabilityWithRemoteMemberDown() throws NacosException {
        Member selfMember = new Member();
        selfMember.setIp("127.0.0.1");
        selfMember.setPort(8848);
        
        Member remoteMember = new Member();
        remoteMember.setIp("192.168.1.2");
        remoteMember.setPort(8848);
        remoteMember.setState(NodeState.DOWN);
        
        when(pluginManager.isPluginAvailable("auth:test")).thenReturn(true);
        when(memberManager.allMembers()).thenReturn(Arrays.asList(selfMember, remoteMember));
        when(memberManager.getSelf()).thenReturn(selfMember);
        
        Map<String, Boolean> result = pluginInnerHandler.getPluginAvailability("auth", "test");
        
        assertEquals(2, result.size());
        assertTrue(result.get("127.0.0.1:8848"));
        assertFalse(result.get("192.168.1.2:8848"));
    }
    
    @Test
    void testGetPluginAvailabilityWithRemoteNullResponse() throws NacosException {
        Member selfMember = new Member();
        selfMember.setIp("127.0.0.1");
        selfMember.setPort(8848);
        
        Member remoteMember = new Member();
        remoteMember.setIp("192.168.1.2");
        remoteMember.setPort(8848);
        remoteMember.setState(NodeState.UP);
        
        when(pluginManager.isPluginAvailable("auth:test")).thenReturn(true);
        when(memberManager.allMembers()).thenReturn(Arrays.asList(selfMember, remoteMember));
        when(memberManager.getSelf()).thenReturn(selfMember);
        when(rpcClientProxy.sendRequest(eq(remoteMember), any())).thenReturn(null);
        
        Map<String, Boolean> result = pluginInnerHandler.getPluginAvailability("auth", "test");
        
        assertEquals(2, result.size());
        assertFalse(result.get("192.168.1.2:8848"));
    }
    
    @Test
    void testGetPluginAvailabilityWithRemoteException() throws NacosException {
        Member selfMember = new Member();
        selfMember.setIp("127.0.0.1");
        selfMember.setPort(8848);
        
        Member remoteMember = new Member();
        remoteMember.setIp("192.168.1.2");
        remoteMember.setPort(8848);
        remoteMember.setState(NodeState.UP);
        
        when(pluginManager.isPluginAvailable("auth:test")).thenReturn(true);
        when(memberManager.allMembers()).thenReturn(Arrays.asList(selfMember, remoteMember));
        when(memberManager.getSelf()).thenReturn(selfMember);
        when(rpcClientProxy.sendRequest(eq(remoteMember), any()))
            .thenThrow(new NacosException(500, "connection failed"));
        
        Map<String, Boolean> result = pluginInnerHandler.getPluginAvailability("auth", "test");
        
        assertEquals(2, result.size());
        assertFalse(result.get("192.168.1.2:8848"));
    }
    
    private PluginInfo createMockPluginInfo(String pluginId, PluginType type, String name,
        boolean enabled) {
        PluginInfo pluginInfo = new PluginInfo();
        pluginInfo.setPluginId(pluginId);
        pluginInfo.setPluginType(type);
        pluginInfo.setPluginName(name);
        pluginInfo.setEnabled(enabled);
        pluginInfo.setCritical(false);
        pluginInfo.setConfigurable(false);
        return pluginInfo;
    }
}
