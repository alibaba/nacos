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

package com.alibaba.nacos.airegistry.service.ard;

import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.service.mcp.McpOperationService;
import com.alibaba.nacos.ai.service.agent.AgentPersistenceService;
import com.alibaba.nacos.ai.service.resource.AiResourceFileReader;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.ai.service.skills.SkillClientOperationService;
import com.alibaba.nacos.ai.service.skills.SkillQueryResult;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillResource;
import com.alibaba.nacos.api.ai.model.a2a.AgentCapabilities;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.agent.AgentCallInterface;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.airegistry.constant.ArdProtocolConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ArdArtifactService}.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
class ArdArtifactServiceTest {
    
    @Mock
    private AiResourceManager resourceManager;
    
    @Mock
    private McpOperationService mcpServerOperationService;
    
    @Mock
    private AiResourceFileReader fileReader;
    
    @Mock
    private SkillClientOperationService skillClientOperationService;
    
    @Mock
    private AgentPersistenceService agentPersistenceService;
    
    @Test
    void getShouldReturnCompleteSkillZip() throws Exception {
        Skill skill = skill();
        when(skillClientOperationService.querySkill("public", "demo", "1.0.0", null, null))
            .thenReturn(new SkillQueryResult(skill, "md5", "1.0.0"));
        ArdArtifactService service = service();
        
        ArdArtifact artifact = service.get("public", AiResourceConstants.RESOURCE_TYPE_SKILL,
            "demo", "1.0.0", null);
        
        assertEquals(ArdProtocolConstants.MEDIA_TYPE_SKILL_PACKAGE, artifact.getMediaType());
        assertEquals(Set.of("demo/SKILL.md", "demo/references/guide.md"),
            zipEntries((byte[]) artifact.getBody()));
    }
    
    @Test
    void getShouldPropagateSkillNotFound() throws Exception {
        when(skillClientOperationService.querySkill("public", "demo", "1.0.0", null, null))
            .thenThrow(new NacosApiException(NacosException.NOT_FOUND,
                ErrorCode.RESOURCE_NOT_FOUND, "skill not found"));
        ArdArtifactService service = service();
        
        NacosException exception = assertThrows(NacosException.class,
            () -> service.get("public", AiResourceConstants.RESOURCE_TYPE_SKILL, "demo", "1.0.0",
                null));
        
        assertEquals(NacosException.NOT_FOUND, exception.getErrCode());
    }
    
    @Test
    void getShouldReturnExactA2aAndNacosAgentRepresentations() throws Exception {
        ArdArtifactService service = serviceWithAgentPersistence();
        AiResource meta = agentMeta("enable");
        AgentVersionDetail version = agentVersion("online", "sha256:digest");
        AgentCard card = agentCard();
        version.setCallInterfaces(List.of(call("a2a", card),
            call("custom", Map.of("service", "demo"))));
        when(resourceManager.findMeta("public", "demo", Constants.Agent.RESOURCE_TYPE_AGENT))
            .thenReturn(meta);
        when(agentPersistenceService.getAgentVersion("public", "demo", "1.0.0"))
            .thenReturn(version);
        
        ArdArtifact a2a = service.get("public", Constants.Agent.RESOURCE_TYPE_AGENT, "demo",
            "1.0.0", null, "sha256:digest", "a2a-agent-card");
        ArdArtifact nacos = service.get("public", Constants.Agent.RESOURCE_TYPE_AGENT, "demo",
            "1.0.0", null, "sha256:digest", "nacos-agent");
        
        assertEquals(ArdProtocolConstants.MEDIA_TYPE_A2A_AGENT_CARD, a2a.getMediaType());
        assertEquals("demo", ((AgentCard) a2a.getBody()).getName());
        assertEquals(ArdProtocolConstants.MEDIA_TYPE_NACOS_AGENT, nacos.getMediaType());
        Map<?, ?> body = (Map<?, ?>) nacos.getBody();
        assertEquals(List.of("schemaVersion", "agentName", "version", "contentDigest",
            "callInterfaces"), List.copyOf(body.keySet()));
        assertEquals("sha256:digest", body.get("contentDigest"));
        assertSame(version.getCallInterfaces(), body.get("callInterfaces"));
    }
    
    @Test
    void getShouldRejectUnavailableAgentArtifactAsNotFound() throws Exception {
        ArdArtifactService service = serviceWithAgentPersistence();
        AiResource meta = agentMeta("enable");
        when(resourceManager.findMeta("public", "demo", Constants.Agent.RESOURCE_TYPE_AGENT))
            .thenReturn(null, agentMeta("disable"), meta, meta, meta, meta, meta);
        when(agentPersistenceService.getAgentVersion("public", "demo", "1.0.0"))
            .thenReturn(null, agentVersion("offline", "sha256:digest"),
                agentVersion("online", "sha256:other"),
                agentVersion("online", "sha256:digest"),
                agentVersion("online", "sha256:digest"));
        
        assertNotFound(() -> service.get("public", "agent", "demo", "1.0.0", null,
            "sha256:digest", "nacos-agent"));
        assertNotFound(() -> service.get("public", "agent", "demo", "1.0.0", null,
            "sha256:digest", "nacos-agent"));
        assertNotFound(() -> service.get("public", "agent", "demo", "1.0.0", null,
            "sha256:digest", "nacos-agent"));
        assertNotFound(() -> service.get("public", "agent", "demo", "1.0.0", null,
            "sha256:digest", "nacos-agent"));
        assertNotFound(() -> service.get("public", "agent", "demo", "1.0.0", null,
            "sha256:digest", "nacos-agent"));
        assertNotFound(() -> service.get("public", "agent", "demo", "1.0.0", null,
            "sha256:digest", "a2a-agent-card"));
        assertNotFound(() -> service.get("public", "agent", "demo", "1.0.0", null,
            "sha256:digest", "unknown"));
    }
    
