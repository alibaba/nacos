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

package com.alibaba.nacos.ai.service.hub;

import com.alibaba.nacos.ai.form.hub.HubResourceTransferForm;
import com.alibaba.nacos.ai.form.hub.HubShareResourceForm;
import com.alibaba.nacos.ai.model.hub.HubResourceType;
import com.alibaba.nacos.api.ai.model.hub.HubResourceTransferRequest;
import com.alibaba.nacos.api.ai.model.hub.HubResourceTransferResult;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Typed transfer form/facade validation precedes any source, namespace or storage IO. */
class HubResourceTransferFacadeTest {
    
    private final HubResourceTransferService service = mock(HubResourceTransferService.class);
    private final HubResourceTransferFacade facade = new HubResourceTransferFacade(service);
    private ConfigurableEnvironment oldEnvironment;
    
    @BeforeEach
    void setUp() {
        oldEnvironment = EnvUtil.getEnvironment();
        EnvUtil.setEnvironment(new StandardEnvironment());
    }
    
    @AfterEach
    void restoreEnvironment() {
        EnvUtil.setEnvironment(oldEnvironment);
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"mcp", "agent", "prompt", "agentspec"})
    void importUsesTypedDefaultDraftAndSafePublicResult(String type) throws Exception {
        HubResourceTransferRequest request = request();
        when(service.importFromHub(
            eq(HubResourceType.valueOf(type.toUpperCase(java.util.Locale.ROOT))), eq(501L), any()))
            .thenReturn(new HubResourceDraftTransferWriter.CreatedDraft(901L, "0.0.1"));
        HubResourceTransferResult result = facade.importFromHub(type, request);
        assertEquals(type, result.getResourceType());
        assertEquals("0.0.1", result.getDraftVersion());
        assertEquals("draft", result.getStatus());
        assertEquals("HUB", result.getOrigin());
        assertEquals(501L, result.getHubEntryId());
    }
    
    @Test
    void exactAgentSourceNameIsNotTrimmedBySharedForm() throws Exception {
        HubShareResourceForm form = new HubShareResourceForm();
        form.setSourceNamespaceId("source");
        form.setSourceName(" Agent name ");
        form.setSourceVersion("1.0.0");
        assertEquals(" Agent name ", form.prepareRequest().getSourceName());
    }
    
    @Test
    void malformedIdsAndMcpDependencyJsonFailAsControlledParameterErrors() {
        HubResourceTransferForm form = new HubResourceTransferForm();
        form.setHubEntryId("01");
        assertThrows(NacosException.class, form::prepareRequest);
        form.setHubEntryId("501");
        for (String invalid : new String[] {"null", "[]", "{", "true"}) {
            form.setMcpRemoteServerConfig(invalid);
            assertThrows(NacosException.class, form::prepareRequest);
        }
    }
    
    private HubResourceTransferRequest request() {
        HubResourceTransferRequest request = new HubResourceTransferRequest();
        request.setHubEntryId(501L);
        request.setTargetNamespaceId("target");
        request.setTargetName("new-name");
        return request;
    }
}
