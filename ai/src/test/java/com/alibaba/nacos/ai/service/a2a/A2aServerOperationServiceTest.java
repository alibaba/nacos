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
 *
 */

package com.alibaba.nacos.ai.service.a2a;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.service.SyncEffectService;
import com.alibaba.nacos.ai.service.a2a.identity.AgentIdCodecHolder;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardVersionInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentProvider;
import com.alibaba.nacos.api.ai.model.a2a.AgentVersionDetail;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.exception.ConfigAlreadyExistsException;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.service.ConfigDetailService;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import com.alibaba.nacos.naming.core.v2.index.ServiceStorage;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for A2aServerOperationServiceTest.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class A2aServerOperationServiceTest {
    
    private static final String TEST_NAMESPACE_ID = "test-namespace";
    
    private static final String TEST_AGENT_NAME = "test-agent";
    
    private static final String TEST_AGENT_VERSION = "1.0.0";
    
    private static final String TEST_REGISTRATION_TYPE = "service";
    
    private static final String ENCODED_AGENT_NAME = "encoded-test-agent";
    
    private static final String ENCODED_AGENT_NAME_WITH_VERSION = ENCODED_AGENT_NAME + "-" + TEST_AGENT_VERSION;
    
    @Mock
    private ConfigQueryChainService configQueryChainService;
    
    @Mock
    private ConfigOperationService configOperationService;
    
    @Mock
    private ConfigDetailService configDetailService;
    
    @Mock
    private SyncEffectService syncEffectService;
    
    @Mock
    private AgentIdCodecHolder agentIdCodecHolder;
    
    @Mock
    private ServiceStorage serviceStorage;
    
    private A2aServerOperationService a2aServerOperationService;
    
    @BeforeEach
    void setUp() {
        a2aServerOperationService = new A2aServerOperationService(configQueryChainService, configOperationService,
                configDetailService, syncEffectService, serviceStorage, agentIdCodecHolder);
        
        when(agentIdCodecHolder.encode(anyString())).thenReturn(ENCODED_AGENT_NAME);
        when(agentIdCodecHolder.encodeForSearch(anyString())).thenReturn(ENCODED_AGENT_NAME);
    }
    
    @Test
    void testRegisterAgentSuccess() throws NacosException {
        final AgentCard agentCard = buildTestAgentCard();
        
        ConfigQueryChainResponse response = mock(ConfigQueryChainResponse.class);
        when(response.getStatus()).thenReturn(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND);
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(response);
        
        when(configOperationService.publishConfig(any(ConfigForm.class), any(ConfigRequestInfo.class), eq(null))).thenReturn(true);
        doNothing().when(syncEffectService).toSync(any(ConfigForm.class), anyLong());
        
        a2aServerOperationService.registerAgent(agentCard, TEST_NAMESPACE_ID, TEST_REGISTRATION_TYPE);
        
        verify(configOperationService, times(2)).publishConfig(any(ConfigForm.class), any(ConfigRequestInfo.class), eq(null));
        verify(syncEffectService, times(1)).toSync(any(ConfigForm.class), anyLong());
    }
    
    @Test
    void testRegisterAgentAlreadyExists() throws NacosException {
        AgentCard agentCard = buildTestAgentCard();
        
        ConfigQueryChainResponse response = mock(ConfigQueryChainResponse.class);
        when(response.getStatus()).thenReturn(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND);
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(response);
        
        when(configOperationService.publishConfig(any(ConfigForm.class), any(ConfigRequestInfo.class), eq(null)))
                .thenThrow(new ConfigAlreadyExistsException("Config already exists"));
        
        NacosApiException exception = assertThrows(NacosApiException.class, () -> {
            a2aServerOperationService.registerAgent(agentCard, TEST_NAMESPACE_ID, TEST_REGISTRATION_TYPE);
        });
        
        assertEquals(NacosException.CONFLICT, exception.getErrCode());
        assertEquals(ErrorCode.RESOURCE_CONFLICT.getCode(), exception.getDetailErrCode());
    }
    
    @Test
    void testDeleteAgentSuccess() throws NacosException {
        AgentCardVersionInfo versionInfo = buildTestAgentCardVersionInfo();
        ConfigQueryChainResponse response = mock(ConfigQueryChainResponse.class);
        
        when(response.getStatus()).thenReturn(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        when(response.getContent()).thenReturn(JacksonUtils.toJson(versionInfo));
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(response);
        
        when(configOperationService.deleteConfig(eq(ENCODED_AGENT_NAME_WITH_VERSION),
                eq(Constants.A2A.AGENT_VERSION_GROUP), eq(TEST_NAMESPACE_ID), eq(null), eq(null), eq("nacos"), eq(null))).thenReturn(true);
        
        a2aServerOperationService.deleteAgent(TEST_NAMESPACE_ID, TEST_AGENT_NAME, TEST_AGENT_VERSION);
        
        verify(configOperationService, times(1)).deleteConfig(eq(ENCODED_AGENT_NAME_WITH_VERSION),
                eq(Constants.A2A.AGENT_VERSION_GROUP), eq(TEST_NAMESPACE_ID), eq(null), eq(null), eq("nacos"), eq(null));
    }
    
    @Test
    void testDeleteAgentWhenAgentNotFound() throws NacosException {
        ConfigQueryChainResponse response = mock(ConfigQueryChainResponse.class);
        when(response.getStatus()).thenReturn(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND);
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(response);
        
        a2aServerOperationService.deleteAgent(TEST_NAMESPACE_ID, TEST_AGENT_NAME, TEST_AGENT_VERSION);
        
        verify(configOperationService, times(0)).deleteConfig(anyString(), anyString(), anyString(), any(), any(),
                anyString(), any());
    }
    
    @Test
    void testUpdateAgentCardSuccess() throws NacosException {
        final AgentCard agentCard = buildTestAgentCard();
        
        ConfigQueryChainResponse versionResponse = mock(ConfigQueryChainResponse.class);
        when(versionResponse.getStatus()).thenReturn(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        when(versionResponse.getContent()).thenReturn(JacksonUtils.toJson(buildTestAgentCardVersionInfo()));
        
        ConfigQueryChainResponse detailResponse = mock(ConfigQueryChainResponse.class);
        when(detailResponse.getStatus()).thenReturn(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        when(detailResponse.getContent()).thenReturn(JacksonUtils.toJson(buildTestAgentCardDetailInfo()));
        
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
                .thenReturn(versionResponse)
                .thenReturn(detailResponse);
        
        when(configOperationService.publishConfig(any(ConfigForm.class), any(ConfigRequestInfo.class), eq(null))).thenReturn(true);
        doNothing().when(syncEffectService).toSync(any(ConfigForm.class), anyLong());
        
        a2aServerOperationService.updateAgentCard(agentCard, TEST_NAMESPACE_ID, TEST_REGISTRATION_TYPE, true);
        
        verify(configOperationService, times(2)).publishConfig(any(ConfigForm.class), any(ConfigRequestInfo.class), eq(null));
        verify(syncEffectService, times(1)).toSync(any(ConfigForm.class), anyLong());
    }
    
    @Test
    void testListAgentsSuccess() throws NacosException {
        Page<ConfigInfo> configPage = new Page<>();
        ConfigInfo configInfo = new ConfigInfo();
        AgentCardVersionInfo versionInfo = buildTestAgentCardVersionInfo();
        configInfo.setContent(JacksonUtils.toJson(versionInfo));
        configPage.setPageItems(Collections.singletonList(configInfo));
        configPage.setTotalCount(1);
        
        when(configDetailService.findConfigInfoPage(eq(Constants.A2A.SEARCH_BLUR), eq(1), eq(10), anyString(),
                eq(Constants.A2A.AGENT_GROUP), eq(TEST_NAMESPACE_ID), eq(null))).thenReturn(configPage);
        
        Page<AgentCardVersionInfo> result = a2aServerOperationService.listAgents(TEST_NAMESPACE_ID, TEST_AGENT_NAME,
                Constants.A2A.SEARCH_BLUR, 1, 10);
        
        assertNotNull(result);
        assertEquals(1, result.getTotalCount());
        assertEquals(1, result.getPageItems().size());
    }
    
    @Test
    void testListAgentVersionsSuccess() throws NacosApiException {
        AgentCardVersionInfo versionInfo = buildTestAgentCardVersionInfo();
        ConfigQueryChainResponse response = mock(ConfigQueryChainResponse.class);
        when(response.getStatus()).thenReturn(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        when(response.getContent()).thenReturn(JacksonUtils.toJson(versionInfo));
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(response);
        
        List<AgentVersionDetail> result = a2aServerOperationService.listAgentVersions(TEST_NAMESPACE_ID, TEST_AGENT_NAME);
        
        assertNotNull(result);
        assertEquals(1, result.size());
    }
    
    @Test
    void testGetAgentCardSuccess() throws NacosApiException {
        AgentCardVersionInfo versionInfo = buildTestAgentCardVersionInfo();
        AgentCardDetailInfo detailInfo = buildTestAgentCardDetailInfo();
        
        ConfigQueryChainResponse versionResponse = mock(ConfigQueryChainResponse.class);
        when(versionResponse.getStatus()).thenReturn(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        when(versionResponse.getContent()).thenReturn(JacksonUtils.toJson(versionInfo));
        
        ConfigQueryChainResponse detailResponse = mock(ConfigQueryChainResponse.class);
        when(detailResponse.getStatus()).thenReturn(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        when(detailResponse.getContent()).thenReturn(JacksonUtils.toJson(detailInfo));
        
        // Mock service storage for endpoint injection
        Service service = Service.newService(TEST_NAMESPACE_ID, Constants.A2A.AGENT_ENDPOINT_GROUP, 
                ENCODED_AGENT_NAME + "::" + TEST_AGENT_VERSION);
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.setHosts(Collections.emptyList());
        when(serviceStorage.getData(service)).thenReturn(serviceInfo);
        
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
                .thenReturn(versionResponse)
                .thenReturn(detailResponse);
        
        AgentCardDetailInfo result = a2aServerOperationService.getAgentCard(TEST_NAMESPACE_ID, TEST_AGENT_NAME,
                TEST_AGENT_VERSION, TEST_REGISTRATION_TYPE);
        
        assertNotNull(result);
        assertEquals(TEST_AGENT_NAME, result.getName());
        assertEquals(TEST_AGENT_VERSION, result.getVersion());
    }
    
    @Test
    void testGetAgentCardNotFound() throws NacosApiException {
        AgentCardVersionInfo versionInfo = buildTestAgentCardVersionInfo();
        
        ConfigQueryChainResponse versionResponse = mock(ConfigQueryChainResponse.class);
        when(versionResponse.getStatus()).thenReturn(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        when(versionResponse.getContent()).thenReturn(JacksonUtils.toJson(versionInfo));
        
        ConfigQueryChainResponse detailResponse = mock(ConfigQueryChainResponse.class);
        when(detailResponse.getStatus()).thenReturn(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND);
        
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
                .thenReturn(versionResponse)
                .thenReturn(detailResponse);
        
        NacosApiException exception = assertThrows(NacosApiException.class, () -> {
            a2aServerOperationService.getAgentCard(TEST_NAMESPACE_ID, TEST_AGENT_NAME, TEST_AGENT_VERSION,
                    TEST_REGISTRATION_TYPE);
        });
        
        assertEquals(NacosException.NOT_FOUND, exception.getErrCode());
        assertEquals(ErrorCode.AGENT_VERSION_NOT_FOUND.getCode(), exception.getDetailErrCode());
    }
    
    private AgentCard buildTestAgentCard() {
        AgentCard agentCard = new AgentCard();
        agentCard.setName(TEST_AGENT_NAME);
        agentCard.setVersion(TEST_AGENT_VERSION);
        agentCard.setDescription("Test Agent Description");
        AgentProvider agentProvider = new AgentProvider();
        agentProvider.setOrganization("Test Organization");
        agentCard.setProvider(agentProvider);
        agentCard.setPreferredTransport("http");
        return agentCard;
    }
    
    private AgentCardDetailInfo buildTestAgentCardDetailInfo() {
        AgentCardDetailInfo detailInfo = new AgentCardDetailInfo();
        detailInfo.setName(TEST_AGENT_NAME);
        detailInfo.setVersion(TEST_AGENT_VERSION);
        detailInfo.setDescription("Test Agent Description");
        AgentProvider agentProvider = new AgentProvider();
        agentProvider.setOrganization("Test Organization");
        detailInfo.setProvider(agentProvider);
        detailInfo.setRegistrationType(TEST_REGISTRATION_TYPE);
        return detailInfo;
    }
    
    private AgentCardVersionInfo buildTestAgentCardVersionInfo() {
        AgentCardVersionInfo versionInfo = new AgentCardVersionInfo();
        versionInfo.setName(TEST_AGENT_NAME);
        versionInfo.setVersion(TEST_AGENT_VERSION);
        versionInfo.setLatestPublishedVersion(TEST_AGENT_VERSION);
        versionInfo.setRegistrationType(TEST_REGISTRATION_TYPE);
        
        AgentVersionDetail versionDetail = new AgentVersionDetail();
        versionDetail.setVersion(TEST_AGENT_VERSION);
        versionDetail.setLatest(true);
        versionInfo.setVersionDetails(Collections.singletonList(versionDetail));
        
        return versionInfo;
    }
}