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

import com.alibaba.nacos.ai.form.mcp.client.McpSearchForm;
import com.alibaba.nacos.ai.service.search.AiResourceSearchApplicationService;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.core.model.form.PageForm;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link McpClientController}.
 */
class McpClientControllerTest {
    
    @Test
    void searchShouldValidateDefaultsAndDelegate() throws NacosException {
        AiResourceSearchApplicationService service =
            mock(AiResourceSearchApplicationService.class);
        McpClientController controller = new McpClientController(service);
        McpSearchForm form = new McpSearchForm();
        PageForm pageForm = new PageForm();
        Page<McpServerBasicInfo> page = new Page<>();
        McpServerBasicInfo item = new McpServerBasicInfo();
        item.setName("research-mcp");
        page.setPageItems(Collections.singletonList(item));
        when(service.searchMcpServers(form, 1, 100)).thenReturn(page);
        
        Result<Page<McpServerBasicInfo>> result = controller.search(form, pageForm);
        
        assertEquals("public", form.getNamespaceId());
        assertEquals("research-mcp", result.getData().getPageItems().get(0).getName());
        verify(service).searchMcpServers(form, 1, 100);
    }
}
