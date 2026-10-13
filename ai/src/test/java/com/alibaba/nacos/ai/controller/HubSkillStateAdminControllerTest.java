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

import com.alibaba.nacos.ai.service.hub.HubSkillShareStateService;
import com.alibaba.nacos.api.ai.model.hub.HubSkillShareState;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.core.exception.NacosApiExceptionHandler;
import org.junit.jupiter.api.Test;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HubSkillStateAdminControllerTest {
    
    private final HubSkillShareStateService service = mock(HubSkillShareStateService.class);
    private final MockMvc mvc =
        MockMvcBuilders.standaloneSetup(new HubSkillStateAdminController(service))
            .setControllerAdvice(new NacosApiExceptionHandler()).build();
    private static final String PATH = "/v3/admin/ai/hub/skills";
    
    @Test
    void invalidGovernanceIdIsControlledBeforeServiceInvocation() throws Exception {
        for (String id : new String[] {"", "x", "0", "-1", "9223372036854775808"}) {
            mvc.perform(get(PATH + "/governance-state").param("hubEntryId", id))
                .andExpect(status().isBadRequest());
        }
        mvc.perform(get(PATH + "/governance-state")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    
    @Test
    void largeIdAndMetaVersionSerializeWithoutPrecisionLoss() throws Exception {
        HubSkillShareState state = new HubSkillShareState();
        state.setHubEntryId(Long.MAX_VALUE);
        state.setMetaVersion(9007199254740993L);
        state.setSharedVersion("1.0.0");
        when(service.byEntryForAdmin(Long.MAX_VALUE)).thenReturn(state);
        String response = mvc.perform(get(PATH + "/governance-state")
            .param("hubEntryId", String.valueOf(Long.MAX_VALUE)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode data = JacksonUtils.toObj(response, JsonNode.class).get("data");
        assertEquals("9223372036854775807", data.get("hubEntryId").asText());
        assertEquals("9007199254740993", data.get("metaVersion").asText());
        verify(service).byEntryForAdmin(Long.MAX_VALUE);
    }
    
    @Test
    void sourceLookupPreservesNamespaceAndFullName() throws Exception {
        mvc.perform(get(PATH + "/source-share").param("sourceNamespaceId", "ns-a")
            .param("sourceName", "find/skills,tools")).andExpect(status().isOk());
        verify(service).bySource("ns-a", "find/skills,tools");
    }
    
    @Test
    void realServiceDenialsRemain403InHttpResponse() throws Exception {
        when(service.byEntryForAdmin(901L)).thenThrow(new NacosApiException(NacosException.NO_RIGHT,
            ErrorCode.ACCESS_DENIED, "Hub permission denied"));
        mvc.perform(get(PATH + "/governance-state").param("hubEntryId", "901"))
            .andExpect(status().isForbidden());
    }
}
