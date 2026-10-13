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

import com.alibaba.nacos.ai.form.hub.HubSkillReadEntryForm;
import com.alibaba.nacos.ai.form.hub.HubSkillReadListForm;
import com.alibaba.nacos.ai.service.hub.HubSkillReadService;
import com.alibaba.nacos.api.ai.model.hub.HubSkillContent;
import com.alibaba.nacos.api.ai.model.hub.HubSkillDetail;
import com.alibaba.nacos.api.ai.model.hub.HubSkillDownload;
import com.alibaba.nacos.api.ai.model.hub.HubSkillListRequest;
import com.alibaba.nacos.api.ai.model.hub.HubSkillSummary;
import com.alibaba.nacos.api.model.Page;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Only the internal read service, not raw source storage, is reachable from Admin HTTP. */
class HubSkillReadAdminControllerTest {
    
    private final HubSkillReadService service = mock(HubSkillReadService.class);
    private final HubSkillReadAdminController controller = new HubSkillReadAdminController(service);
    
    @Test
    void listDelegatesWithoutChangingCallerProvidedFilters() throws Exception {
        HubSkillReadListForm form = new HubSkillReadListForm();
        HubSkillListRequest request = form.prepareRequest();
        form.setQuery("find-skills");
        Page<HubSkillSummary> expected = new Page<>();
        when(service.list(org.mockito.ArgumentMatchers.any(HubSkillListRequest.class)))
            .thenReturn(expected);
        assertSame(expected, controller.list(form).getData());
        verify(service).list(org.mockito.ArgumentMatchers.any(HubSkillListRequest.class));
    }
    
    @Test
    void detailAndContentUseTheSameImmutableEntryId() throws Exception {
        HubSkillDetail detail = new HubSkillDetail();
        HubSkillContent content = new HubSkillContent();
        when(service.getDetail(305L)).thenReturn(detail);
        when(service.getContent(305L)).thenReturn(content);
        assertSame(detail, controller.detail(entryForm()).getData());
        assertSame(content, controller.content(entryForm()).getData());
        verify(service).getDetail(305L);
        verify(service).getContent(305L);
        HubSkillDownload download = new HubSkillDownload();
        when(service.download(305L)).thenReturn(download);
        assertSame(download, controller.download(entryForm()).getData());
    }
    
    private HubSkillReadEntryForm entryForm() {
        HubSkillReadEntryForm result = new HubSkillReadEntryForm();
        result.setHubEntryId("305");
        return result;
    }
}
