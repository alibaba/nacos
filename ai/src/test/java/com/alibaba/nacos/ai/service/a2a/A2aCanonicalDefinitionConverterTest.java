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

package com.alibaba.nacos.ai.service.a2a;

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentInterface;
import com.alibaba.nacos.api.ai.model.a2a.AgentProvider;
import com.alibaba.nacos.api.ai.model.agent.AgentDraftCreateRequest;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class A2aCanonicalDefinitionConverterTest {
    
    private static final String NAMESPACE_ID = "tenant-a";
    
    private A2aCanonicalDefinitionConverter converter;
    
    @BeforeEach
    void setUp() {
        converter = new A2aCanonicalDefinitionConverter();
    }
    
    @Test
    void shouldConvertCompleteCardWithoutMutatingSource() throws NacosException {
        AgentCard card = card();
        AgentInterface duplicate = agentInterface("https://EXAMPLE.com/a2a", "HTTP+JSON",
            "0.3");
        card.setSupportedInterfaces(Arrays.asList(card.getSupportedInterfaces().get(0), duplicate,
            agentInterface("https://example.com:8443/stream", "JSONRPC", "0.3")));
        
        AgentDraftCreateRequest result = converter.convert(NAMESPACE_ID, card, "url", true);
        
        assertEquals("research-agent", result.getAgentName());
        assertEquals("1.0.0", result.getVersion());
        assertEquals("Research", result.getDescription());
        assertEquals("Example", result.getProvider().getName());
        assertEquals(Arrays.asList(EndpointSource.DECLARED, EndpointSource.RUNTIME),
            result.getCallInterfaces().get(0).getEndpointSourceOrder());
        assertEquals(2, result.getCallInterfaces().get(0).getDeclaredEndpoints().size());
        assertEquals("https://example.com:443/a2a",
            result.getCallInterfaces().get(0).getDeclaredEndpoints().get(0).getUri());
        assertEquals("HTTP+JSON",
            result.getCallInterfaces().get(0).getDeclaredEndpoints().get(0).getTransport());
        Map<?, ?> descriptor = (Map<?, ?>) result.getCallInterfaces().get(0)
            .getNativeDescriptor();
        assertEquals("Research", descriptor.get("description"));
        assertEquals("text/plain", ((List<?>) descriptor.get("defaultInputModes")).get(0));
        assertFalse(descriptor.containsKey("registrationType"));
        assertEquals(3, card.getSupportedInterfaces().size());
        assertNull(card.getUrl());
    }
    
    @Test
    void shouldNormalizeLegacyCardAndServiceSourceOrder() throws NacosException {
        AgentCard card = card();
        card.setSupportedInterfaces(null);
        card.setUrl("https://EXAMPLE.com/a2a");
        card.setPreferredTransport("HTTP+JSON");
        card.setProtocolVersion("0.3");
        
        AgentDraftCreateRequest result = converter.convert(NAMESPACE_ID, card, " service ".trim(),
            false);
        
        assertEquals(Arrays.asList(EndpointSource.RUNTIME, EndpointSource.DECLARED),
            result.getCallInterfaces().get(0).getEndpointSourceOrder());
        assertEquals("https://example.com:443/a2a",
            result.getCallInterfaces().get(0).getDeclaredEndpoints().get(0).getUri());
        assertNull(result.getDescription());
        assertNull(result.getProvider());
        assertNull(card.getSupportedInterfaces());
    }
    
    @Test
    void shouldRejectNullCardInvalidRegistrationAndInvalidInterface()
        throws NacosApiException {
        assertThrows(IllegalArgumentException.class,
            () -> converter.convert(NAMESPACE_ID, null, "URL", true));
        assertThrows(NacosApiException.class,
            () -> converter.normalizeRegistrationType(null, null));
        assertThrows(NacosApiException.class,
            () -> converter.normalizeRegistrationType(" URL ", null));
        assertEquals(AiConstants.A2a.A2A_ENDPOINT_TYPE_URL,
            converter.normalizeRegistrationType("", "URL"));
        
        AgentCard invalid = card();
        invalid.getSupportedInterfaces().get(0).setUrl("relative");
        assertThrows(IllegalArgumentException.class,
            () -> converter.convert(NAMESPACE_ID, invalid, "URL", false));
    }
    
    private AgentCard card() {
        AgentCard result = new AgentCard();
        result.setName("research-agent");
        result.setVersion("1.0.0");
        result.setDescription("Research");
        result.setIconUrl("https://example.com/icon.png");
        result.setDefaultInputModes(Arrays.asList("text/plain"));
        result.setDefaultOutputModes(Arrays.asList("application/json"));
        AgentProvider provider = new AgentProvider();
        provider.setOrganization("Example");
        provider.setUrl("https://example.com");
        result.setProvider(provider);
        result.setSupportedInterfaces(Arrays.asList(
            agentInterface("https://EXAMPLE.com/a2a", "HTTP+JSON", "0.3")));
        return result;
    }
    
    private AgentInterface agentInterface(String url, String binding, String version) {
        AgentInterface result = new AgentInterface();
        result.setUrl(url);
        result.setProtocolBinding(binding);
        result.setProtocolVersion(version);
        return result;
    }
}
