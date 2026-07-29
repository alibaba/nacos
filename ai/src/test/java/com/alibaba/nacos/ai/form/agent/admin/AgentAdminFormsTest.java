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

package com.alibaba.nacos.ai.form.agent.admin;

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.agent.AgentDraftCreateRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentDraftUpdateRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentLabelsUpdateRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentProvider;
import com.alibaba.nacos.api.ai.model.agent.AgentUpdateRequest;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentAdminFormsTest {
    
    private static final String AGENT_NAME = "Demo Agent";
    
    private static final String VERSION = "1.0.0";
    
    @Test
    void testDraftCreateFormBuildsCompleteRequest() throws NacosApiException {
        AgentDraftCreateForm form = new AgentDraftCreateForm();
        form.setAgentName(AGENT_NAME);
        form.setVersion(VERSION);
        form.setDisplayName("Demo");
        form.setDescription("description");
        form.setIconUrl("https://example.com/icon.png");
        form.setProvider("{\"name\":\"Nacos\",\"url\":\"https://nacos.io\"}");
        form.setTags("[\"assistant\"]");
        form.setExtensions("{\"region\":\"east\"}");
        form.setCallInterfaces("[]");
        form.setAuthor("alice");
        form.setChangeDescription("initial draft");
        form.setBasedOnVersion(null);
        
        assertEquals("Demo", form.getDisplayName());
        assertEquals("description", form.getDescription());
        assertEquals("https://example.com/icon.png", form.getIconUrl());
        assertEquals("{\"name\":\"Nacos\",\"url\":\"https://nacos.io\"}", form.getProvider());
        assertEquals("[\"assistant\"]", form.getTags());
        assertEquals("{\"region\":\"east\"}", form.getExtensions());
        assertEquals("[]", form.getCallInterfaces());
        assertEquals("alice", form.getAuthor());
        assertEquals("initial draft", form.getChangeDescription());
        assertNull(form.getBasedOnVersion());
        
        AgentDraftCreateRequest request = form.toRequest();
        assertEquals(AGENT_NAME, request.getAgentName());
        assertEquals(VERSION, request.getVersion());
        assertEquals("Nacos", request.getProvider().getName());
        assertEquals("assistant", request.getTags().get(0));
        assertEquals("east", request.getExtensions().get("region"));
    }
    
    @Test
    void testDraftCreateFormSupportsBasedOnVersion() throws NacosApiException {
        AgentDraftCreateForm form = new AgentDraftCreateForm();
        form.setAgentName(AGENT_NAME);
        form.setVersion("2.0.0");
        form.setBasedOnVersion(VERSION);
        
        assertEquals(VERSION, form.getBasedOnVersion());
        assertEquals(VERSION, form.toRequest().getBasedOnVersion());
    }
    
    @Test
    void testDraftUpdateFormBuildsRequest() throws NacosApiException {
        AgentDraftUpdateForm form = new AgentDraftUpdateForm();
        form.setAgentName(AGENT_NAME);
        form.setVersion(VERSION);
        form.setCallInterfaces("[]");
        form.setChangeDescription("updated");
        
        assertEquals("[]", form.getCallInterfaces());
        assertEquals("updated", form.getChangeDescription());
        
        AgentDraftUpdateRequest request = form.toRequest();
        assertEquals(AGENT_NAME, request.getAgentName());
        assertEquals(VERSION, request.getVersion());
        assertEquals(0, request.getCallInterfaces().size());
        assertEquals("updated", request.getChangeDescription());
    }
    
    @Test
    void testLabelsUpdateFormBuildsRequest() throws NacosApiException {
        AgentLabelsUpdateForm form = new AgentLabelsUpdateForm();
        form.setAgentName(AGENT_NAME);
        form.setLabels("{\"stable\":\"1.0.0\"}");
        
        assertEquals("{\"stable\":\"1.0.0\"}", form.getLabels());
        
        AgentLabelsUpdateRequest request = form.toRequest();
        assertEquals(VERSION, request.getLabels().get("stable"));
    }
    
    @Test
    void testAgentUpdateFormBuildsCompleteRequest() throws NacosApiException {
        AgentUpdateForm form = new AgentUpdateForm();
        form.setAgentName(AGENT_NAME);
        form.setDisplayName("Demo");
        form.setDescription("description");
        form.setIconUrl("https://example.com/icon.png");
        form.setProvider("{\"name\":\"Nacos\"}");
        form.setTags("[\"assistant\"]");
        form.setExtensions("{\"region\":\"east\"}");
        form.setStatus(AiConstants.Agent.RESOURCE_STATUS_ENABLE);
        
        assertEquals("Demo", form.getDisplayName());
        assertEquals("description", form.getDescription());
        assertEquals("https://example.com/icon.png", form.getIconUrl());
        assertEquals("{\"name\":\"Nacos\"}", form.getProvider());
        assertEquals("[\"assistant\"]", form.getTags());
        assertEquals("{\"region\":\"east\"}", form.getExtensions());
        assertEquals(AiConstants.Agent.RESOURCE_STATUS_ENABLE, form.getStatus());
        
        AgentUpdateRequest request = form.toRequest();
        assertEquals("Demo", request.getDisplayName());
        assertEquals("Nacos", request.getProvider().getName());
        assertEquals("assistant", request.getTags().get(0));
        assertEquals("east", request.getExtensions().get("region"));
    }
    
    @Test
    void testListFormDefaultsNamespaceAndValidatesOrder() throws NacosApiException {
        AgentListForm form = new AgentListForm();
        form.setNamespaceId(null);
        form.setAgentName("Demo");
        form.setOrderBy(null);
        form.validate();
        
        assertEquals("public", form.getNamespaceId());
        assertEquals("Demo", form.getAgentName());
        assertNull(form.getOrderBy());
        
        form.setOrderBy("download_count");
        form.validate();
        assertEquals("download_count", form.getOrderBy());
        
        form.setOrderBy("unsupported");
        assertThrows(IllegalArgumentException.class, form::validate);
    }
    
    @Test
    void testRuntimeEndpointFormSupportsOptionalVersion() throws NacosApiException {
        AgentRuntimeEndpointForm form = new AgentRuntimeEndpointForm();
        form.setAgentName(AGENT_NAME);
        form.setProtocol("a2a");
        form.validate();
        
        assertEquals("a2a", form.getProtocol());
        assertNull(form.getVersion());
        
        form.setVersion(VERSION);
        form.validate();
        assertEquals(VERSION, form.getVersion());
        
        form.setVersion("invalid");
        assertThrows(IllegalArgumentException.class, form::validate);
    }
    
    @Test
    void testVersionListFormAcceptsEveryLifecycleStatus() throws NacosApiException {
        AgentVersionListForm form = new AgentVersionListForm();
        form.setAgentName(AGENT_NAME);
        String[] statuses = {null, AiConstants.Agent.VERSION_STATUS_DRAFT,
            AiConstants.Agent.VERSION_STATUS_REVIEWING,
            AiConstants.Agent.VERSION_STATUS_REVIEWED,
            AiConstants.Agent.VERSION_STATUS_ONLINE,
            AiConstants.Agent.VERSION_STATUS_OFFLINE};
        for (String status : statuses) {
            form.setStatus(status);
            form.validate();
            assertEquals(status, form.getStatus());
        }
        
        form.setStatus("invalid");
        assertThrows(IllegalArgumentException.class, form::validate);
    }
    
    @Test
    void testJsonParserClassOverload() throws NacosApiException {
        assertNull(AgentAdminFormJsonParser.parseOptional("provider", " ",
            AgentProvider.class));
        AgentProvider provider = AgentAdminFormJsonParser.parseOptional("provider",
            "{\"name\":\"Nacos\"}", AgentProvider.class);
        assertEquals("Nacos", provider.getName());
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> AgentAdminFormJsonParser.parseOptional("provider", "{", AgentProvider.class));
        assertEquals("Request parameter `provider` is not valid JSON.",
            exception.getMessage());
    }
    
    @Test
    void testJsonParserTypeReferenceOverload() throws NacosApiException {
        TypeReference<List<String>> type = new TypeReference<List<String>>() {
        };
        assertNull(AgentAdminFormJsonParser.parseOptional("tags", null, type));
        assertEquals("assistant",
            AgentAdminFormJsonParser.parseOptional("tags", "[\"assistant\"]", type).get(0));
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> AgentAdminFormJsonParser.parseOptional("tags", "[", type));
        assertEquals("Request parameter `tags` is not valid JSON.",
            exception.getMessage());
    }
}
