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

package com.alibaba.nacos.api.ai.utils;

import com.alibaba.nacos.api.ai.model.a2a.AgentCapabilities;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentInterface;
import com.alibaba.nacos.api.exception.NacosException;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class A2aAgentCardUtilsTest {
    
    @Test
    void legacyFieldsBecomeSupportedInterfacesAndFillOnlyMissingDefaults() throws Exception {
        AgentCard card = legacy();
        A2aAgentCardUtils.validateAgentCard(card);
        assertEquals("", card.getDescription());
        assertNotNull(card.getCapabilities());
        assertTrue(card.getDefaultInputModes().isEmpty());
        assertTrue(card.getDefaultOutputModes().isEmpty());
        assertTrue(card.getSkills().isEmpty());
        assertEquals(1, card.getSupportedInterfaces().size());
        AgentInterface address = card.getSupportedInterfaces().get(0);
        assertEquals(card.getUrl(), address.getUrl());
        assertEquals("JSONRPC", address.getProtocolBinding());
        assertEquals("JSONRPC", address.getTransport());
        assertEquals("0.3", address.getProtocolVersion());
        assertTrue(card.getAdditionalInterfaces().isEmpty());
    }
    
    @Test
    void v1InterfacesTakePrecedenceAndPreserveMeaningfulCardFields() throws Exception {
        AgentCard card = legacy();
        card.setDescription("description");
        card.setDefaultInputModes(Collections.singletonList("text/plain"));
        card.setDefaultOutputModes(Collections.singletonList("application/json"));
        card.setSkills(Collections.emptyList());
        AgentInterface first = address("https://first.example", "HTTP+JSON", "1.0");
        AgentInterface second = address("https://second.example", "GRPC", "1.1");
        card.setSupportedInterfaces(Arrays.asList(first, second));
        A2aAgentCardUtils.validateAgentCard(card);
        assertEquals(first.getUrl(), card.getUrl());
        assertEquals("HTTP+JSON", card.getPreferredTransport());
        assertEquals("1.0", card.getProtocolVersion());
        assertEquals(Collections.singletonList(second), card.getAdditionalInterfaces());
        assertEquals("description", card.getDescription());
        assertEquals(Collections.singletonList("text/plain"), card.getDefaultInputModes());
        assertEquals(Collections.singletonList("application/json"), card.getDefaultOutputModes());
    }
    
    @Test
    void legacyAdditionalInterfacesInheritOnlyMissingVersionAndBinding() throws Exception {
        AgentCard card = legacy();
        AgentInterface inherited = address("https://additional.example", null, null);
        AgentInterface custom = address("https://grpc.example", "GRPC", "1.0");
        custom.setTransport(null);
        card.setAdditionalInterfaces(Arrays.asList(null, inherited, custom));
        A2aAgentCardUtils.validateAgentCard(card);
        assertEquals(3, card.getSupportedInterfaces().size());
        assertEquals("0.3", inherited.getProtocolVersion());
        assertEquals("JSONRPC", inherited.getProtocolBinding());
        assertEquals("1.0", custom.getProtocolVersion());
        assertEquals("GRPC", custom.getTransport());
    }
    
    @Test
    void transportAliasNormalizesBothWaysAndInvalidSupportedEntriesAreFiltered() throws Exception {
        AgentCard card = legacy();
        AgentInterface alias = address("https://alias.example", null, "1.0");
        alias.setTransport("GRPC");
        card.setSupportedInterfaces(Arrays.asList(null, new AgentInterface(), alias));
        A2aAgentCardUtils.validateAgentCard(card);
        assertEquals(Collections.singletonList(alias), card.getSupportedInterfaces());
        assertEquals("GRPC", card.getPreferredTransport());
        AgentCard fallback = legacy();
        fallback.setSupportedInterfaces(Collections.singletonList(new AgentInterface()));
        A2aAgentCardUtils.validateAgentCard(fallback);
        assertEquals("https://legacy.example", fallback.getUrl());
    }
    
    @Test
    void extendedCapabilityWinsOtherwiseHistoricalFlagFillsIt() throws Exception {
        AgentCard card = legacy();
        card.setSupportsAuthenticatedExtendedCard(true);
        A2aAgentCardUtils.validateAgentCard(card);
        assertTrue(card.getCapabilities().getExtendedAgentCard());
        AgentCapabilities capabilities = new AgentCapabilities();
        capabilities.setExtendedAgentCard(false);
        card.setCapabilities(capabilities);
        A2aAgentCardUtils.validateAgentCard(card);
        assertFalse(card.getSupportsAuthenticatedExtendedCard());
    }
    
    @Test
    void missingIdentityOrBothInterfaceFormatsFailAsControlledErrors() {
        assertDoesNotThrow(() -> A2aAgentCardUtils.normalizeAgentCard(null));
        assertThrows(NacosException.class, () -> A2aAgentCardUtils.validateAgentCard(null));
        AgentCard card = legacy();
        card.setName("");
        assertThrows(NacosException.class, () -> A2aAgentCardUtils.validateAgentCard(card));
        card.setName("demo");
        card.setVersion(null);
        assertThrows(NacosException.class, () -> A2aAgentCardUtils.validateAgentCard(card));
        card.setVersion("1.0.0");
        card.setProtocolVersion(null);
        assertThrows(NacosException.class, () -> A2aAgentCardUtils.validateAgentCard(card));
        card.setProtocolVersion("0.3");
        card.setUrl(null);
        assertThrows(NacosException.class, () -> A2aAgentCardUtils.validateAgentCard(card));
        card.setUrl("https://legacy.example");
        card.setPreferredTransport(null);
        assertThrows(NacosException.class, () -> A2aAgentCardUtils.validateAgentCard(card));
    }
    
    private AgentCard legacy() {
        AgentCard card = new AgentCard();
        card.setName("demo");
        card.setVersion("1.0.0");
        card.setUrl("https://legacy.example");
        card.setPreferredTransport("JSONRPC");
        card.setProtocolVersion("0.3");
        return card;
    }
    
    private AgentInterface address(String url, String binding, String version) {
        AgentInterface result = new AgentInterface();
        result.setUrl(url);
        result.setProtocolBinding(binding);
        result.setProtocolVersion(version);
        return result;
    }
}
