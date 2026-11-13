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

import com.alibaba.nacos.ai.service.hub.HubAgentService;
import com.alibaba.nacos.api.ai.model.hub.HubShareResult;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HubAgentAdminControllerTest {
    
    @Test
    void fixedResourceRouteDelegatesShareAndOnlyAllowsHubImport() throws Exception {
        HubAgentService service = mock(HubAgentService.class);
        when(service.share(any())).thenReturn(new HubShareResult());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new HubAgentAdminController(service),
            new HubAgentStateAdminController(service), new HubAgentImportAdminController(service))
            .build();
        String path = "/v3/admin/ai/hub/agent";
        assertArrayEquals(new String[] {path}, HubAgentAdminController.class
            .getAnnotation(RequestMapping.class).value());
        mvc.perform(post(path + "/share").param("sourceNamespaceId", "public")
            .param("sourceName", "demo").param("sourceVersion", "1.0.0"))
            .andExpect(status().isOk());
        verify(service).share(any());
        mvc.perform(post(path + "/import").param("hubEntryId", "501")
            .param("targetNamespaceId", "public").param("targetName", "imported")
            .param("targetVersion", "0.0.1")).andExpect(status().isOk());
        verify(service).importFromHub(any());
        for (String route : new String[] {"copy", "clone"}) {
            mvc.perform(post(path + "/" + route)).andExpect(status().isNotFound());
        }
    }
}
