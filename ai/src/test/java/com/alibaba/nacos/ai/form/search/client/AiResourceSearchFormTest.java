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

package com.alibaba.nacos.ai.form.search.client;

import com.alibaba.nacos.ai.form.agentspecs.client.AgentSpecSearchForm;
import com.alibaba.nacos.ai.form.mcp.client.McpSearchForm;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.NacosForm;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for AI Resource Search forms.
 */
class AiResourceSearchFormTest {
    
    @Test
    void genericFormShouldApplyDefaultsAndNormalizeFilters() throws Exception {
        AiResourceSearchForm form = new AiResourceSearchForm();
        form.setResourceTypes(Arrays.asList(" skill ", "", "skill", null, "agent"));
        form.setTagsAll(Arrays.asList(" research ", "research"));
        form.setCapabilitiesAny(Arrays.asList(null, "streaming"));
        
        form.validate();
        
        assertEquals("public", form.getNamespaceId());
        assertEquals(20, form.getLimit());
        assertEquals(Arrays.asList("skill", "agent"), form.getResourceTypes());
        assertEquals(List.of("research"), form.getTagsAll());
        assertEquals(List.of("streaming"), form.getCapabilitiesAny());
    }
    
    @Test
    void genericFormShouldRejectInvalidBounds() {
        AiResourceSearchForm form = new AiResourceSearchForm();
        form.setLimit(0);
        assertInvalid(form);
        form.setLimit(101);
        assertInvalid(form);
        
        form = new AiResourceSearchForm();
        form.setQuery("q".repeat(1025));
        assertInvalid(form);
        form.setQuery(null);
        form.setCursor("c".repeat(2049));
        assertInvalid(form);
        
        form = new AiResourceSearchForm();
        form.setResourceTypes(uniqueValues(33));
        assertInvalid(form);
    }
    
    @Test
    void resourceSpecificFormsShouldNormalizeAndRejectInvalidBounds() throws Exception {
        AiResourcePageSearchForm form = new AiResourcePageSearchForm();
        form.setTagsAll(Arrays.asList(" tag ", "tag", null));
        form.validate();
        assertEquals("public", form.getNamespaceId());
        assertEquals(List.of("tag"), form.getTagsAll());
        
        form = new AiResourcePageSearchForm();
        form.setQuery("q".repeat(1025));
        assertInvalid(form);
        form.setQuery(null);
        form.setTagsAll(uniqueValues(33));
        assertInvalid(form);
    }
    
    @Test
    void mcpFormShouldNormalizeProtocolAndCapabilityFilters() throws Exception {
        McpSearchForm form = new McpSearchForm();
        form.setProtocolsAny(Arrays.asList(" stdio ", "stdio", null));
        form.setCapabilitiesAny(Arrays.asList(" tool ", "tool"));
        
        form.validate();
        
        assertEquals(List.of("stdio"), form.getProtocolsAny());
        assertEquals(List.of("tool"), form.getCapabilitiesAny());
        
        form = new McpSearchForm();
        form.setProtocolsAny(uniqueValues(33));
        assertInvalid(form);
    }
    
    @Test
    void agentSpecFormShouldPreserveKeywordAndNormalizeTags() throws Exception {
        AgentSpecSearchForm form = new AgentSpecSearchForm();
        form.setKeyword("planner");
        form.setTagsAll(Arrays.asList(" workflow ", "workflow", null));
        
        form.validate();
        
        assertEquals("public", form.getNamespaceId());
        assertEquals("planner", form.getKeyword());
        assertEquals(List.of("workflow"), form.getTagsAll());
        
        form.setKeyword("q".repeat(1025));
        assertInvalid(form);
    }
    
    private List<String> uniqueValues(int size) {
        return IntStream.range(0, size).mapToObj(value -> "value-" + value)
            .collect(Collectors.toList());
    }
    
    private void assertInvalid(NacosForm form) {
        assertThrows(NacosApiException.class, form::validate);
    }
}
