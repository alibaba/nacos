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

package com.alibaba.nacos.config.server.service.listener;

import com.alibaba.nacos.api.config.model.ConfigListenerInfo;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.config.NacosAuthConfig;
import com.alibaba.nacos.auth.config.NacosAuthConfigHolder;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.service.notify.HttpClientManager;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.plugin.auth.constant.Constants.Auth;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RemoteConfigListenerStateServiceImplTest {
    
    @Mock
    private ServerMemberManager memberManager;
    
    @Mock
    private NacosRestTemplate nacosRestTemplate;
    
    private MockedStatic<HttpClientManager> httpClientManagerMockedStatic;
    
    private MockedStatic<NacosAuthConfigHolder> authConfigHolderMockedStatic;
    
    private NacosAuthConfigHolder nacosAuthConfigHolder;
    
    @Mock
    private NacosAuthConfig nacosAuthConfig;
    
    private RemoteConfigListenerStateServiceImpl service;
    
    private ConfigurableEnvironment cachedEnvironment;
    
    @BeforeEach
    void setUp() {
        cachedEnvironment = EnvUtil.getEnvironment();
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(Auth.NACOS_CORE_AUTH_ENABLED, "false");
        environment.setProperty(Auth.NACOS_CORE_AUTH_ADMIN_ENABLED, "false");
        EnvUtil.setEnvironment(environment);
        EnvUtil.setContextPath("/nacos");
        nacosAuthConfigHolder = Mockito.mock(NacosAuthConfigHolder.class);
        httpClientManagerMockedStatic =
            Mockito.mockStatic(HttpClientManager.class);
        httpClientManagerMockedStatic
            .when(HttpClientManager::getNacosRestTemplate)
            .thenReturn(nacosRestTemplate);
        authConfigHolderMockedStatic =
            Mockito.mockStatic(NacosAuthConfigHolder.class);
        authConfigHolderMockedStatic
            .when(NacosAuthConfigHolder::getInstance)
            .thenReturn(nacosAuthConfigHolder);
        when(nacosAuthConfigHolder.getNacosAuthConfigByScope(anyString()))
            .thenReturn(nacosAuthConfig);
        service = new RemoteConfigListenerStateServiceImpl(memberManager);
    }
    
    @AfterEach
    void tearDown() {
        if (httpClientManagerMockedStatic != null) {
            httpClientManagerMockedStatic.close();
        }
        if (authConfigHolderMockedStatic != null) {
            authConfigHolderMockedStatic.close();
        }
        EnvUtil.setContextPath(null);
        EnvUtil.setEnvironment(cachedEnvironment);
    }
    
    @Test
    void testGetListenerStateWithNoMembers() {
        when(memberManager.allMembersWithoutSelf())
            .thenReturn(Collections.emptyList());
        ConfigListenerInfo result =
            service.getListenerState("d", "g", "ns");
        assertNotNull(result);
        assertEquals(ConfigListenerInfo.QUERY_TYPE_CONFIG,
            result.getQueryType());
        assertEquals(0, result.getListenersStatus().size());
    }
    
    @Test
    void testGetListenerStateWithMember() throws Exception {
        Member member = new Member();
        member.setIp("10.0.0.1");
        member.setPort(8848);
        List<Member> members = new ArrayList<>();
        members.add(member);
        when(memberManager.allMembersWithoutSelf()).thenReturn(members);
        
        ConfigListenerInfo info = new ConfigListenerInfo();
        info.setListenersStatus(new HashMap<>());
        info.getListenersStatus().put("1.2.3.4", "md5abc");
        Result<ConfigListenerInfo> apiResult = Result.success(info);
        String json = JacksonUtils.toJson(apiResult);
        HttpRestResult<String> restResult = new HttpRestResult<>();
        restResult.setCode(200);
        restResult.setData(json);
        doReturn(restResult).when(nacosRestTemplate)
            .get(anyString(), any(), any(), eq(String.class));
        
        ConfigListenerInfo result =
            service.getListenerState("d", "g", "ns");
        assertNotNull(result);
        assertEquals("md5abc", result.getListenersStatus().get("1.2.3.4"));
    }
    
    @Test
    void testGetListenerStateWithFailedResponse() throws Exception {
        Member member = new Member();
        member.setIp("10.0.0.1");
        member.setPort(8848);
        List<Member> members = new ArrayList<>();
        members.add(member);
        when(memberManager.allMembersWithoutSelf()).thenReturn(members);
        
        HttpRestResult<String> restResult = new HttpRestResult<>();
        restResult.setCode(500);
        restResult.setMessage("error");
        doReturn(restResult).when(nacosRestTemplate)
            .get(anyString(), any(), any(), eq(String.class));
        
        ConfigListenerInfo result =
            service.getListenerState("d", "g", "ns");
        assertNotNull(result);
        assertEquals(0, result.getListenersStatus().size());
    }
    
    @Test
    void testGetListenerStateWithException() throws Exception {
        Member member = new Member();
        member.setIp("10.0.0.1");
        member.setPort(8848);
        List<Member> members = new ArrayList<>();
        members.add(member);
        when(memberManager.allMembersWithoutSelf()).thenReturn(members);
        
        doThrow(new RuntimeException("connect error")).when(nacosRestTemplate)
            .get(anyString(), any(), any(), eq(String.class));
        
        ConfigListenerInfo result =
            service.getListenerState("d", "g", "ns");
        assertNotNull(result);
        assertEquals(0, result.getListenersStatus().size());
    }
    
    @Test
    void testGetListenerStateByIpWithNoMembers() {
        when(memberManager.allMembersWithoutSelf())
            .thenReturn(Collections.emptyList());
        ConfigListenerInfo result =
            service.getListenerStateByIp("1.2.3.4");
        assertNotNull(result);
        assertEquals(ConfigListenerInfo.QUERY_TYPE_IP,
            result.getQueryType());
    }
    
    @Test
    void testGetListenerStateByIpWithMember() throws Exception {
        Member member = new Member();
        member.setIp("10.0.0.1");
        member.setPort(8848);
        List<Member> members = new ArrayList<>();
        members.add(member);
        when(memberManager.allMembersWithoutSelf()).thenReturn(members);
        
        ConfigListenerInfo info = new ConfigListenerInfo();
        info.setListenersStatus(new HashMap<>());
        info.getListenersStatus().put("gk1", "md5val");
        Result<ConfigListenerInfo> apiResult = Result.success(info);
        String json = JacksonUtils.toJson(apiResult);
        HttpRestResult<String> restResult = new HttpRestResult<>();
        restResult.setCode(200);
        restResult.setData(json);
        doReturn(restResult).when(nacosRestTemplate)
            .get(anyString(), any(), any(), eq(String.class));
        
        ConfigListenerInfo result =
            service.getListenerStateByIp("1.2.3.4");
        assertNotNull(result);
        assertEquals("md5val", result.getListenersStatus().get("gk1"));
    }
}
