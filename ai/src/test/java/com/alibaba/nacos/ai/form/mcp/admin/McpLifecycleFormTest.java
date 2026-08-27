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

package com.alibaba.nacos.ai.form.mcp.admin;

import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class McpLifecycleFormTest {
    
    @Test
    void baseFormShouldDefaultNamespaceAndRequireName() throws NacosApiException {
        McpLifecycleForm form = new McpLifecycleForm();
        form.setMcpName("weather");
        
        form.validate();
        
        assertEquals(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, form.getNamespaceId());
        assertThrows(NacosApiException.class, () -> {
            form.setMcpName(" ");
            form.validate();
        });
    }
    
    @Test
    void baseFormShouldRejectInvalidNamespace() {
        McpLifecycleForm form = new McpLifecycleForm();
        form.setNamespaceId("invalid namespace");
        form.setMcpName("weather");
        
        assertThrows(NacosApiException.class, form::validate);
    }
    
    @Test
    void versionAndDraftFormsShouldRequireExactVersionAndServerContent() {
        McpLifecycleVersionForm versionForm = new McpLifecycleVersionForm();
        versionForm.setMcpName("weather");
        assertThrows(NacosApiException.class, versionForm::validate);
        
        McpLifecycleDraftForm draftForm = new McpLifecycleDraftForm();
        draftForm.setMcpName("weather");
        draftForm.setVersion("1.0.0");
        assertThrows(NacosApiException.class, draftForm::validate);
    }
    
    @Test
    void versionListShouldAcceptKnownStatusCaseInsensitively() throws NacosApiException {
        McpLifecycleVersionListForm form = new McpLifecycleVersionListForm();
        form.setMcpName("weather");
        form.validate();
        assertNull(form.getStatus());
        
        form.setStatus(AiResourceConstants.VERSION_STATUS_ONLINE.toUpperCase(Locale.ROOT));
        form.validate();
        assertEquals(AiResourceConstants.VERSION_STATUS_ONLINE, form.getStatus());
        
        form.setStatus("unknown");
        assertThrows(NacosApiException.class, form::validate);
    }
    
    @Test
    void labelsFormShouldCarryReplacementPayload() throws NacosApiException {
        McpLifecycleLabelsForm form = new McpLifecycleLabelsForm();
        form.setMcpName("weather");
        form.setLabels("{\"stable\":\"1.0.0\"}");
        
        form.validate();
        
        assertEquals("{\"stable\":\"1.0.0\"}", form.getLabels());
    }
}
