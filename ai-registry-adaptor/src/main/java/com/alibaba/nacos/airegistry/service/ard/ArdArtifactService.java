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
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.service.McpServerOperationService;
import com.alibaba.nacos.ai.service.agent.AgentArtifactBuilder;
import com.alibaba.nacos.ai.service.agent.AgentPersistenceService;
import com.alibaba.nacos.ai.service.resource.AiResourceFileReader;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.ai.service.skills.SkillClientOperationService;
import com.alibaba.nacos.ai.service.skills.SkillQueryResult;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail;
import com.alibaba.nacos.api.ai.model.prompt.PromptUtils;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionInfo;
import com.alibaba.nacos.api.ai.model.skills.SkillUtils;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.airegistry.config.ConditionalOnArdEnabled;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.airegistry.constant.ArdProtocolConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Loads ARD artifact documents behind catalog entry URLs.
 *
 * @author nacos
 */
@Service
@ConditionalOnArdEnabled
public class ArdArtifactService {
    
    private final AiResourceManager resourceManager;
    
    private final McpServerOperationService mcpServerOperationService;
    
    private final AiResourceFileReader fileReader;
    
    private final SkillClientOperationService skillClientOperationService;
    
    private AgentPersistenceService agentPersistenceService;
    
    @Autowired
    public ArdArtifactService(AiResourceManager resourceManager,
        McpServerOperationService mcpServerOperationService,
        AiResourceFileReader fileReader, SkillClientOperationService skillClientOperationService) {
        this.resourceManager = resourceManager;
        this.mcpServerOperationService = mcpServerOperationService;
        this.fileReader = fileReader;
        this.skillClientOperationService = skillClientOperationService;
    }
    
    @Autowired(required = false)
    public void setAgentPersistenceService(AgentPersistenceService agentPersistenceService) {
        this.agentPersistenceService = agentPersistenceService;
    }
    
    /**
     * Load a versioned ARD artifact.
     */
    public ArdArtifact get(String namespaceId, String resourceType, String resourceName,
        String version, String mcpName) throws NacosException {
        return get(namespaceId, resourceType, resourceName, version, mcpName, null, null);
    }
    
