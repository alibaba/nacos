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

package com.alibaba.nacos.ai.service.hub;

import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.config.HubConfig;
import com.alibaba.nacos.ai.model.hub.HubResourceType;
import com.alibaba.nacos.ai.service.repository.HubEntryStorage;
import com.alibaba.nacos.ai.utils.HubResourceReferenceJsonUtil;
import com.alibaba.nacos.api.ai.model.hub.HubResourceShareState;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/** Mutation state cannot be inferred from shared directory admission or another resource type. */
class HubResourceManagementServiceTest {
    
    private final HubResourcePublicationService publications =
        mock(HubResourcePublicationService.class);
    private final HubResourceEligibilityValidator eligibility =
        mock(HubResourceEligibilityValidator.class);
    private final HubResourcePermissionGuard permissions = mock(HubResourcePermissionGuard.class);
    private final HubEntryStorage storage = mock(HubEntryStorage.class);
    private final HubResourceManagementService service =
        new HubResourceManagementService(publications, eligibility, permissions, storage);
    
    @ParameterizedTest
    @EnumSource(value = HubResourceType.class, names = {"MCP", "AGENT", "PROMPT", "AGENTSPEC"})
    void sourceStateIsTypedAndRechecksSourceWriteAfterIo(HubResourceType type) throws Exception {
        AiResource source = new AiResource();
        source.setId(101L);
        when(eligibility.requireSource(type, "source", "same-name", null)).thenReturn(source);
        when(eligibility.requireSource(type, "source", "same-name", 101L)).thenReturn(source);
        when(storage.findByInternalName(type, "same-name-101")).thenReturn(entry(type));
        
        HubResourceShareState state =
            service.sourceState(type.resourceType(), "source", "same-name");
        
        assertEquals(501L, state.getHubEntryId());
        assertEquals(4L, state.getMetaVersion());
        assertEquals("1.0.0", state.getSharedVersion());
        assertFalse(state.isActive());
        verify(permissions, times(2)).requireSourceWrite(type, source);
        verifyNoInteractions(publications);
    }
    
    @ParameterizedTest
    @EnumSource(value = HubResourceType.class, names = {"MCP", "AGENT", "PROMPT", "AGENTSPEC"})
    void sourceWriteDeniedBeforeAnyHubStateIsRead(HubResourceType type) throws Exception {
        AiResource source = new AiResource();
        source.setId(101L);
        when(eligibility.requireSource(type, "source", "same-name", null)).thenReturn(source);
        doThrow(new NacosApiException(403, ErrorCode.ACCESS_DENIED, "denied"))
            .when(permissions).requireSourceWrite(type, source);
        assertThrows(NacosException.class,
            () -> service.sourceState(type.resourceType(), "source", "same-name"));
        verifyNoInteractions(storage);
    }
    
    @Test
    void foreignTypedEntryCannotBeReturnedToAdministrator() throws Exception {
        when(storage.findByEntryId(HubResourceType.AGENT, 501L))
            .thenReturn(entry(HubResourceType.PROMPT));
        assertThrows(NacosException.class, () -> service.governanceState("agent", 501L));
        verify(permissions).requireAdministrator();
        verifyNoInteractions(eligibility);
    }
    
    @Test
    void neverSharedSourceReturnsNullAfterReauthorization() throws Exception {
        AiResource source = new AiResource();
        source.setId(101L);
        when(eligibility.requireSource(eq(HubResourceType.PROMPT), eq("source"), eq("same-name"),
            any()))
            .thenReturn(source);
        assertNull(service.sourceState("prompt", "source", "same-name"));
        verify(permissions, times(2)).requireSourceWrite(HubResourceType.PROMPT, source);
    }
    
    @Test
    void administratorDenialDoesNotLeakWhetherAnEntryExists() throws Exception {
        doThrow(new NacosApiException(403, ErrorCode.ACCESS_DENIED, "denied"))
            .when(permissions).requireAdministrator();
        assertThrows(NacosException.class, () -> service.governanceState("mcp", 501L));
        verifyNoInteractions(storage, eligibility);
    }
    
    @Test
    void unknownTypeCannotAccessSkillOrAnyStorage() {
        assertThrows(NacosException.class, () -> service.governanceState("skill", 501L));
        assertThrows(NacosException.class,
            () -> service.sourceState("../skills", "source", "same-name"));
        verifyNoInteractions(storage, eligibility, publications, permissions);
    }
    
    @ParameterizedTest
    @EnumSource(value = HubResourceType.class, names = {"MCP", "AGENT", "PROMPT", "AGENTSPEC"})
    void disabledHubRejectsStateReadsBeforeSourceOrEntryIo(HubResourceType type) throws Exception {
        try (MockedStatic<HubConfig> config = mockStatic(HubConfig.class)) {
            config.when(HubConfig::requireEnabled).thenThrow(new NacosApiException(501,
                ErrorCode.API_FUNCTION_DISABLED, "disabled"));
            assertEquals(501, assertThrows(NacosApiException.class,
                () -> service.sourceState(type.resourceType(), "source", "same-name"))
                .getErrCode());
            assertEquals(ErrorCode.API_FUNCTION_DISABLED.getCode(), assertThrows(
                NacosApiException.class, () -> service.governanceState(type.resourceType(), 501L))
                .getDetailErrCode());
        }
        verifyNoInteractions(storage, eligibility, publications, permissions);
    }
    
    private AiResource entry(HubResourceType type) throws Exception {
        AiResource entry = new AiResource();
        entry.setId(501L);
        entry.setMetaVersion(4L);
        entry.setNamespaceId("__nacos_ai_hub__");
        entry.setType(type.resourceType());
        entry.setName("same-name-101");
        entry.setStatus("disable");
        entry.setFrom("hub");
        entry.setOwner("nacos-ai-hub");
        entry.setScope("PUBLIC");
        entry.setExt(HubResourceReferenceJsonUtil.toJson(
            HubResourceReferenceJsonUtil.build("source", 101L, "same-name", 201L, "1.0.0")));
        return entry;
    }
}
