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

import com.alibaba.nacos.ai.service.hub.HubSkillReadService;
import com.alibaba.nacos.api.ai.model.hub.HubSkillListRequest;
import com.alibaba.nacos.api.model.Page;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real MVC binding and error mapping without substituting for the deployed auth tests. */
class HubSkillReadHttpTest {
    
    private final HubSkillReadService service = mock(HubSkillReadService.class);
    private final MockMvc mvc =
        MockMvcBuilders.standaloneSetup(new HubSkillReadAdminController(service))
            .setControllerAdvice(new NacosApiExceptionHandler()).build();
    private static final String PATH = "/v3/admin/ai/hub/skills";
    
    @Test
    void invalidNumericParametersProduce400BeforeCallingService() throws Exception {
        for (String value : new String[] {"", "abc", "0", "-1", "9223372036854775808"}) {
            mvc.perform(get(PATH + "/detail").param("hubEntryId", value))
                .andExpect(status().isBadRequest());
            mvc.perform(get(PATH + "/content").param("hubEntryId", value))
                .andExpect(status().isBadRequest());
            mvc.perform(get(PATH + "/download").param("hubEntryId", value))
                .andExpect(status().isBadRequest());
            mvc.perform(get(PATH + "/list").param("pageNo", value))
                .andExpect(status().isBadRequest());
        }
        mvc.perform(get(PATH + "/detail")).andExpect(status().isBadRequest());
        mvc.perform(get(PATH + "/list").param("pageSize", "101"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    
    @Test
    void indexedTagsPreserveCommasAcrossTheRemoteTransport() throws Exception {
        when(service.list(any(HubSkillListRequest.class))).thenReturn(new Page<>());
        mvc.perform(
            get(PATH + "/list").param("tagsAll[0]", "search,tools").param("tagsAll[1]", "coding"))
            .andExpect(status().isOk());
        ArgumentCaptor<HubSkillListRequest> captor =
            ArgumentCaptor.forClass(HubSkillListRequest.class);
        verify(service).list(captor.capture());
        assertEquals(java.util.List.of("search,tools", "coding"), captor.getValue().getTagsAll());
        assertEquals(1, captor.getValue().getPageNo());
        assertEquals(20, captor.getValue().getPageSize());
    }
    
    @Test
    void largeEntryIdIsPassedExactly() throws Exception {
        mvc.perform(get(PATH + "/detail").param("hubEntryId", "9007199254740993"))
            .andExpect(status().isOk());
        verify(service).getDetail(9007199254740993L);
    }
}
