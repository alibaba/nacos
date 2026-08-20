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

package com.alibaba.nacos.ai.service.agent;

import com.alibaba.nacos.api.ai.model.a2a.AgentCapabilities;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.agent.AgentCallInterface;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentArtifactBuilderTest {
    
    @Test
    void shouldFindOnlyValidExactVersionA2aCard() {
        AgentVersionDetail version = version();
        AgentCard valid = card("demo", "1.0.0");
        version.setCallInterfaces(Arrays.asList(null, call("grpc", valid),
            call("a2a", Map.of("name", "broken")), call("A2A", card("other", "1.0.0")),
            call("a2a", card("demo", "2.0.0")), call("A2A", valid)));
        
        AgentCard result = AgentArtifactBuilder.findA2aAgentCard("demo", version);
        
        assertEquals("demo", result.getName());
        assertEquals("1.0.0", result.getVersion());
        assertNull(AgentArtifactBuilder.findA2aAgentCard("missing", version));
        assertNull(AgentArtifactBuilder.findA2aAgentCard("demo", null));
        version.setCallInterfaces(null);
        assertNull(AgentArtifactBuilder.findA2aAgentCard("demo", version));
    }
    
    @Test
    void shouldBuildSchemaConstrainedNacosArtifact() {
        AgentVersionDetail version = version();
        List<AgentCallInterface> interfaces = List.of(call("grpc", Map.of("service", "demo")));
        version.setCallInterfaces(interfaces);
        
        Map<String, Object> result = AgentArtifactBuilder.buildNacosAgentArtifact(version);
        
        assertEquals(List.of("schemaVersion", "agentName", "version", "contentDigest",
            "callInterfaces"), List.copyOf(result.keySet()));
        assertEquals("1.0", result.get("schemaVersion"));
        assertEquals("demo", result.get("agentName"));
        assertEquals("1.0.0", result.get("version"));
        assertEquals("sha256:digest", result.get("contentDigest"));
        assertSame(interfaces, result.get("callInterfaces"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentArtifactBuilder.buildNacosAgentArtifact(null));
    }
    
    private AgentVersionDetail version() {
        AgentVersionDetail result = new AgentVersionDetail();
        result.setAgentName("demo");
        result.setVersion("1.0.0");
        result.setContentDigest("sha256:digest");
        return result;
    }
    
    private AgentCallInterface call(String protocol, Object descriptor) {
        AgentCallInterface result = new AgentCallInterface();
        result.setProtocol(protocol);
        result.setNativeDescriptor(descriptor);
        return result;
    }
    
    private AgentCard card(String name, String version) {
        AgentCard result = new AgentCard();
        result.setName(name);
        result.setVersion(version);
        result.setDescription("Demo card");
        result.setUrl("https://example.com/a2a");
        result.setPreferredTransport("HTTP+JSON");
        result.setProtocolVersion("0.3.0");
        result.setCapabilities(new AgentCapabilities());
        return result;
    }
}
