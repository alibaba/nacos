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

package com.alibaba.nacos.console.controller.v3.ai;

import com.alibaba.nacos.ai.form.skills.admin.SkillForm;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.skills.SkillDownload;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.console.proxy.ai.SkillProxy;
import com.alibaba.nacos.core.exception.NacosApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static com.alibaba.nacos.plugin.auth.constant.Constants.Tag.ALLOW_ANONYMOUS;
import static com.alibaba.nacos.plugin.auth.constant.Constants.Tag.ONLY_IDENTITY;

class ConsoleSharedSkillReadHttpTest {
    
    private final SkillProxy proxy = mock(SkillProxy.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ConsoleSkillController(proxy))
        .setControllerAdvice(new NacosApiExceptionHandler()).build();
    
    @Test
    void existingConsoleDownloadPreservesOriginalBytesAndSharedTransportHeaders() throws Exception {
        SkillDownload download = new SkillDownload();
        download.setHubEntryId(901L);
        download.setDisplayName("find-skills");
        download.setResolvedVersion("1.0.0");
        download.setZipBytes(new byte[] {0, (byte) 255});
        when(proxy.downloadSkillPackage(any())).thenReturn(download);
        mvc.perform(get("/v3/console/ai/skills/version/download")
            .param("namespaceId", AiConstants.Hub.NAMESPACE_ID).param("skillName", "901")
            .param("version", "1.0.0")).andExpect(status().isOk())
            .andExpect(content().bytes(download.getZipBytes()))
            .andExpect(result -> {
                assertEquals("901", result.getResponse().getHeader("X-Nacos-Hub-Entry-Id"));
                assertEquals("1.0.0",
                    result.getResponse().getHeader("X-Nacos-Skill-Resolved-Version"));
                assertEquals("no-store", result.getResponse().getHeader("Cache-Control"));
            });
        ArgumentCaptor<SkillForm> form = ArgumentCaptor.forClass(SkillForm.class);
        verify(proxy).downloadSkillPackage(form.capture());
        assertEquals(AiConstants.Hub.NAMESPACE_ID, form.getValue().getNamespaceId());
        assertEquals("901", form.getValue().getSkillName());
        assertEquals("1.0.0", form.getValue().getVersion());
    }
    
    @Test
    void onlyTheFourSharedReadMappingsAdmitAnonymousIdentity() {
        int sharedMethods = 0;
        for (Method method : ConsoleSkillController.class.getDeclaredMethods()) {
            if (!method.getName().contains("Shared")) {
                continue;
            }
            Secured secured = method.getAnnotation(Secured.class);
            assertTrue(Arrays.asList(secured.tags()).contains(ALLOW_ANONYMOUS));
            assertTrue(Arrays.asList(secured.tags()).contains(ONLY_IDENTITY));
            sharedMethods++;
        }
        assertEquals(4, sharedMethods);
    }
    
    @Test
    void dedicatedConsoleHubReadRoutesAreAbsent() throws Exception {
        for (String suffix : new String[] {"list", "detail", "content", "download"}) {
            mvc.perform(get("/v3/console/ai/hub/skills/" + suffix))
                .andExpect(status().isNotFound());
        }
    }
}
