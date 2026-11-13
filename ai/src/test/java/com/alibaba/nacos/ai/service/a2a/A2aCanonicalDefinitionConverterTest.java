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
import com.alibaba.nacos.api.ai.model.a2a.AgentSkill;
import com.alibaba.nacos.api.utils.json.JsonUtils;
import com.alibaba.nacos.api.ai.model.a2a.AgentInterface;
import com.alibaba.nacos.api.ai.model.a2a.AgentProvider;
import com.alibaba.nacos.api.ai.model.agent.admin.AgentDraftCreateRequest;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.alibaba.nacos.ai.model.agent.AgentVersionContent;
import com.alibaba.nacos.ai.service.agent.storage.AgentVersionContentSerializer;
import java.util.ArrayList;

import java.util.Arrays;
import java.util.Collections;
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
        assertEquals(2,
            result.getCallInterfaces().get(0).getEndpointSets().get(0).getEndpoints().size());
        assertEquals("https://example.com:443/a2a",
            result.getCallInterfaces().get(0).getEndpointSets().get(0).getEndpoints().get(0)
                .getUri());
        assertEquals("HTTP+JSON",
            result.getCallInterfaces().get(0).getEndpointSets().get(0).getEndpoints().get(0)
                .getTransport());
        Map<?, ?> descriptor = (Map<?, ?>) result.getCallInterfaces().get(0)
            .getNativeDescriptor();
        assertEquals("Research", descriptor.get("description"));
        assertEquals("text/plain", ((List<?>) descriptor.get("defaultInputModes")).get(0));
        assertFalse(descriptor.containsKey("registrationType"));
        assertEquals(3, card.getSupportedInterfaces().size());
        assertNull(card.getUrl());
    }
    
    @Test
    void shouldPreserveSkillRequirementsInIsolatedNativeDescriptor() throws NacosException {
        AgentCard source = card();
        AgentSkill skill = JsonUtils.toObj("{\"id\":\"protected\",\"securityRequirements\":[{"
            + "\"schemes\":{\"oauth2\":{\"list\":[\"skill-read\"]}}}]}", AgentSkill.class);
        source.setSkills(Collections.singletonList(skill));
        AgentDraftCreateRequest request = converter.convert(NAMESPACE_ID, source, "URL", true);
        AgentCard stored = JsonUtils.toObj(JsonUtils.toJson(request.getCallInterfaces().get(0)
            .getNativeDescriptor()), AgentCard.class);
        assertEquals(source.getSkills(), stored.getSkills());
        assertNull(stored.getSecurityRequirements());
        assertNull(stored.getSecurity());
        stored.getSkills().get(0).getSecurityRequirements().get(0).getSchemes().get("oauth2")
            .getList().clear();
        assertEquals(Collections.singletonList("skill-read"), skill.getSecurityRequirements()
            .get(0).getSchemes().get("oauth2").getList());
    }
    
    @Test
    void shouldNormalizeLegacyCardAndServiceSourceOrder() throws NacosException {
        AgentCard card = card();
        card.setSupportedInterfaces(null);
        card.setUrl("https://EXAMPLE.com/a2a");
        card.setPreferredTransport("HTTP+JSON");
        card.setProtocolVersion("0.3");
        
        AgentDraftCreateRequest result =
            converter.convert(NAMESPACE_ID, card, " service ".trim(),
                false);
        
        assertEquals(Arrays.asList(EndpointSource.RUNTIME, EndpointSource.DECLARED),
            result.getCallInterfaces().get(0).getEndpointSourceOrder());
        assertEquals("https://example.com:443/a2a",
            result.getCallInterfaces().get(0).getEndpointSets().get(0).getEndpoints().get(0)
                .getUri());
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
    
    @ParameterizedTest
    @CsvSource({
        "false,URL,f1dc5ff7cdb9e74e3b83b5ca1722f9d97dc8f777d2156a161e3e3879534a9caa",
        "false,SERVICE,3e7e63cc6a1f37ede9c34219f861a42b8ab571edfed151497bc7a2902958d34e",
        "true,URL,21d9051582defe07e6e13f936b39c7e33ac7ba3b55db4d174835c0973989472a",
        "true,SERVICE,fb770b2cf62e34273a9c129a26da0cf0c2d6e210576c1fe3b8524d29efbe04f4"
    })
    void preservesPreBuilderMigrationContentDigest(boolean legacy, String type, String digest)
        throws Exception {
        // Digests recorded with the converter at 7c546cfda, before extraction into API utilities.
        AgentCard card = new AgentCard();
        card.setName("fixture-agent");
        card.setVersion("1.0.0");
        card.setDescription("fixture");
        card.setUrl("https://example.com/rpc");
        card.setPreferredTransport("JSONRPC");
        card.setProtocolVersion("0.3");
        if (!legacy) {
            List<AgentInterface> interfaces = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                AgentInterface address = agentInterface(i < 2 ? "https://example.com/" + i
                    : "http://second.example/run", "JSONRPC", "1." + i);
                address.setTenant(i == 2 ? "" : "opaque" + i);
                interfaces.add(address);
            }
            card.setSupportedInterfaces(interfaces);
        }
        for (boolean includeMetadata : new boolean[] {false, true}) {
            AgentDraftCreateRequest converted =
                converter.convert("public", card, type, includeMetadata);
            AgentVersionContent content = new AgentVersionContent(converted.getCallInterfaces());
            assertEquals("sha256:" + digest,
                AgentVersionContentSerializer.serialize(content).getContentDigest());
            assertEquals(includeMetadata ? "fixture" : null, converted.getDescription());
            converted.getCallInterfaces().get(0).getEndpointSets().get(0).getEndpoints()
                .forEach(endpoint -> {
                    assertNull(endpoint.getMetadata());
                    assertEquals(0, endpoint.getPriority());
                });
        }
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
