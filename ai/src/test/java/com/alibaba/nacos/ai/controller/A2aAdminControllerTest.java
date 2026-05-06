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

package com.alibaba.nacos.ai.controller;

import com.alibaba.nacos.ai.form.a2a.admin.AgentCardForm;
import com.alibaba.nacos.ai.form.a2a.admin.AgentCardUpdateForm;
import com.alibaba.nacos.ai.form.a2a.admin.AgentForm;
import com.alibaba.nacos.ai.form.a2a.admin.AgentListForm;
import com.alibaba.nacos.ai.service.a2a.A2aServerOperationService;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardVersionInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentVersionDetail;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.core.model.form.PageForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static com.alibaba.nacos.ai.constant.Constants.MCP_LIST_SEARCH_BLUR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for A2aAdminController.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
class A2aAdminControllerTest {
    
    @Mock
    private A2aServerOperationService a2aServerOperationService;
    
    @InjectMocks
    private A2aAdminController a2aAdminController;
    
    private AgentCardForm agentCardForm;
    
    private AgentForm agentForm;
    
    private AgentCardUpdateForm agentCardUpdateForm;
    
    private AgentListForm agentListForm;
    
    private PageForm pageForm;
    
    @BeforeEach
    void setUp() {
        agentCardForm = new AgentCardForm();
        agentCardForm.setAgentName("test-agent");
        agentCardForm.setNamespaceId("public");
        agentCardForm.setVersion("1.0.0");
        agentCardForm.setRegistrationType(AiConstants.A2a.A2A_ENDPOINT_TYPE_URL);
        agentCardForm.setAgentCard(
                "{\"name\":\"test-agent\",\"version\":\"1.0.0\",\"protocolVersion\":\"1.0\",\"preferredTransport\":\"JSONRPC\",\"description\":\"Test agent description\",\"url\":\"http://test-agent.example.com\"}");
        
        agentForm = new AgentForm();
        agentForm.setAgentName("test-agent");
        agentForm.setNamespaceId("public");
        agentForm.setVersion("1.0.0");
        agentForm.setRegistrationType(AiConstants.A2a.A2A_ENDPOINT_TYPE_URL);
        
        agentCardUpdateForm = new AgentCardUpdateForm();
        agentCardUpdateForm.setAgentName("test-agent");
        agentCardUpdateForm.setNamespaceId("public");
        agentCardUpdateForm.setVersion("1.0.0");
        agentCardUpdateForm.setSetAsLatest(true);
        agentCardUpdateForm.setRegistrationType(AiConstants.A2a.A2A_ENDPOINT_TYPE_URL);
        agentCardUpdateForm.setAgentCard(
                "{\"name\":\"test-agent\",\"version\":\"1.0.0\",\"protocolVersion\":\"1.0\",\"preferredTransport\":\"JSONRPC\",\"description\":\"Updated description\",\"url\":\"http://test-agent.example.com\"}");
        
        agentListForm = new AgentListForm();
        agentListForm.setAgentName("test-agent");
        agentListForm.setNamespaceId("public");
        agentListForm.setSearch(MCP_LIST_SEARCH_BLUR);
        
        pageForm = new PageForm();
        pageForm.setPageNo(1);
        pageForm.setPageSize(10);
    }
    
    @Test
    void testRegisterAgentSuccess() throws NacosException {
        // Arrange
        doNothing().when(a2aServerOperationService).registerAgent(any(AgentCard.class), anyString(), anyString());
        
        // Act
        Result<String> result = a2aAdminController.registerAgent(agentCardForm);
        
        // Assert
        assertNotNull(result);
        assertEquals("ok", result.getData());
        verify(a2aServerOperationService).registerAgent(any(AgentCard.class), anyString(), anyString());
    }
    
    @Test
    void testRegisterAgentValidationFailure() throws NacosException {
        // Arrange
        AgentCardForm invalidForm = new AgentCardForm();
        // Missing required name field
        
        // Act & Assert
        assertThrows(NacosApiException.class, () -> a2aAdminController.registerAgent(invalidForm));
        verify(a2aServerOperationService, never()).registerAgent(any(AgentCard.class), anyString(), anyString());
    }
    
