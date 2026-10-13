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

import com.alibaba.nacos.ai.form.hub.HubSkillReadEntryForm;
import com.alibaba.nacos.ai.form.hub.HubSkillReadListForm;
import com.alibaba.nacos.api.ai.model.hub.HubSkillContent;
import com.alibaba.nacos.api.ai.model.hub.HubSkillDetail;
import com.alibaba.nacos.api.ai.model.hub.HubSkillDownload;
import com.alibaba.nacos.api.ai.model.hub.HubSkillListRequest;
import com.alibaba.nacos.api.ai.model.hub.HubSkillSummary;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.console.proxy.ai.HubSkillReadProxy;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Console read endpoints must always follow the deployment-mode proxy. */
class ConsoleHubSkillReadControllerTest {
    
    private final HubSkillReadProxy proxy = mock(HubSkillReadProxy.class);
    private final ConsoleHubSkillReadController controller =
        new ConsoleHubSkillReadController(proxy);
    
    @Test
    void listUsesTheProxyWithOriginalFilters() throws Exception {
        HubSkillReadListForm form = new HubSkillReadListForm();
        form.setPageSize("5");
        Page<HubSkillSummary> expected = new Page<>();
        when(proxy.list(org.mockito.ArgumentMatchers.any(HubSkillListRequest.class)))
            .thenReturn(expected);
        assertSame(expected, controller.list(form).getData());
        verify(proxy).list(org.mockito.ArgumentMatchers.any(HubSkillListRequest.class));
    }
    
    @Test
    void detailAndContentDoNotUseOrdinarySkillEndpoints() throws Exception {
        HubSkillDetail detail = new HubSkillDetail();
        HubSkillContent content = new HubSkillContent();
        when(proxy.detail(901L)).thenReturn(detail);
        when(proxy.content(901L)).thenReturn(content);
        assertSame(detail, controller.detail(entryForm()).getData());
        assertSame(content, controller.content(entryForm()).getData());
        verify(proxy).detail(901L);
        verify(proxy).content(901L);
    }
    
    private HubSkillReadEntryForm entryForm() {
        HubSkillReadEntryForm result = new HubSkillReadEntryForm();
        result.setHubEntryId("901");
        return result;
    }
    
    @Test
    void downloadUsesModeProxyWithZipHeadersAndPinnedVersion() throws Exception {
        HubSkillDownload download = new HubSkillDownload();
        download.setHubEntryId(901L);
        download.setDisplayName("find-skills");
        download.setSharedVersion("1.0.0");
        download.setZipBytes(new byte[] {0, (byte) 255});
        when(proxy.download(901L)).thenReturn(download);
        ResponseEntity<byte[]> response = controller.download(entryForm());
        org.junit.jupiter.api.Assertions.assertArrayEquals(download.getZipBytes(),
            response.getBody());
        org.junit.jupiter.api.Assertions.assertEquals("application/zip",
            response.getHeaders().getContentType().toString());
        org.junit.jupiter.api.Assertions.assertEquals("1.0.0",
            response.getHeaders().getFirst("X-Nacos-Skill-Resolved-Version"));
        org.junit.jupiter.api.Assertions.assertEquals("901",
            response.getHeaders().getFirst("X-Nacos-Hub-Entry-Id"));
        org.junit.jupiter.api.Assertions.assertEquals("no-store",
            response.getHeaders().getCacheControl());
        verify(proxy).download(901L);
    }
    
}