    /**
     * Load a versioned ARD artifact with representation-specific integrity parameters.
     */
    public ArdArtifact get(String namespaceId, String resourceType, String resourceName,
        String version, String mcpName, String contentDigest, String representation)
        throws NacosException {
        if (StringUtils.isBlank(namespaceId) || StringUtils.isBlank(resourceType)
            || StringUtils.isBlank(resourceName) || StringUtils.isBlank(version)) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_MISSING,
                "Required ARD artifact parameter not present");
        }
        if (AiResourceConstants.RESOURCE_TYPE_SKILL.equals(resourceType)) {
            return skillArtifact(namespaceId, resourceName, version);
        }
        if (AiResourceConstants.RESOURCE_TYPE_PROMPT.equals(resourceType)) {
            return promptArtifact(namespaceId, resourceType, resourceName, version);
        }
        if (AiResourceConstants.RESOURCE_TYPE_MCP.equals(resourceType)) {
            McpServerDetailInfo detail = mcpServerOperationService.getMcpServerDetail(namespaceId,
                resourceName, mcpName, version);
            return new ArdArtifact(ArdProtocolConstants.MEDIA_TYPE_MCP, detail);
        }
        if (Constants.Agent.RESOURCE_TYPE_AGENT.equals(resourceType)) {
            return agentArtifact(namespaceId, resourceName, version, contentDigest,
                representation);
        }
        throw new NacosApiException(NacosException.INVALID_PARAM,
            ErrorCode.PARAMETER_VALIDATE_ERROR,
            "Unsupported ARD artifact resourceType: " + resourceType);
    }
    
    private ArdArtifact agentArtifact(String namespaceId, String agentName, String version,
        String contentDigest, String representation) throws NacosException {
        if (StringUtils.isBlank(contentDigest) || StringUtils.isBlank(representation)) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_MISSING,
                "Agent artifact contentDigest and representation are required");
        }
        AiResource meta = resourceManager.findMeta(namespaceId, agentName,
            Constants.Agent.RESOURCE_TYPE_AGENT);
        if (meta == null
            || !AiResourceConstants.META_STATUS_ENABLE.equalsIgnoreCase(meta.getStatus())) {
            throw notFound("Agent not found: " + agentName);
        }
        resourceManager.ensureReadableOrNotFound(meta, "Agent not found: " + agentName);
        if (agentPersistenceService == null) {
            throw new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR,
                "Agent artifact persistence is unavailable");
        }
        AgentVersionDetail detail;
        try {
            detail = agentPersistenceService.getAgentVersion(namespaceId, agentName, version);
        } catch (NacosException e) {
            if (e.getErrCode() == NacosException.NOT_FOUND) {
                throw notFound("Agent version not found: " + agentName + "@" + version);
            }
            throw e;
        }
        if (detail == null || !AiConstants.Agent.VERSION_STATUS_ONLINE.equalsIgnoreCase(
            detail.getStatus()) || !Objects.equals(contentDigest, detail.getContentDigest())) {
            throw notFound("Agent artifact not found: " + agentName + "@" + version);
        }
        if (AgentArtifactBuilder.ARTIFACT_KIND_A2A_AGENT_CARD.equals(representation)) {
            AgentCard card = AgentArtifactBuilder.findA2aAgentCard(agentName, detail);
            if (card == null) {
                throw notFound("A2A Agent Card artifact not found: " + agentName + "@" + version);
            }
            return new ArdArtifact(ArdProtocolConstants.MEDIA_TYPE_A2A_AGENT_CARD, card);
        }
        if (AgentArtifactBuilder.ARTIFACT_KIND_NACOS_AGENT.equals(representation)) {
            return new ArdArtifact(ArdProtocolConstants.MEDIA_TYPE_NACOS_AGENT,
                AgentArtifactBuilder.buildNacosAgentArtifact(detail));
        }
        throw notFound("Agent artifact representation not found: " + representation);
    }
    
    private ArdArtifact skillArtifact(String namespaceId, String resourceName, String version)
        throws NacosException {
        SkillQueryResult result =
            skillClientOperationService.querySkill(namespaceId, resourceName, version, null, null);
        try {
            return new ArdArtifact(ArdProtocolConstants.MEDIA_TYPE_SKILL_PACKAGE,
                SkillUtils.toZipBytes(result.getSkill()));
        } catch (Exception e) {
            throw new NacosApiException(NacosException.SERVER_ERROR,
                ErrorCode.DATA_ACCESS_ERROR, e, "Failed to create ARD Skill artifact");
        }
    }
    
    private ArdArtifact promptArtifact(String namespaceId, String resourceType,
        String resourceName, String version) throws NacosException {
        AiResourceVersion resourceVersion =
            readableOnlineVersion(namespaceId, resourceType, resourceName, version);
        byte[] bytes = readResourceFile(resourceVersion, namespaceId, resourceType, resourceName,
            version, PromptUtils.PROMPT_MAIN_DATA_ID);
        PromptVersionInfo prompt = JacksonUtils.toObj(new String(bytes, StandardCharsets.UTF_8),
            PromptVersionInfo.class);
        return new ArdArtifact(ArdProtocolConstants.MEDIA_TYPE_PROMPT, prompt);
    }
    
    private AiResourceVersion readableOnlineVersion(String namespaceId, String resourceType,
        String resourceName, String version) throws NacosException {
        AiResource meta = resourceManager.findMeta(namespaceId, resourceName, resourceType);
        if (meta == null
            || !AiResourceConstants.META_STATUS_ENABLE.equalsIgnoreCase(meta.getStatus())) {
            throw notFound(resourceType + " not found: " + resourceName);
        }
        resourceManager.ensureReadableOrNotFound(meta,
            resourceType + " not found: " + resourceName);
        AiResourceVersion resourceVersion =
            resourceManager.findVersion(namespaceId, resourceName, resourceType, version);
        if (resourceVersion == null || !AiResourceConstants.VERSION_STATUS_ONLINE
            .equalsIgnoreCase(resourceVersion.getStatus())) {
            throw notFound(resourceType + " version not found: " + resourceName + "@" + version);
        }
        return resourceVersion;
    }
    
    private byte[] readResourceFile(AiResourceVersion version, String namespaceId,
        String resourceType, String resourceName, String resourceVersion, String filePath)
        throws NacosException {
        try {
            byte[] bytes = fileReader.read(version, namespaceId, resourceType, resourceName,
                resourceVersion, filePath);
            if (bytes == null || bytes.length == 0) {
                throw notFound("ARD artifact file not found");
            }
            return bytes;
        } catch (NacosException e) {
            throw e;
        } catch (Exception e) {
            throw new NacosApiException(NacosException.SERVER_ERROR,
                ErrorCode.DATA_ACCESS_ERROR, e, "Failed to load ARD artifact");
        }
    }
    
    private NacosApiException notFound(String message) {
        return new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
            message);
    }
}
