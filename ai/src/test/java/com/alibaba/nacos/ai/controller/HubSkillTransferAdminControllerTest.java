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

package com.alibaba.nacos.ai.controller;

import com.alibaba.nacos.ai.service.hub.HubSkillTransferService;
import com.alibaba.nacos.api.ai.model.hub.HubSkillTransferRequest;
import com.alibaba.nacos.api.ai.model.hub.HubSkillTransferResult;
import com.alibaba.nacos.core.exception.NacosApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MVC transfer binding and validation before either permission flow. */
class HubSkillTransferAdminControllerTest {
    
    private static final String PATH = "/v3/admin/ai/hub/skills";
    private final HubSkillTransferService service = mock(HubSkillTransferService.class);
    private final MockMvc mvc =
        MockMvcBuilders.standaloneSetup(new HubSkillTransferAdminController(service))
            .setControllerAdvice(new NacosApiExceptionHandler()).build();
    
    @Test
    void invalidHubIdsAreControlled400BeforeCallingService() throws Exception {
        for (String value : new String[] {"", "abc", "0", "-1", "9223372036854775808"}) {
            mvc.perform(post(PATH + "/import").param("hubEntryId", value)
                .param("targetNamespaceId", "target-ns").param("targetName", "new-skill"))
                .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }
    
    @Test
    void importPreservesExactIdAndDefaultsTargetVersion() throws Exception {
        when(service.importFromHub(any())).thenReturn(new HubSkillTransferResult());
        mvc.perform(post(PATH + "/import").param("hubEntryId", "9007199254740993")
            .param("targetNamespaceId", "target-ns").param("targetName", "new-skill"))
            .andExpect(status().isOk());
        ArgumentCaptor<HubSkillTransferRequest> captor =
            ArgumentCaptor.forClass(HubSkillTransferRequest.class);
        verify(service).importFromHub(captor.capture());
        assertEquals(9007199254740993L, captor.getValue().getHubEntryId());
        assertEquals("0.0.1", captor.getValue().getTargetVersion());
    }
    
    @Test
    void hybridAndLatestRequestsCannotReachService() throws Exception {
        mvc.perform(post(PATH + "/import").param("hubEntryId", "1").param("sourceName", "")
            .param("targetNamespaceId", "target-ns").param("targetName", "new-skill"))
            .andExpect(status().isBadRequest());
        mvc.perform(post(PATH + "/copy").param("sourceNamespaceId", "source-ns")
            .param("sourceName", "original").param("sourceVersion", "latest")
            .param("targetNamespaceId", "target-ns").param("targetName", "new-skill"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    
    @Test
    void copyDelegatesConcreteSourceAndTargetSeparately() throws Exception {
        when(service.copyDirect(any())).thenReturn(new HubSkillTransferResult());
        mvc.perform(post(PATH + "/copy").param("sourceNamespaceId", "source-ns")
            .param("sourceName", "original").param("sourceVersion", "1.0.0")
            .param("targetNamespaceId", "target-ns").param("targetName", "new-skill"))
            .andExpect(status().isOk());
        ArgumentCaptor<HubSkillTransferRequest> captor =
            ArgumentCaptor.forClass(HubSkillTransferRequest.class);
        verify(service).copyDirect(captor.capture());
        assertEquals("source-ns", captor.getValue().getSourceNamespaceId());
        assertEquals("1.0.0", captor.getValue().getSourceVersion());
    }
}