    @Test
    void testRegisterAgentServiceException() throws NacosException {
        // Arrange
        NacosException exception = new NacosException(NacosException.SERVER_ERROR, "Registration failed");
        doThrow(exception).when(a2aServerOperationService)
                .registerAgent(any(AgentCard.class), anyString(), anyString());
        
        // Act & Assert
        assertThrows(NacosException.class, () -> a2aAdminController.registerAgent(agentCardForm));
        verify(a2aServerOperationService).registerAgent(any(AgentCard.class), anyString(), anyString());
    }
    
    @Test
    void testGetAgentCardSuccess() throws NacosApiException {
        // Arrange
        AgentCardDetailInfo expectedAgentCard = new AgentCardDetailInfo();
        expectedAgentCard.setName("test-agent");
        expectedAgentCard.setVersion("1.0.0");
        expectedAgentCard.setProtocolVersion("1.0");
        expectedAgentCard.setPreferredTransport("JSONRPC");
        expectedAgentCard.setDescription("Test agent description");
        
        when(a2aServerOperationService.getAgentCard(anyString(), anyString(), anyString(), anyString())).thenReturn(
                expectedAgentCard);
        
        // Act
        Result<AgentCardDetailInfo> result = a2aAdminController.getAgentCard(agentForm);
        
        // Assert
        assertNotNull(result);
        assertEquals(expectedAgentCard, result.getData());
        verify(a2aServerOperationService).getAgentCard(anyString(), anyString(), anyString(), anyString());
    }
    
    @Test
    void testGetAgentCardValidationFailure() throws NacosApiException {
        // Arrange
        AgentForm invalidForm = new AgentForm();
        // Missing required fields
        
        // Act & Assert
        assertThrows(NacosApiException.class, () -> a2aAdminController.getAgentCard(invalidForm));
        verify(a2aServerOperationService, never()).getAgentCard(anyString(), anyString(), anyString(), anyString());
    }
    
    @Test
    void testGetAgentCardServiceException() throws NacosApiException {
        // Arrange
        NacosApiException exception = new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.RESOURCE_NOT_FOUND,
                "Agent not found");
        when(a2aServerOperationService.getAgentCard(anyString(), anyString(), anyString(), anyString())).thenThrow(
                exception);
        
