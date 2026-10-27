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

import com.alibaba.nacos.ai.service.hub.HubSkillManagementAdapter;
import com.alibaba.nacos.ai.service.skills.SkillOperationService;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.skills.SkillDownload;
import com.alibaba.nacos.core.exception.NacosApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SharedSkillReadHttpTest {
    
    private static final String PATH = "/v3/admin/ai/skills";
    private static final String HUB = AiConstants.Hub.NAMESPACE_ID;
    private final SkillOperationService service = mock(SkillOperationService.class);
    private final HubSkillManagementAdapter shared = mock(HubSkillManagementAdapter.class);
    private MockMvc mvc;
    
    @BeforeEach
    void setUp() {
        SkillAdminController controller = new SkillAdminController(service);
        controller.setSharedSkills(shared);
        mvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new NacosApiExceptionHandler()).build();
    }
    
    @Test
    void existingReadPathsKeepTheSharedNamespaceAndOpaqueIdentity() throws Exception {
        mvc.perform(get(PATH).param("namespaceId", HUB).param("skillName", "9007199254740993"))
            .andExpect(status().isOk());
        verify(service).getSkillDetail(HUB, "9007199254740993");
        mvc.perform(get(PATH + "/version").param("namespaceId", HUB).param("skillName", "5")
            .param("version", "1.0.0")).andExpect(status().isOk());
        verify(service).getSkillVersionDetail(HUB, "5", "1.0.0");
        mvc.perform(get(PATH + "/list").param("namespaceId", HUB).param("skillName", "find")
            .param("search", "blur").param("owner", "alice").param("scope", "PUBLIC")
            .param("bizTag", "coding").param("pageNo", "2").param("pageSize", "5"))
            .andExpect(status().isOk());
        verify(service).listSkills(HUB, "find", "blur", null, "alice", "PUBLIC", "coding", 2, 5);
    }
    
    @Test
    void existingSharedListBindsBothManagementSortModes() throws Exception {
        mvc.perform(get(PATH + "/list").param("namespaceId", HUB)
            .param("orderBy", "download_count").param("pageNo", "2").param("pageSize", "5"))
            .andExpect(status().isOk());
        verify(service).listSkills(HUB, null, null, "download_count", null, null, null, 2, 5);
        mvc.perform(get(PATH + "/list").param("namespaceId", HUB)).andExpect(status().isOk());
        verify(service).listSkills(HUB, null, null, null, null, null, null, 1, 100);
    }
    
    @Test
    void downloadRelaysOriginalPackageAndSharedHeaders() throws Exception {
        SkillDownload download = new SkillDownload();
        download.setHubEntryId(5L);
        download.setDisplayName("source-skill");
        download.setResolvedVersion("1.0.0");
        download.setZipBytes(new byte[] {0, (byte) 255});
        when(shared.download("5", "1.0.0")).thenReturn(download);
        mvc.perform(get(PATH + "/version/download").param("namespaceId", HUB)
            .param("skillName", "5").param("version", "1.0.0"))
            .andExpect(status().isOk()).andExpect(content().bytes(download.getZipBytes()))
            .andExpect(content().contentType("application/zip"))
            .andExpect(result -> {
                assertEquals("5", result.getResponse().getHeader("X-Nacos-Hub-Entry-Id"));
                assertEquals("1.0.0",
                    result.getResponse().getHeader("X-Nacos-Skill-Resolved-Version"));
                assertEquals("no-store", result.getResponse().getHeader("Cache-Control"));
            });
        verify(shared).download("5", "1.0.0");
    }
    
    @Test
    void omittedNamespaceStillDefaultsToPublicAndDedicatedReadRoutesAreAbsent() throws Exception {
        mvc.perform(get(PATH).param("skillName", "normal")).andExpect(status().isOk());
        verify(service).getSkillDetail("public", "normal");
        for (String suffix : new String[] {"list", "detail", "content", "download"}) {
            mvc.perform(get("/v3/admin/ai/hub/skills/" + suffix)).andExpect(status().isNotFound());
        }
    }
}
