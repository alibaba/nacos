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

package com.alibaba.nacos.console.handler.impl.remote;

import com.alibaba.nacos.api.common.NodeState;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.api.model.response.NacosMember;
import com.alibaba.nacos.auth.config.NacosAuthConfig;
import com.alibaba.nacos.auth.config.NacosAuthConfigHolder;
import com.alibaba.nacos.console.config.NacosConsoleAuthConfig;
import com.alibaba.nacos.console.handler.core.ClusterHandler;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.NacosMemberManager;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RemoteServerConnectorTest {
    
    @Mock
    NacosMemberManager memberManager;
    
    @Mock
    ClusterHandler clusterHandler;
    
    @Mock
    NacosConsoleAuthConfig mockNacosAuthConfig;
    
    NacosAuthConfig cachedConsoleAuthConfig;
    
    RemoteServerConnector remoteServerConnector;
    
    private ConfigurableEnvironment cachedEnvironment;
    
    @BeforeEach
    void setUp() {
        cachedEnvironment = EnvUtil.getEnvironment();
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("nacos.core.auth.admin.enabled", "false");
        EnvUtil.setEnvironment(environment);
        remoteServerConnector = new RemoteServerConnector(memberManager, clusterHandler);
        cachedConsoleAuthConfig = NacosAuthConfigHolder.getInstance()
            .getNacosAuthConfigByScope(NacosConsoleAuthConfig.NACOS_CONSOLE_AUTH_SCOPE);
        Map<String, NacosAuthConfig> nacosAuthConfigMap =
            (Map<String, NacosAuthConfig>) ReflectionTestUtils.getField(
                NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap");
        nacosAuthConfigMap.put(NacosConsoleAuthConfig.NACOS_CONSOLE_AUTH_SCOPE,
            mockNacosAuthConfig);
    }
    
    @AfterEach
    void tearDown() {
        Map<String, NacosAuthConfig> nacosAuthConfigMap =
            (Map<String, NacosAuthConfig>) ReflectionTestUtils.getField(
                NacosAuthConfigHolder.getInstance(), "nacosAuthConfigMap");
        nacosAuthConfigMap.put(NacosConsoleAuthConfig.NACOS_CONSOLE_AUTH_SCOPE,
            cachedConsoleAuthConfig);
        EnvUtil.setEnvironment(cachedEnvironment);
    }
    
    @Test
    void testGetServerContextPathDefault() {
        String contextPath = remoteServerConnector.getServerContextPath();
        assertEquals("/nacos", contextPath);
    }
    
    @Test
    void testGetServerContextPathCustom() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("nacos.console.remote.server.context-path", "/custom");
        EnvUtil.setEnvironment(environment);
        String contextPath = remoteServerConnector.getServerContextPath();
        assertEquals("/custom", contextPath);
    }
    
    @Test
    void testAddAuthIdentityWithNoAuthConfig() {
        when(mockNacosAuthConfig.getServerIdentityKey()).thenReturn("");
        HttpPost httpPost = new HttpPost("http://localhost:8080/test");
        remoteServerConnector.addAuthIdentity(httpPost);
        assertNull(httpPost.getFirstHeader("serverIdentity"));
    }
    
    @Test
    void testAddAuthIdentityWithAuthConfig() {
        when(mockNacosAuthConfig.getServerIdentityKey()).thenReturn("testKey");
        when(mockNacosAuthConfig.getServerIdentityValue()).thenReturn("testValue");
        HttpPost httpPost = new HttpPost("http://localhost:8080/test");
        remoteServerConnector.addAuthIdentity(httpPost);
        assertNotNull(httpPost.getFirstHeader("testKey"));
        assertEquals("testValue", httpPost.getFirstHeader("testKey").getValue());
    }
    
    @Test
    void testRandomOneHealthyMember() throws NacosException {
        Member member = new Member();
        member.setIp("127.0.0.1");
        member.setPort(8080);
        member.setState(NodeState.UP);
        Collection<Member> allMembers = new ArrayList<>();
        allMembers.add(member);
        when(memberManager.allMembers()).thenReturn(allMembers);
        NacosMember nacosMember = new NacosMember();
        nacosMember.setAddress("127.0.0.1:8080");
        nacosMember.setState(NodeState.UP);
        doReturn(Collections.singletonList(nacosMember)).when(clusterHandler).getNodeList("");
        Member result = remoteServerConnector.randomOneHealthyMember();
        assertNotNull(result);
        assertEquals("127.0.0.1:8080", result.getAddress());
    }
    
    @Test
    void testRandomOneHealthyMemberNoHealthyNode() throws NacosException {
        Member member = new Member();
        member.setIp("127.0.0.1");
        member.setPort(8080);
        member.setState(NodeState.DOWN);
        Collection<Member> allMembers = new ArrayList<>();
        allMembers.add(member);
        when(memberManager.allMembers()).thenReturn(allMembers);
        NacosMember nacosMember = new NacosMember();
        nacosMember.setAddress("127.0.0.1:8080");
        nacosMember.setState(NodeState.DOWN);
        doReturn(Collections.singletonList(nacosMember)).when(clusterHandler).getNodeList("");
        assertThrows(NacosRuntimeException.class,
            () -> remoteServerConnector.randomOneHealthyMember());
    }
    
    @Test
    void testRandomOneHealthyMemberEmptyCluster() throws NacosException {
        Collection<Member> allMembers = new ArrayList<>();
        when(memberManager.allMembers()).thenReturn(allMembers);
        doReturn(Collections.emptyList()).when(clusterHandler).getNodeList("");
        assertThrows(NacosRuntimeException.class,
            () -> remoteServerConnector.randomOneHealthyMember());
    }
    
    @Test
    void testRandomOneHealthyMemberFiltersUnhealthy() throws NacosException {
        Member healthyMember = new Member();
        healthyMember.setIp("127.0.0.1");
        healthyMember.setPort(8080);
        healthyMember.setState(NodeState.UP);
        Member unhealthyMember = new Member();
        unhealthyMember.setIp("127.0.0.2");
        unhealthyMember.setPort(8080);
        unhealthyMember.setState(NodeState.DOWN);
        Collection<Member> allMembers = new ArrayList<>();
        allMembers.add(healthyMember);
        allMembers.add(unhealthyMember);
        when(memberManager.allMembers()).thenReturn(allMembers);
        NacosMember nacosMemberUp = new NacosMember();
        nacosMemberUp.setAddress("127.0.0.1:8080");
        nacosMemberUp.setState(NodeState.UP);
        NacosMember nacosMemberDown = new NacosMember();
        nacosMemberDown.setAddress("127.0.0.2:8080");
        nacosMemberDown.setState(NodeState.DOWN);
        doReturn(java.util.List.of(nacosMemberUp, nacosMemberDown)).when(clusterHandler)
            .getNodeList("");
        Member result = remoteServerConnector.randomOneHealthyMember();
        assertNotNull(result);
        assertEquals("127.0.0.1:8080", result.getAddress());
    }
}