        // Act & Assert
        assertThrows(NacosApiException.class, () -> a2aAdminController.getAgentCard(agentForm));
        verify(a2aServerOperationService).getAgentCard(anyString(), anyString(), anyString(), anyString());
    }
    
    @Test
    void testUpdateAgentCardSuccess() throws NacosException {
        // Arrange
        doNothing().when(a2aServerOperationService)
                .updateAgentCard(any(AgentCard.class), anyString(), anyString(), anyBoolean());
        
        // Act
        Result<String> result = a2aAdminController.updateAgentCard(agentCardUpdateForm);
        
        // Assert
        assertNotNull(result);
        assertEquals("ok", result.getData());
        verify(a2aServerOperationService).updateAgentCard(any(AgentCard.class), anyString(), anyString(), anyBoolean());
    }
    
    @Test
    void testUpdateAgentCardValidationFailure() throws NacosException {
        // Arrange
        AgentCardUpdateForm invalidForm = new AgentCardUpdateForm();
        // Missing required name field
        
        // Act & Assert
        assertThrows(NacosApiException.class, () -> a2aAdminController.updateAgentCard(invalidForm));
        verify(a2aServerOperationService, never()).updateAgentCard(any(AgentCard.class), anyString(), anyString(),
                anyBoolean());
    }
    
    @Test
    void testUpdateAgentCardServiceException() throws NacosException {
        // Arrange
        NacosException exception = new NacosException(NacosException.SERVER_ERROR, "Update failed");
        doThrow(exception).when(a2aServerOperationService)
                .updateAgentCard(any(AgentCard.class), anyString(), anyString(), anyBoolean());
        
        // Act & Assert
        assertThrows(NacosException.class, () -> a2aAdminController.updateAgentCard(agentCardUpdateForm));
        verify(a2aServerOperationService).updateAgentCard(any(AgentCard.class), anyString(), anyString(), anyBoolean());
    }
    
    @Test
    void testDeleteAgentSuccess() throws NacosException {
        // Arrange
        doNothing().when(a2aServerOperationService).deleteAgent(anyString(), anyString(), anyString());
        
        // Act
        Result<String> result = a2aAdminController.deleteAgent(agentForm);
        
        // Assert
        assertNotNull(result);
        assertEquals("ok", result.getData());
        verify(a2aServerOperationService).deleteAgent(anyString(), anyString(), anyString());
    }
    
    @Test
    void testDeleteAgentValidationFailure() throws NacosException {
        // Arrange
        AgentForm invalidForm = new AgentForm();
        // Missing required fields
        
        // Act & Assert
        assertThrows(NacosApiException.class, () -> a2aAdminController.deleteAgent(invalidForm));
        verify(a2aServerOperationService, never()).deleteAgent(anyString(), anyString(), anyString());
    }
    
    @Test
    void testDeleteAgentServiceException() throws NacosException {
        // Arrange
        NacosException exception = new NacosException(NacosException.SERVER_ERROR, "Delete failed");
        doThrow(exception).when(a2aServerOperationService).deleteAgent(anyString(), anyString(), anyString());
        
        // Act & Assert
        assertThrows(NacosException.class, () -> a2aAdminController.deleteAgent(agentForm));
        verify(a2aServerOperationService).deleteAgent(anyString(), anyString(), anyString());
    }
    
    @Test
    void testListAgentsSuccess() throws NacosException {
        // Arrange
        AgentCardVersionInfo agent1 = new AgentCardVersionInfo();
        agent1.setName("agent1");
        agent1.setLatestPublishedVersion("1.0.0");
        
        AgentCardVersionInfo agent2 = new AgentCardVersionInfo();
        agent2.setName("agent2");
        agent2.setLatestPublishedVersion("2.0.0");
        
        List<AgentCardVersionInfo> agentList = Arrays.asList(agent1, agent2);
        Page<AgentCardVersionInfo> expectedPage = new Page<>();
        expectedPage.setPageItems(agentList);
        expectedPage.setTotalCount(2);
        expectedPage.setPageNumber(1);
        expectedPage.setPagesAvailable(1);
        
        when(a2aServerOperationService.listAgents(anyString(), anyString(), anyString(), anyInt(),
                anyInt())).thenReturn(expectedPage);
        
        // Act
        Result<Page<AgentCardVersionInfo>> result = a2aAdminController.listAgents(agentListForm, pageForm);
        
        // Assert
        assertNotNull(result);
        assertEquals(expectedPage, result.getData());
        assertEquals(2, result.getData().getTotalCount());
        assertEquals(2, result.getData().getPageItems().size());
        verify(a2aServerOperationService).listAgents(anyString(), anyString(), anyString(), anyInt(), anyInt());
    }
    
    @Test
    void testListAgentsAgentListFormValidationFailure() throws NacosException {
        // Arrange
        AgentListForm invalidForm = new AgentListForm();
        // Missing required fields
        PageForm validPageForm = new PageForm();
        validPageForm.setPageNo(1);
        validPageForm.setPageSize(10);
        
        // Act & Assert
        assertThrows(NacosApiException.class, () -> a2aAdminController.listAgents(invalidForm, validPageForm));
        verify(a2aServerOperationService, never()).listAgents(anyString(), anyString(), anyString(), anyInt(),
                anyInt());
    }
    
    @Test
    void testListAgentsPageFormValidationFailure() throws NacosException {
        // Arrange
        AgentListForm validAgentListForm = new AgentListForm();
        validAgentListForm.setAgentName("test-agent");
        validAgentListForm.setNamespaceId("public");
        validAgentListForm.setSearch(MCP_LIST_SEARCH_BLUR);
        
        PageForm invalidPageForm = new PageForm();
        invalidPageForm.setPageNo(0); // Invalid page number
        
        // Act & Assert
        assertThrows(NacosApiException.class, () -> a2aAdminController.listAgents(validAgentListForm, invalidPageForm));
        verify(a2aServerOperationService, never()).listAgents(anyString(), anyString(), anyString(), anyInt(),
                anyInt());
    }
    
    @Test
    void testListAgentsServiceException() throws NacosException {
        // Arrange
        NacosException exception = new NacosException(NacosException.SERVER_ERROR, "List failed");
        when(a2aServerOperationService.listAgents(anyString(), anyString(), anyString(), anyInt(), anyInt())).thenThrow(
                exception);
        
        // Act & Assert
        assertThrows(NacosException.class, () -> a2aAdminController.listAgents(agentListForm, pageForm));
        verify(a2aServerOperationService).listAgents(anyString(), anyString(), anyString(), anyInt(), anyInt());
    }
    
    @Test
    void testListAgentVersionsSuccess() throws NacosException {
        // Arrange
        AgentVersionDetail version1 = new AgentVersionDetail();
        version1.setVersion("1.0.0");
        version1.setLatest(true);
        
        AgentVersionDetail version2 = new AgentVersionDetail();
        version2.setVersion("2.0.0");
        version2.setLatest(false);
        
        List<AgentVersionDetail> versionList = Arrays.asList(version1, version2);
        
        when(a2aServerOperationService.listAgentVersions(anyString(), anyString())).thenReturn(versionList);
        
        // Act
        Result<List<AgentVersionDetail>> result = a2aAdminController.listAgentVersions(agentForm);
        
        // Assert
        assertNotNull(result);
        assertEquals(versionList, result.getData());
        assertEquals(2, result.getData().size());
        verify(a2aServerOperationService).listAgentVersions(anyString(), anyString());
    }
    
    @Test
    void testListAgentVersionsValidationFailure() throws NacosApiException {
        // Arrange
        AgentForm invalidForm = new AgentForm();
        // Missing required fields
        
        // Act & Assert
        assertThrows(NacosApiException.class, () -> a2aAdminController.listAgentVersions(invalidForm));
        verify(a2aServerOperationService, never()).listAgentVersions(anyString(), anyString());
    }
    
    @Test
    void testListAgentVersionsEmptyResult() throws NacosException {
        // Arrange
        List<AgentVersionDetail> emptyList = Arrays.asList();
        when(a2aServerOperationService.listAgentVersions(anyString(), anyString())).thenReturn(emptyList);
        
        // Act
        Result<List<AgentVersionDetail>> result = a2aAdminController.listAgentVersions(agentForm);
        
        // Assert
        assertNotNull(result);
        assertEquals(emptyList, result.getData());
        assertEquals(0, result.getData().size());
        verify(a2aServerOperationService).listAgentVersions(anyString(), anyString());
    }
    
    @Test
    void testListAgentsEmptyResult() throws NacosException {
        // Arrange
        List<AgentCardVersionInfo> emptyList = Arrays.asList();
        Page<AgentCardVersionInfo> emptyPage = new Page<>();
        emptyPage.setPageItems(emptyList);
        emptyPage.setTotalCount(0);
        emptyPage.setPageNumber(1);
        emptyPage.setPagesAvailable(1);
        
        when(a2aServerOperationService.listAgents(anyString(), anyString(), anyString(), anyInt(),
                anyInt())).thenReturn(emptyPage);
        
        // Act
        Result<Page<AgentCardVersionInfo>> result = a2aAdminController.listAgents(agentListForm, pageForm);
        
        // Assert
        assertNotNull(result);
        assertEquals(emptyPage, result.getData());
        assertEquals(0, result.getData().getTotalCount());
        assertEquals(0, result.getData().getPageItems().size());
        verify(a2aServerOperationService).listAgents(anyString(), anyString(), anyString(), anyInt(), anyInt());
    }
}