    @Test
    void getShouldPreserveUnexpectedAgentPersistenceError() throws Exception {
        ArdArtifactService service = serviceWithAgentPersistence();
        when(resourceManager.findMeta("public", "demo", Constants.Agent.RESOURCE_TYPE_AGENT))
            .thenReturn(agentMeta("enable"));
        doThrow(new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
            "missing"),
            new NacosApiException(NacosException.SERVER_ERROR,
                ErrorCode.DATA_ACCESS_ERROR, "database unavailable"))
            .when(agentPersistenceService).getAgentVersion("public", "demo", "1.0.0");
        
        assertNotFound(() -> service.get("public", "agent", "demo", "1.0.0", null,
            "sha256:digest", "nacos-agent"));
        NacosException exception = assertThrows(NacosException.class,
            () -> service.get("public", "agent", "demo", "1.0.0", null,
                "sha256:digest", "nacos-agent"));
        assertEquals(NacosException.SERVER_ERROR, exception.getErrCode());
    }
    
    @Test
    void getShouldValidateAgentArtifactParametersAndPersistenceAvailability() throws Exception {
        ArdArtifactService service = service();
        assertThrows(NacosApiException.class,
            () -> service.get(null, "agent", "demo", "1.0.0", null));
        assertThrows(NacosApiException.class,
            () -> service.get("public", "unknown", "demo", "1.0.0", null));
        assertThrows(NacosApiException.class,
            () -> service.get("public", "agent", "demo", "1.0.0", null, null,
                "nacos-agent"));
        when(resourceManager.findMeta("public", "demo", Constants.Agent.RESOURCE_TYPE_AGENT))
            .thenReturn(agentMeta("enable"));
        NacosException exception = assertThrows(NacosException.class,
            () -> service.get("public", "agent", "demo", "1.0.0", null,
                "sha256:digest", "nacos-agent"));
        assertEquals(NacosException.SERVER_ERROR, exception.getErrCode());
        service.setAgentPersistenceService(null);
    }
    
    private ArdArtifactService service() {
        return new ArdArtifactService(resourceManager, mcpServerOperationService, fileReader,
            skillClientOperationService);
    }
    
    private ArdArtifactService serviceWithAgentPersistence() {
        ArdArtifactService result = service();
        result.setAgentPersistenceService(agentPersistenceService);
        return result;
    }
    
    private AiResource agentMeta(String status) {
        AiResource result = new AiResource();
        result.setNamespaceId("public");
        result.setName("demo");
        result.setType(Constants.Agent.RESOURCE_TYPE_AGENT);
        result.setStatus(status);
        return result;
    }
    
    private AgentVersionDetail agentVersion(String status, String digest) {
        AgentVersionDetail result = new AgentVersionDetail();
        result.setAgentName("demo");
        result.setVersion("1.0.0");
        result.setStatus(status);
        result.setContentDigest(digest);
        result.setCallInterfaces(List.of(call("custom", Map.of("service", "demo"))));
        return result;
    }
    
    private AgentCallInterface call(String protocol, Object descriptor) {
        AgentCallInterface result = new AgentCallInterface();
        result.setProtocol(protocol);
        result.setNativeDescriptor(descriptor);
        return result;
    }
    
    private AgentCard agentCard() {
        AgentCard result = new AgentCard();
        result.setName("demo");
        result.setVersion("1.0.0");
        result.setDescription("Demo card");
        result.setUrl("https://example.com/a2a");
        result.setPreferredTransport("HTTP+JSON");
        result.setProtocolVersion("0.3.0");
        result.setCapabilities(new AgentCapabilities());
        return result;
    }
    
    private void assertNotFound(ThrowingOperation operation) {
        NacosException exception = assertThrows(NacosException.class, operation::run);
        assertEquals(NacosException.NOT_FOUND, exception.getErrCode());
    }
    
    @FunctionalInterface
    private interface ThrowingOperation {
        
        void run() throws Exception;
    }
    
    private Skill skill() {
        Skill skill = new Skill();
        skill.setName("demo");
        skill.setSkillMd("# Demo");
        SkillResource resource = new SkillResource();
        resource.setName("guide.md");
        resource.setType("references");
        resource.setContent("Guide");
        skill.setResource(Map.of(resource.getResourceIdentifier(), resource));
        return skill;
    }
    
    private Set<String> zipEntries(byte[] bytes) throws Exception {
        Set<String> result = new HashSet<>();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                result.add(entry.getName());
            }
        }
        return result;
    }
}
