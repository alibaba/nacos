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
import com.alibaba.nacos.api.ai.constant.AiConstants;
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
import java.util.LinkedList;
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
        
        when(configOperationService.publishConfig(any(ConfigForm.class), any(ConfigRequestInfo.class),
                eq(null))).thenReturn(true);
        doNothing().when(syncEffectService).toSync(any(ConfigForm.class), anyLong());
        
        a2aServerOperationService.registerAgent(agentCard, TEST_NAMESPACE_ID, TEST_REGISTRATION_TYPE);
        
        verify(configOperationService, times(2)).publishConfig(any(ConfigForm.class), any(ConfigRequestInfo.class),
                eq(null));
        verify(syncEffectService, times(1)).toSync(any(ConfigForm.class), anyLong());
    }
    
    @Test
    void testRegisterAgentAlreadyExists() throws NacosException {
        AgentCard agentCard = buildTestAgentCard();
        
        ConfigQueryChainResponse response = mock(ConfigQueryChainResponse.class);
        when(response.getStatus()).thenReturn(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND);
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(response);
        
        when(configOperationService.publishConfig(any(ConfigForm.class), any(ConfigRequestInfo.class),
                eq(null))).thenThrow(new ConfigAlreadyExistsException("Config already exists"));
        
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
                eq(Constants.A2A.AGENT_VERSION_GROUP), eq(TEST_NAMESPACE_ID), eq(null), eq(null), eq("nacos"),
                eq(null))).thenReturn(true);
        
        a2aServerOperationService.deleteAgent(TEST_NAMESPACE_ID, TEST_AGENT_NAME, TEST_AGENT_VERSION);
        
        verify(configOperationService, times(1)).deleteConfig(eq(ENCODED_AGENT_NAME_WITH_VERSION),
                eq(Constants.A2A.AGENT_VERSION_GROUP), eq(TEST_NAMESPACE_ID), eq(null), eq(null), eq("nacos"),
                eq(null));
    }
    
    @Test
    void testDeleteAgentDeleteAllVersions() throws NacosException {
        AgentCardVersionInfo versionInfo = buildTestAgentCardVersionInfo();
        ConfigQueryChainResponse response = mock(ConfigQueryChainResponse.class);
        
        when(response.getStatus()).thenReturn(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        when(response.getContent()).thenReturn(JacksonUtils.toJson(versionInfo));
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(response);
        
        when(configOperationService.deleteConfig(anyString(), anyString(), anyString(), any(), any(), anyString(),
                any())).thenReturn(true);
        
        // Test deleting all versions (version param is null)
        a2aServerOperationService.deleteAgent(TEST_NAMESPACE_ID, TEST_AGENT_NAME, null);
        
        // Should delete both version-specific config and main config
        verify(configOperationService, times(2)).deleteConfig(anyString(), anyString(), anyString(), any(), any(),
                anyString(), any());
    }
    
    @Test
    void testDeleteAgentDeleteLastVersion() throws NacosException {
        AgentCardVersionInfo versionInfo = buildTestAgentCardVersionInfo();
        ConfigQueryChainResponse response = mock(ConfigQueryChainResponse.class);
        
        when(response.getStatus()).thenReturn(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        when(response.getContent()).thenReturn(JacksonUtils.toJson(versionInfo));
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(response);
        
        when(configOperationService.deleteConfig(eq(ENCODED_AGENT_NAME_WITH_VERSION),
                eq(Constants.A2A.AGENT_VERSION_GROUP), eq(TEST_NAMESPACE_ID), eq(null), eq(null), eq("nacos"),
                eq(null))).thenReturn(true);
        when(configOperationService.deleteConfig(eq(ENCODED_AGENT_NAME), eq(Constants.A2A.AGENT_GROUP),
                eq(TEST_NAMESPACE_ID), eq(null), eq(null), eq("nacos"), eq(null))).thenReturn(true);
        
        // Test deleting the last version - should also delete main config
        a2aServerOperationService.deleteAgent(TEST_NAMESPACE_ID, TEST_AGENT_NAME, TEST_AGENT_VERSION);
        
        verify(configOperationService).deleteConfig(eq(ENCODED_AGENT_NAME_WITH_VERSION),
                eq(Constants.A2A.AGENT_VERSION_GROUP), eq(TEST_NAMESPACE_ID), eq(null), eq(null), eq("nacos"),
                eq(null));
        verify(configOperationService).deleteConfig(eq(ENCODED_AGENT_NAME), eq(Constants.A2A.AGENT_GROUP),
                eq(TEST_NAMESPACE_ID), eq(null), eq(null), eq("nacos"), eq(null));
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
    void testDeleteAgentWithVersionNotFoundInVersionDetails() throws NacosException {
        AgentCardVersionInfo versionInfo = buildTestAgentCardVersionInfo();
        // Change the version in version details to something else
        versionInfo.getVersionDetails().get(0).setVersion("2.0.0");
        
        ConfigQueryChainResponse response = mock(ConfigQueryChainResponse.class);
        when(response.getStatus()).thenReturn(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        when(response.getContent()).thenReturn(JacksonUtils.toJson(versionInfo));
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(response);
        
        when(configOperationService.deleteConfig(anyString(), anyString(), anyString(), any(), any(), anyString(),
                any())).thenReturn(true);
        
        // Try to delete a version that doesn't exist in version details
        a2aServerOperationService.deleteAgent(TEST_NAMESPACE_ID, TEST_AGENT_NAME, TEST_AGENT_VERSION);
        
        // Should still delete the config file even if not in version details
        verify(configOperationService).deleteConfig(eq(ENCODED_AGENT_NAME_WITH_VERSION),
                eq(Constants.A2A.AGENT_VERSION_GROUP), eq(TEST_NAMESPACE_ID), any(), any(), anyString(), any());
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
        
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(versionResponse)
                .thenReturn(detailResponse);
        
        when(configOperationService.publishConfig(any(ConfigForm.class), any(ConfigRequestInfo.class),
                eq(null))).thenReturn(true);
        doNothing().when(syncEffectService).toSync(any(ConfigForm.class), anyLong());
        
        a2aServerOperationService.updateAgentCard(agentCard, TEST_NAMESPACE_ID, TEST_REGISTRATION_TYPE, true);
        
        verify(configOperationService, times(2)).publishConfig(any(ConfigForm.class), any(ConfigRequestInfo.class),
                eq(null));
        verify(syncEffectService, times(1)).toSync(any(ConfigForm.class), anyLong());
    }
    
    @Test
    void testUpdateAgentCardWithExistingVersion() throws NacosException {
        final AgentCard agentCard = buildTestAgentCard();
        AgentCardVersionInfo existingVersionInfo = buildTestAgentCardVersionInfo();
        
        ConfigQueryChainResponse versionResponse = mock(ConfigQueryChainResponse.class);
        when(versionResponse.getStatus()).thenReturn(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        when(versionResponse.getContent()).thenReturn(JacksonUtils.toJson(existingVersionInfo));
        
        ConfigQueryChainResponse detailResponse = mock(ConfigQueryChainResponse.class);
        when(detailResponse.getStatus()).thenReturn(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        when(detailResponse.getContent()).thenReturn(JacksonUtils.toJson(buildTestAgentCardDetailInfo()));
        
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(versionResponse)
                .thenReturn(detailResponse);
        
        when(configOperationService.publishConfig(any(ConfigForm.class), any(ConfigRequestInfo.class),
                eq(null))).thenReturn(true);
        doNothing().when(syncEffectService).toSync(any(ConfigForm.class), anyLong());
        
        // Update with existing version
        a2aServerOperationService.updateAgentCard(agentCard, TEST_NAMESPACE_ID, TEST_REGISTRATION_TYPE, true);
        
        verify(configOperationService, times(2)).publishConfig(any(ConfigForm.class), any(ConfigRequestInfo.class),
                eq(null));
        verify(syncEffectService).toSync(any(ConfigForm.class), anyLong());
    }
    
    @Test
    void testUpdateAgentCardWithNewVersion() throws NacosException {
        final AgentCard agentCard = buildTestAgentCard();
        AgentCardVersionInfo existingVersionInfo = buildTestAgentCardVersionInfo();
        // Modify version to be different from agent card
        existingVersionInfo.getVersionDetails().get(0).setVersion("0.9.0");
        
        ConfigQueryChainResponse versionResponse = mock(ConfigQueryChainResponse.class);
        when(versionResponse.getStatus()).thenReturn(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        when(versionResponse.getContent()).thenReturn(JacksonUtils.toJson(existingVersionInfo));
        
        ConfigQueryChainResponse detailResponse = mock(ConfigQueryChainResponse.class);
        when(detailResponse.getStatus()).thenReturn(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        when(detailResponse.getContent()).thenReturn(JacksonUtils.toJson(buildTestAgentCardDetailInfo()));
        
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(versionResponse)
                .thenReturn(detailResponse);
        
        when(configOperationService.publishConfig(any(ConfigForm.class), any(ConfigRequestInfo.class),
                eq(null))).thenReturn(true);
        doNothing().when(syncEffectService).toSync(any(ConfigForm.class), anyLong());
        
        // Update with new version - should add to version list
        a2aServerOperationService.updateAgentCard(agentCard, TEST_NAMESPACE_ID, TEST_REGISTRATION_TYPE, true);
        
        verify(configOperationService, times(2)).publishConfig(any(ConfigForm.class), any(ConfigRequestInfo.class),
                eq(null));
        verify(syncEffectService).toSync(any(ConfigForm.class), anyLong());
    }
    
    @Test
    void testUpdateAgentCardWithoutRegistrationType() throws NacosException {
        final AgentCard agentCard = buildTestAgentCard();
        AgentCardVersionInfo existingVersionInfo = buildTestAgentCardVersionInfo();
        
        ConfigQueryChainResponse versionResponse = mock(ConfigQueryChainResponse.class);
        when(versionResponse.getStatus()).thenReturn(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        when(versionResponse.getContent()).thenReturn(JacksonUtils.toJson(existingVersionInfo));
        
        ConfigQueryChainResponse detailResponse = mock(ConfigQueryChainResponse.class);
        when(detailResponse.getStatus()).thenReturn(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        when(detailResponse.getContent()).thenReturn(JacksonUtils.toJson(buildTestAgentCardDetailInfo()));
        
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(versionResponse)
                .thenReturn(detailResponse);
        
        when(configOperationService.publishConfig(any(ConfigForm.class), any(ConfigRequestInfo.class),
                eq(null))).thenReturn(true);
        doNothing().when(syncEffectService).toSync(any(ConfigForm.class), anyLong());
        
        // Update without registration type - should use existing one
        a2aServerOperationService.updateAgentCard(agentCard, TEST_NAMESPACE_ID, null, false);
        
        verify(configOperationService, times(2)).publishConfig(any(ConfigForm.class), any(ConfigRequestInfo.class),
                eq(null));
        verify(syncEffectService).toSync(any(ConfigForm.class), anyLong());
    }
    
    @Test
    void testUpdateAgentCardNotSetAsLatest() throws NacosException {
        final AgentCard agentCard = buildTestAgentCard();
        
        ConfigQueryChainResponse versionResponse = mock(ConfigQueryChainResponse.class);
        when(versionResponse.getStatus()).thenReturn(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        when(versionResponse.getContent()).thenReturn(JacksonUtils.toJson(buildTestAgentCardVersionInfo()));
        
        ConfigQueryChainResponse detailResponse = mock(ConfigQueryChainResponse.class);
        when(detailResponse.getStatus()).thenReturn(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        when(detailResponse.getContent()).thenReturn(JacksonUtils.toJson(buildTestAgentCardDetailInfo()));
        
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(versionResponse)
                .thenReturn(detailResponse);
        
        when(configOperationService.publishConfig(any(ConfigForm.class), any(ConfigRequestInfo.class),
                eq(null))).thenReturn(true);
        doNothing().when(syncEffectService).toSync(any(ConfigForm.class), anyLong());
        
        // Update without setting as latest
        a2aServerOperationService.updateAgentCard(agentCard, TEST_NAMESPACE_ID, TEST_REGISTRATION_TYPE, false);
        
        verify(configOperationService, times(2)).publishConfig(any(ConfigForm.class), any(ConfigRequestInfo.class),
                eq(null));
        verify(syncEffectService, times(1)).toSync(any(ConfigForm.class), anyLong());
    }
    
    @Test
    void testUpdateAgentCardSetAsLatestWithMultipleVersions() throws NacosException {
        final AgentCard agentCard = buildTestAgentCard();
        AgentCardVersionInfo existingVersionInfo = buildTestAgentCardVersionInfo();
        List<AgentVersionDetail> newVersionDetail = new LinkedList<>(existingVersionInfo.getVersionDetails());
        
        // Add another version
        AgentVersionDetail anotherVersion = new AgentVersionDetail();
        anotherVersion.setVersion("0.9.0");
        anotherVersion.setLatest(false);
        newVersionDetail.add(anotherVersion);
        existingVersionInfo.setVersionDetails(newVersionDetail);
        
        ConfigQueryChainResponse versionResponse = mock(ConfigQueryChainResponse.class);
        when(versionResponse.getStatus()).thenReturn(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        when(versionResponse.getContent()).thenReturn(JacksonUtils.toJson(existingVersionInfo));
        
        ConfigQueryChainResponse detailResponse = mock(ConfigQueryChainResponse.class);
        when(detailResponse.getStatus()).thenReturn(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        when(detailResponse.getContent()).thenReturn(JacksonUtils.toJson(buildTestAgentCardDetailInfo()));
        
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(versionResponse)
                .thenReturn(detailResponse);
        
        when(configOperationService.publishConfig(any(ConfigForm.class), any(ConfigRequestInfo.class),
                eq(null))).thenReturn(true);
        doNothing().when(syncEffectService).toSync(any(ConfigForm.class), anyLong());
        
        // Update and set as latest - should update version details appropriately
        a2aServerOperationService.updateAgentCard(agentCard, TEST_NAMESPACE_ID, TEST_REGISTRATION_TYPE, true);
        
        verify(configOperationService, times(2)).publishConfig(any(ConfigForm.class), any(ConfigRequestInfo.class),
                eq(null));
        verify(syncEffectService).toSync(any(ConfigForm.class), anyLong());
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
    void testListAgentsAccurateSearch() throws NacosException {
        Page<ConfigInfo> configPage = new Page<>();
        ConfigInfo configInfo = new ConfigInfo();
        AgentCardVersionInfo versionInfo = buildTestAgentCardVersionInfo();
        configInfo.setContent(JacksonUtils.toJson(versionInfo));
        configPage.setPageItems(Collections.singletonList(configInfo));
        configPage.setTotalCount(1);
        
        when(configDetailService.findConfigInfoPage(eq(Constants.A2A.SEARCH_ACCURATE), eq(1), eq(10), anyString(),
                eq(Constants.A2A.AGENT_GROUP), eq(TEST_NAMESPACE_ID), eq(null))).thenReturn(configPage);
        
        Page<AgentCardVersionInfo> result = a2aServerOperationService.listAgents(TEST_NAMESPACE_ID, TEST_AGENT_NAME,
                Constants.A2A.SEARCH_ACCURATE, 1, 10);
        
        assertNotNull(result);
        assertEquals(1, result.getTotalCount());
        assertEquals(1, result.getPageItems().size());
    }
    
    @Test
    void testListAgentsEmptyAgentName() throws NacosException {
        Page<ConfigInfo> configPage = new Page<>();
        ConfigInfo configInfo = new ConfigInfo();
        AgentCardVersionInfo versionInfo = buildTestAgentCardVersionInfo();
        configInfo.setContent(JacksonUtils.toJson(versionInfo));
        configPage.setPageItems(Collections.singletonList(configInfo));
        configPage.setTotalCount(1);
        
        when(configDetailService.findConfigInfoPage(eq(Constants.A2A.SEARCH_BLUR), eq(1), eq(10), anyString(),
                eq(Constants.A2A.AGENT_GROUP), eq(TEST_NAMESPACE_ID), eq(null))).thenReturn(configPage);
        
        Page<AgentCardVersionInfo> result = a2aServerOperationService.listAgents(TEST_NAMESPACE_ID, null,
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
        
        List<AgentVersionDetail> result = a2aServerOperationService.listAgentVersions(TEST_NAMESPACE_ID,
                TEST_AGENT_NAME);
        
        assertNotNull(result);
        assertEquals(1, result.size());
    }
    
    @Test
    void testQueryAgentCardVersionInfoNotFound() throws NacosApiException {
        ConfigQueryChainResponse response = mock(ConfigQueryChainResponse.class);
        when(response.getStatus()).thenReturn(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND);
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(response);
        
        NacosApiException exception = assertThrows(NacosApiException.class, () -> {
            // This is a private method, but we can test it through public methods that use it
            a2aServerOperationService.listAgentVersions(TEST_NAMESPACE_ID, "non-existent-agent");
        });
        
        assertEquals(NacosException.NOT_FOUND, exception.getErrCode());
        assertEquals(ErrorCode.AGENT_NOT_FOUND.getCode(), exception.getDetailErrCode());
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
        
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(versionResponse)
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
        
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(versionResponse)
                .thenReturn(detailResponse);
        
        NacosApiException exception = assertThrows(NacosApiException.class, () -> {
            a2aServerOperationService.getAgentCard(TEST_NAMESPACE_ID, TEST_AGENT_NAME, TEST_AGENT_VERSION,
                    TEST_REGISTRATION_TYPE);
        });
        
        assertEquals(NacosException.NOT_FOUND, exception.getErrCode());
        assertEquals(ErrorCode.AGENT_VERSION_NOT_FOUND.getCode(), exception.getDetailErrCode());
    }
    
    @Test
    void testGetAgentCardLatestVersion() throws NacosApiException {
        AgentCardVersionInfo versionInfo = buildTestAgentCardVersionInfo();
        AgentCardDetailInfo detailInfo = buildTestAgentCardDetailInfo();
        detailInfo.setPreferredTransport(AiConstants.A2a.A2A_ENDPOINT_DEFAULT_TRANSPORT);
        
        ConfigQueryChainResponse versionResponse = mock(ConfigQueryChainResponse.class);
        when(versionResponse.getStatus()).thenReturn(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        when(versionResponse.getContent()).thenReturn(JacksonUtils.toJson(versionInfo));
        
        ConfigQueryChainResponse detailResponse = mock(ConfigQueryChainResponse.class);
        when(detailResponse.getStatus()).thenReturn(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        when(detailResponse.getContent()).thenReturn(JacksonUtils.toJson(detailInfo));
        
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(versionResponse)
                .thenReturn(detailResponse);
        
        final Service service = Service.newService(TEST_NAMESPACE_ID, Constants.A2A.AGENT_ENDPOINT_GROUP,
                ENCODED_AGENT_NAME + "::" + TEST_AGENT_VERSION);
        final ServiceInfo serviceInfo = new ServiceInfo();
        com.alibaba.nacos.api.naming.pojo.Instance instance = new com.alibaba.nacos.api.naming.pojo.Instance();
        instance.setIp("127.0.0.1");
        instance.setPort(8080);
        instance.getMetadata().put(Constants.A2A.AGENT_ENDPOINT_TRANSPORT_KEY, detailInfo.getPreferredTransport());
        serviceInfo.addHost(instance);
        when(serviceStorage.getData(service)).thenReturn(serviceInfo);
        
        // Get latest version (version param is null)
        AgentCardDetailInfo result = a2aServerOperationService.getAgentCard(TEST_NAMESPACE_ID, TEST_AGENT_NAME, null,
                TEST_REGISTRATION_TYPE);
        
        assertNotNull(result);
        assertEquals(TEST_AGENT_NAME, result.getName());
        assertEquals(TEST_AGENT_VERSION, result.getVersion());
        assertEquals(Boolean.TRUE, result.isLatestVersion());
    }
    
    @Test
    void testGetAgentCardLatestVersionNotFound() throws NacosApiException {
        AgentCardVersionInfo versionInfo = buildTestAgentCardVersionInfo();
        // No latest version
        versionInfo.getVersionDetails().get(0).setLatest(false);
        versionInfo.setLatestPublishedVersion(null);
        
        ConfigQueryChainResponse versionResponse = mock(ConfigQueryChainResponse.class);
        when(versionResponse.getStatus()).thenReturn(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        when(versionResponse.getContent()).thenReturn(JacksonUtils.toJson(versionInfo));
        
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(versionResponse);
        
        NacosApiException exception = assertThrows(NacosApiException.class, () -> {
            a2aServerOperationService.getAgentCard(TEST_NAMESPACE_ID, TEST_AGENT_NAME, null, TEST_REGISTRATION_TYPE);
        });
        
        assertEquals(NacosException.NOT_FOUND, exception.getErrCode());
        assertEquals(ErrorCode.AGENT_VERSION_NOT_FOUND.getCode(), exception.getDetailErrCode());
    }
    
    @Test
    void testGetAgentCardWithServiceRegistrationType() throws NacosApiException {
        AgentCardVersionInfo versionInfo = buildTestAgentCardVersionInfo();
        AgentCardDetailInfo detailInfo = buildTestAgentCardDetailInfo();
        detailInfo.setRegistrationType("service");
        
        ConfigQueryChainResponse versionResponse = mock(ConfigQueryChainResponse.class);
        when(versionResponse.getStatus()).thenReturn(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        when(versionResponse.getContent()).thenReturn(JacksonUtils.toJson(versionInfo));
        
        ConfigQueryChainResponse detailResponse = mock(ConfigQueryChainResponse.class);
        when(detailResponse.getStatus()).thenReturn(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        when(detailResponse.getContent()).thenReturn(JacksonUtils.toJson(detailInfo));
        
        Service service = Service.newService(TEST_NAMESPACE_ID, Constants.A2A.AGENT_ENDPOINT_GROUP,
                ENCODED_AGENT_NAME + "::" + TEST_AGENT_VERSION);
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.setHosts(Collections.emptyList());
        when(serviceStorage.getData(service)).thenReturn(serviceInfo);
        
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(versionResponse)
                .thenReturn(detailResponse);
        
        AgentCardDetailInfo result = a2aServerOperationService.getAgentCard(TEST_NAMESPACE_ID, TEST_AGENT_NAME,
                TEST_AGENT_VERSION, "service");
        
        assertNotNull(result);
        assertEquals(TEST_AGENT_NAME, result.getName());
        assertEquals(TEST_AGENT_VERSION, result.getVersion());
    }
    
    @Test
    void testGetAgentCardWithServiceEndpoints() throws NacosApiException {
        final AgentCardVersionInfo versionInfo = buildTestAgentCardVersionInfo();
        AgentCardDetailInfo detailInfo = buildTestAgentCardDetailInfo();
        detailInfo.setRegistrationType("service");
        detailInfo.setPreferredTransport(AiConstants.A2a.A2A_ENDPOINT_DEFAULT_TRANSPORT);
        
        ConfigQueryChainResponse versionResponse = mock(ConfigQueryChainResponse.class);
        when(versionResponse.getStatus()).thenReturn(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        when(versionResponse.getContent()).thenReturn(JacksonUtils.toJson(versionInfo));
        
        ConfigQueryChainResponse detailResponse = mock(ConfigQueryChainResponse.class);
        when(detailResponse.getStatus()).thenReturn(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        when(detailResponse.getContent()).thenReturn(JacksonUtils.toJson(detailInfo));
        
        final Service service = Service.newService(TEST_NAMESPACE_ID, Constants.A2A.AGENT_ENDPOINT_GROUP,
                ENCODED_AGENT_NAME + "::" + TEST_AGENT_VERSION);
        
        // Create a service info with hosts
        final ServiceInfo serviceInfo = new ServiceInfo();
        com.alibaba.nacos.api.naming.pojo.Instance instance = new com.alibaba.nacos.api.naming.pojo.Instance();
        instance.setIp("127.0.0.1");
        instance.setPort(8080);
        instance.getMetadata().put(Constants.A2A.AGENT_ENDPOINT_TRANSPORT_KEY, detailInfo.getPreferredTransport());
        serviceInfo.addHost(instance);
        when(serviceStorage.getData(service)).thenReturn(serviceInfo);
        
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(versionResponse)
                .thenReturn(detailResponse);
        
        AgentCardDetailInfo result = a2aServerOperationService.getAgentCard(TEST_NAMESPACE_ID, TEST_AGENT_NAME,
                TEST_AGENT_VERSION, "service");
        
        assertNotNull(result);
        assertEquals(TEST_AGENT_NAME, result.getName());
        assertEquals(TEST_AGENT_VERSION, result.getVersion());
    }
    
    @Test
    void testGetAgentCardWithEmptyRegistrationType() throws NacosApiException {
        AgentCardVersionInfo versionInfo = buildTestAgentCardVersionInfo();
        AgentCardDetailInfo detailInfo = buildTestAgentCardDetailInfo();
        
        ConfigQueryChainResponse versionResponse = mock(ConfigQueryChainResponse.class);
        when(versionResponse.getStatus()).thenReturn(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        when(versionResponse.getContent()).thenReturn(JacksonUtils.toJson(versionInfo));
        
        ConfigQueryChainResponse detailResponse = mock(ConfigQueryChainResponse.class);
        when(detailResponse.getStatus()).thenReturn(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        when(detailResponse.getContent()).thenReturn(JacksonUtils.toJson(detailInfo));
        
        Service service = Service.newService(TEST_NAMESPACE_ID, Constants.A2A.AGENT_ENDPOINT_GROUP,
                ENCODED_AGENT_NAME + "::" + TEST_AGENT_VERSION);
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.setHosts(Collections.emptyList());
        when(serviceStorage.getData(service)).thenReturn(serviceInfo);
        
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(versionResponse)
                .thenReturn(detailResponse);
        
        // Pass empty registration type - should use the one from detailInfo
        AgentCardDetailInfo result = a2aServerOperationService.getAgentCard(TEST_NAMESPACE_ID, TEST_AGENT_NAME,
                TEST_AGENT_VERSION, null);
        
        assertNotNull(result);
        assertEquals(TEST_AGENT_NAME, result.getName());
        assertEquals(TEST_AGENT_VERSION, result.getVersion());
    }
    
    @Test
    void testListAgentsWithNoAgentNameProvided() throws NacosException {
        Page<ConfigInfo> configPage = new Page<>();
        ConfigInfo configInfo = new ConfigInfo();
        AgentCardVersionInfo versionInfo = buildTestAgentCardVersionInfo();
        configInfo.setContent(JacksonUtils.toJson(versionInfo));
        configPage.setPageItems(Collections.singletonList(configInfo));
        configPage.setTotalCount(1);
        
        when(configDetailService.findConfigInfoPage(eq(Constants.A2A.SEARCH_BLUR), eq(1), eq(10), anyString(),
                eq(Constants.A2A.AGENT_GROUP), eq(TEST_NAMESPACE_ID), eq(null))).thenReturn(configPage);
        
        // Call with empty agent name
        Page<AgentCardVersionInfo> result = a2aServerOperationService.listAgents(TEST_NAMESPACE_ID, "",
                Constants.A2A.SEARCH_BLUR, 1, 10);
        
        assertNotNull(result);
        assertEquals(1, result.getTotalCount());
        assertEquals(1, result.getPageItems().size());
    }
    
    @Test
    void testDeleteAgentWithEmptyVersion() throws NacosException {
        AgentCardVersionInfo versionInfo = buildTestAgentCardVersionInfo();
        ConfigQueryChainResponse response = mock(ConfigQueryChainResponse.class);
        
        when(response.getStatus()).thenReturn(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        when(response.getContent()).thenReturn(JacksonUtils.toJson(versionInfo));
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(response);
        
        when(configOperationService.deleteConfig(anyString(), anyString(), anyString(), any(), any(), anyString(),
                any())).thenReturn(true);
        
        // Test deleting with empty version string - should behave like null
        a2aServerOperationService.deleteAgent(TEST_NAMESPACE_ID, TEST_AGENT_NAME, "");
        
        // Should delete all versions
        verify(configOperationService, times(2)).deleteConfig(anyString(), anyString(), anyString(), any(), any(),
                anyString(), any());
    }
    
    @Test
    void testDeleteAgentVersionNotLatest() throws NacosException {
        AgentCardVersionInfo versionInfo = buildTestAgentCardVersionInfo();
        // Make it not the latest version
        versionInfo.setLatestPublishedVersion("2.0.0");
        versionInfo.getVersionDetails().get(0).setLatest(false);
        List<AgentVersionDetail> newVersionDetails = new LinkedList<>(versionInfo.getVersionDetails());
        versionInfo.setVersionDetails(newVersionDetails);
        
        ConfigQueryChainResponse response = mock(ConfigQueryChainResponse.class);
        when(response.getStatus()).thenReturn(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        when(response.getContent()).thenReturn(JacksonUtils.toJson(versionInfo));
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(response);
        
        when(configOperationService.deleteConfig(eq(ENCODED_AGENT_NAME_WITH_VERSION),
                eq(Constants.A2A.AGENT_VERSION_GROUP), eq(TEST_NAMESPACE_ID), eq(null), eq(null), eq("nacos"),
                eq(null))).thenReturn(true);
        
        // Add a second version so it doesn't delete the main config
        AgentVersionDetail secondVersion = new AgentVersionDetail();
        secondVersion.setVersion("2.0.0");
        secondVersion.setLatest(true);
        versionInfo.getVersionDetails().add(secondVersion);
        
        a2aServerOperationService.deleteAgent(TEST_NAMESPACE_ID, TEST_AGENT_NAME, TEST_AGENT_VERSION);
        
        verify(configOperationService, times(1)).deleteConfig(eq(ENCODED_AGENT_NAME_WITH_VERSION),
                eq(Constants.A2A.AGENT_VERSION_GROUP), eq(TEST_NAMESPACE_ID), eq(null), eq(null), eq("nacos"),
                eq(null));
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