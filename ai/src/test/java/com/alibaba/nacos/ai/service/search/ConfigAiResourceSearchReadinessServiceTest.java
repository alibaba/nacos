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

package com.alibaba.nacos.ai.service.search;

import com.alibaba.nacos.config.server.exception.ConfigAlreadyExistsException;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Constructor;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigAiResourceSearchReadinessServiceTest {
    
    private static final String RESOURCE_TYPE = "agent";
    
    private static final int PROJECTION_VERSION = 1;
    
    @Mock
    private ConfigQueryChainService configQueryChainService;
    
    @Mock
    private ConfigOperationService configOperationService;
    
    @Mock
    private AiResourceIndexTaskRepository taskRepository;
    
    private ConfigAiResourceSearchReadinessService service;
    
    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.ofEpochMilli(12345L), ZoneOffset.UTC);
        service = new ConfigAiResourceSearchReadinessService(configQueryChainService,
            configOperationService, taskRepository, clock);
    }
    
    @Test
    void shouldRequireExactReadyGeneration() {
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
            .thenReturn(response(record("agent", 1, "READY", 12L), "md5"),
                response(record("agent", 1, "VERIFYING", 0L), "md5"),
                response(record("agent", 2, "READY", 12L), "md5"));
        
        assertTrue(service.isReady(RESOURCE_TYPE, PROJECTION_VERSION));
        assertFalse(service.isReady(RESOURCE_TYPE, PROJECTION_VERSION));
        assertFalse(service.isReady(RESOURCE_TYPE, PROJECTION_VERSION));
        assertFalse(service.isReady("", PROJECTION_VERSION));
        assertFalse(service.isReady(RESOURCE_TYPE, 0));
    }
    
    @Test
    void shouldExposeSafeDefaultAndNoopImplementations() {
        ConfigAiResourceSearchReadinessService defaultClockService =
            new ConfigAiResourceSearchReadinessService(configQueryChainService,
                configOperationService, taskRepository);
        assertFalse(defaultClockService.isReady("", PROJECTION_VERSION));
        assertFalse(AiResourceSearchReadinessService.NOOP.isReady(RESOURCE_TYPE,
            PROJECTION_VERSION));
        assertDoesNotThrow(() -> AiResourceSearchReadinessService.NOOP.recordCompletedScan(
            RESOURCE_TYPE, PROJECTION_VERSION, true));
    }
    
    @Test
    void shouldDeclareProductionInjectionConstructor() throws Exception {
        Constructor<ConfigAiResourceSearchReadinessService> constructor =
            ConfigAiResourceSearchReadinessService.class.getConstructor(
                ConfigQueryChainService.class, ConfigOperationService.class,
                AiResourceIndexTaskRepository.class);
        
        assertTrue(constructor.isAnnotationPresent(Autowired.class));
    }
    
    @Test
    void shouldReturnFalseForAbsentInvalidOrUnavailableRecord() {
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
            .thenReturn(notFound(), null, response("", "md5"), response("not-json", "md5"))
            .thenThrow(new IllegalStateException("query failed"));
        
        assertFalse(service.isReady(RESOURCE_TYPE, PROJECTION_VERSION));
        assertFalse(service.isReady(RESOURCE_TYPE, PROJECTION_VERSION));
        assertFalse(service.isReady(RESOURCE_TYPE, PROJECTION_VERSION));
        assertFalse(service.isReady(RESOURCE_TYPE, PROJECTION_VERSION));
        assertFalse(service.isReady(RESOURCE_TYPE, PROJECTION_VERSION));
    }
    
    @Test
    void shouldEstablishVerifyingOnFirstCompleteScan() throws Exception {
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
            .thenReturn(notFound());
        
        service.recordCompletedScan(RESOURCE_TYPE, PROJECTION_VERSION, true);
        
        ArgumentCaptor<ConfigForm> form = ArgumentCaptor.forClass(ConfigForm.class);
        ArgumentCaptor<ConfigRequestInfo> request =
            ArgumentCaptor.forClass(ConfigRequestInfo.class);
        verify(configOperationService).publishConfig(form.capture(), request.capture(), isNull());
        assertTrue(form.getValue().getDataId().endsWith("agent.v1"));
        assertTrue(form.getValue().getContent().contains("\"state\":\"VERIFYING\""));
        assertFalse(request.getValue().getUpdateForExist());
        verifyNoInteractions(taskRepository);
    }
    
    @Test
    void shouldBecomeReadyOnlyAfterCleanVerificationAndTaskDrain() throws Exception {
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
            .thenReturn(response(record("agent", 1, "VERIFYING", 0L), "cas-md5"));
        when(taskRepository.hasUnfinishedTasks(RESOURCE_TYPE)).thenReturn(false);
        
        service.recordCompletedScan(RESOURCE_TYPE, PROJECTION_VERSION, true);
        
        ArgumentCaptor<ConfigForm> form = ArgumentCaptor.forClass(ConfigForm.class);
        ArgumentCaptor<ConfigRequestInfo> request =
            ArgumentCaptor.forClass(ConfigRequestInfo.class);
        verify(configOperationService).publishConfig(form.capture(), request.capture(), isNull());
        assertTrue(form.getValue().getContent().contains("\"state\":\"READY\""));
        assertTrue(form.getValue().getContent().contains("\"completedAt\":12345"));
        assertTrue(request.getValue().getUpdateForExist());
        assertEquals("cas-md5", request.getValue().getCasMd5());
    }
    
    @Test
    void shouldRemainVerifyingForDirtyScanOrUnfinishedTask() throws Exception {
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
            .thenReturn(response(record("agent", 1, "VERIFYING", 0L), "one"),
                response(record("agent", 1, "VERIFYING", 0L), "two"));
        when(taskRepository.hasUnfinishedTasks(RESOURCE_TYPE)).thenReturn(true);
        
        service.recordCompletedScan(RESOURCE_TYPE, PROJECTION_VERSION, false);
        service.recordCompletedScan(RESOURCE_TYPE, PROJECTION_VERSION, true);
        
        ArgumentCaptor<ConfigForm> forms = ArgumentCaptor.forClass(ConfigForm.class);
        verify(configOperationService, org.mockito.Mockito.times(2))
            .publishConfig(forms.capture(), any(ConfigRequestInfo.class), isNull());
        assertTrue(forms.getAllValues().stream()
            .allMatch(form -> form.getContent().contains("\"state\":\"VERIFYING\"")));
        verify(taskRepository).hasUnfinishedTasks(RESOURCE_TYPE);
    }
    
    @Test
    void shouldKeepReadyStickyAndIgnoreInvalidInputs() throws Exception {
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
            .thenReturn(response(record("agent", 1, "READY", 12L), "md5"));
        
        service.recordCompletedScan(RESOURCE_TYPE, PROJECTION_VERSION, false);
        service.recordCompletedScan("", PROJECTION_VERSION, true);
        service.recordCompletedScan(RESOURCE_TYPE, 0, true);
        
        verify(configOperationService, never()).publishConfig(any(), any(), any());
        verifyNoInteractions(taskRepository);
    }
    
    @Test
    void shouldRepairMalformedRecordThroughCasButRefuseMissingCasMetadata() throws Exception {
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
            .thenReturn(response("invalid", "repair-md5"), response("invalid", null));
        
        service.recordCompletedScan(RESOURCE_TYPE, PROJECTION_VERSION, true);
        service.recordCompletedScan(RESOURCE_TYPE, PROJECTION_VERSION, true);
        
        ArgumentCaptor<ConfigRequestInfo> request =
            ArgumentCaptor.forClass(ConfigRequestInfo.class);
        verify(configOperationService).publishConfig(any(ConfigForm.class), request.capture(),
            isNull());
        assertEquals("repair-md5", request.getValue().getCasMd5());
    }
    
    @Test
    void shouldNotAdvanceWhenReadOrTaskInspectionFails() throws Exception {
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
            .thenThrow(new IllegalStateException("query"))
            .thenReturn(response(record("agent", 1, "VERIFYING", 0L), "md5"));
        when(taskRepository.hasUnfinishedTasks(RESOURCE_TYPE))
            .thenThrow(new IllegalStateException("tasks"));
        
        assertDoesNotThrow(() -> service.recordCompletedScan(RESOURCE_TYPE,
            PROJECTION_VERSION, true));
        assertDoesNotThrow(() -> service.recordCompletedScan(RESOURCE_TYPE,
            PROJECTION_VERSION, true));
        
        verify(configOperationService, never()).publishConfig(any(), any(), any());
    }
    
    @Test
    void shouldIgnoreConcurrentInitialization() throws Exception {
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class)))
            .thenReturn(notFound());
        doThrow(new ConfigAlreadyExistsException("exists"))
            .when(configOperationService).publishConfig(any(), any(), isNull());
        
        assertDoesNotThrow(() -> service.recordCompletedScan(RESOURCE_TYPE,
            PROJECTION_VERSION, false));
    }
    
    private ConfigQueryChainResponse notFound() {
        ConfigQueryChainResponse result = new ConfigQueryChainResponse();
        result.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND);
        return result;
    }
    
    private ConfigQueryChainResponse response(String content, String md5) {
        ConfigQueryChainResponse result = new ConfigQueryChainResponse();
        result.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        result.setContent(content);
        result.setMd5(md5);
        return result;
    }
    
    private String record(String resourceType, int version, String state, long completedAt) {
        return "{\"resourceType\":\"" + resourceType + "\",\"projectionVersion\":"
            + version + ",\"state\":\"" + state + "\",\"completedAt\":"
            + completedAt + '}';
    }
}
