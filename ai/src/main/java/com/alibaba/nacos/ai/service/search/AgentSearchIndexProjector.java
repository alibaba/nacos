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

package com.alibaba.nacos.ai.service.search;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.model.search.AiResourceSearchDocument;
import com.alibaba.nacos.ai.utils.AgentRequestUtil;
import com.alibaba.nacos.api.ai.model.a2a.AgentCapabilities;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentExtension;
import com.alibaba.nacos.api.ai.model.a2a.AgentSkill;
import com.alibaba.nacos.api.ai.model.agent.Agent;
import com.alibaba.nacos.api.ai.model.agent.AgentCallInterface;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionCatalog;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionCatalogEntry;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail;
import com.alibaba.nacos.api.utils.json.JsonUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the deterministic shared-search projection for one canonical Agent.
 *
 * @author Nacos
 */
public class AgentSearchIndexProjector {
    
    public static final int PROJECTION_VERSION = 1;
    
    static final String ARTIFACT_KIND_A2A_AGENT_CARD = "a2a-agent-card";
    
    static final String ARTIFACT_KIND_NACOS_AGENT = "nacos-agent";
    
    private static final String A2A_PROTOCOL = "a2a";
    
    private static final int MAX_AGENT_CONTENT_CHARS = 12000;
    
    private final AiResourceIndexProjectionBuilder projectionBuilder =
        new AiResourceIndexProjectionBuilder();
    
    /**
     * Project the current common-latest Agent definition.
     *
     * @param agent canonical Agent directory
     * @param latest exact common-latest online Version
     * @return complete document, chunk, and facet projection
     */
    public AiResourceIndexProjection project(Agent agent, AgentVersionDetail latest) {
        if (agent == null || latest == null) {
            throw new IllegalArgumentException("Agent and latest Version must not be null");
        }
        AgentCard a2aCard = findValidA2aCard(agent.getAgentName(), latest);
        List<String> protocols = collectProtocols(agent.getVersionCatalog());
        List<String> artifactKinds = artifactKinds(a2aCard);
        AiResourceSearchDocument document = buildDocument(agent, latest, protocols,
            artifactKinds, a2aCard);
        return projectionBuilder.build(document, buildContents(latest, a2aCard),
            AiResourceSearchConstants.CHUNK_TYPE_AGENT_CONTENT);
    }
    
    private AiResourceSearchDocument buildDocument(Agent agent, AgentVersionDetail latest,
        List<String> protocols, List<String> artifactKinds, AgentCard a2aCard) {
        AiResourceSearchDocument result = new AiResourceSearchDocument();
        result.setNamespaceId(agent.getNamespaceId());
        result.setResourceType(Constants.Agent.RESOURCE_TYPE_AGENT);
        result.setResourceName(agent.getAgentName());
        result.setResourceVersion(latest.getVersion());
        result.setDisplayName(StringUtils.isBlank(agent.getDisplayName())
            ? agent.getAgentName() : agent.getDisplayName());
        result.setDescription(agent.getDescription());
        result.setTags(JacksonUtils.toJson(nullToEmpty(agent.getTags())));
        result.setCapabilities(JacksonUtils.toJson(capabilities(latest, a2aCard)));
        result.setRepresentativeQueries(JacksonUtils.toJson(
            representativeQueries(agent, a2aCard)));
        result.setMetadata(JacksonUtils.toJson(metadata(agent, latest, protocols,
            artifactKinds)));
        result.setSourceDigest(sourceDigest(agent, latest, artifactKinds));
        result.setStatus(AiResourceSearchConstants.STATUS_ENABLED);
        result.setGenerateMode(AiResourceSearchConstants.GENERATE_MODE_AUTO);
        Long modified = latest.getUpdateTime() == null ? agent.getUpdateTime()
            : latest.getUpdateTime();
        if (modified != null) {
            result.setGmtModified(new Timestamp(modified));
        }
        return result;
    }
    
    private Map<String, Object> metadata(Agent agent, AgentVersionDetail latest,
        List<String> protocols, List<String> artifactKinds) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("namespaceId", agent.getNamespaceId());
        result.put("resourceType", Constants.Agent.RESOURCE_TYPE_AGENT);
        result.put("resourceName", agent.getAgentName());
        result.put("resourceVersion", latest.getVersion());
        putIfPresent(result, "iconUrl", agent.getIconUrl());
        putIfPresent(result, "provider", agent.getProvider());
        result.put("tags", agent.getTags());
        result.put("protocols", protocols);
        result.put("artifactKinds", artifactKinds);
        result.put("versionCatalog", agent.getVersionCatalog());
        putIfPresent(result, "scope", agent.getScope());
        putIfPresent(result, "owner", agent.getOwner());
        result.put("projectionVersion", PROJECTION_VERSION);
        return result;
    }
    
    private List<String> collectProtocols(AgentVersionCatalog catalog) {
        if (catalog == null || catalog.getOnlineVersions() == null) {
            return Collections.emptyList();
        }
        Set<String> result = new LinkedHashSet<>();
        for (AgentVersionCatalogEntry entry : catalog.getOnlineVersions()) {
            if (entry == null || entry.getProtocols() == null) {
                continue;
            }
            for (String protocol : entry.getProtocols()) {
                addIfNotBlank(result, protocol);
            }
        }
        return new ArrayList<>(result);
    }
    
    private List<String> artifactKinds(AgentCard a2aCard) {
        List<String> result = new ArrayList<>();
        if (a2aCard != null) {
            result.add(ARTIFACT_KIND_A2A_AGENT_CARD);
        }
        result.add(ARTIFACT_KIND_NACOS_AGENT);
        return result;
    }
    
    private List<String> capabilities(AgentVersionDetail latest, AgentCard card) {
        Set<String> result = new LinkedHashSet<>();
        if (latest.getCallInterfaces() != null) {
            for (AgentCallInterface callInterface : latest.getCallInterfaces()) {
                if (callInterface != null) {
                    addIfNotBlank(result, callInterface.getProtocol());
                }
            }
        }
        if (card != null) {
            addA2aCapabilities(result, card);
        }
        return new ArrayList<>(result);
    }
    
    private void addA2aCapabilities(Set<String> target, AgentCard card) {
        AgentCapabilities capabilities = card.getCapabilities();
        if (capabilities != null) {
            addFlag(target, "streaming", capabilities.getStreaming());
            addFlag(target, "push-notifications", capabilities.getPushNotifications());
            addFlag(target, "state-transition-history",
                capabilities.getStateTransitionHistory());
            addFlag(target, "extended-agent-card", capabilities.getExtendedAgentCard());
            if (capabilities.getExtensions() != null) {
                for (AgentExtension extension : capabilities.getExtensions()) {
                    if (extension != null) {
                        addIfNotBlank(target, extension.getUri());
                    }
                }
            }
        }
        if (card.getSkills() != null) {
            for (AgentSkill skill : card.getSkills()) {
                if (skill == null) {
                    continue;
                }
                addIfNotBlank(target, skill.getId());
                addIfNotBlank(target, skill.getName());
                addAll(target, skill.getTags());
            }
        }
    }
    
    private List<String> representativeQueries(Agent agent, AgentCard card) {
        Set<String> result = new LinkedHashSet<>();
        addIfNotBlank(result, agent.getAgentName());
        addIfNotBlank(result, agent.getDisplayName());
        addIfNotBlank(result, agent.getDescription());
        if (card != null) {
            addIfNotBlank(result, card.getName());
            addIfNotBlank(result, card.getDescription());
            if (card.getSkills() != null) {
                for (AgentSkill skill : card.getSkills()) {
                    if (skill == null) {
                        continue;
                    }
                    addIfNotBlank(result, skill.getName());
                    addIfNotBlank(result, skill.getDescription());
                    addAll(result, skill.getExamples());
                }
            }
        }
        return new ArrayList<>(result);
    }
    
    private List<AiResourceIndexEnhancementContent> buildContents(AgentVersionDetail latest,
        AgentCard a2aCard) {
        List<AiResourceIndexEnhancementContent> result = new ArrayList<>();
        if (a2aCard != null) {
            addContent(result, "agent-a2a.json", a2aText(a2aCard));
        }
        if (latest.getCallInterfaces() == null) {
            return result;
        }
        int index = 0;
        for (AgentCallInterface callInterface : latest.getCallInterfaces()) {
            if (callInterface != null && !A2A_PROTOCOL.equals(callInterface.getProtocol())) {
                addContent(result, "agent-" + index + '-' + callInterface.getProtocol()
                    + ".json", descriptorText(callInterface.getNativeDescriptor()));
            }
            index++;
        }
        return result;
    }
    
    private void addContent(List<AiResourceIndexEnhancementContent> target, String path,
        String content) {
        if (StringUtils.isNotBlank(content)) {
            target.add(new AiResourceIndexEnhancementContent(path, limit(content)));
        }
    }
    
    private String a2aText(AgentCard card) {
        StringBuilder result = new StringBuilder();
        appendLine(result, card.getName());
        appendLine(result, card.getDescription());
        if (card.getSkills() != null) {
            for (AgentSkill skill : card.getSkills()) {
                if (skill == null) {
                    continue;
                }
                appendLine(result, skill.getId());
                appendLine(result, skill.getName());
                appendLine(result, skill.getDescription());
                appendLines(result, skill.getTags());
                appendLines(result, skill.getExamples());
            }
        }
        return result.toString();
    }
    
    private String descriptorText(Object descriptor) {
        if (descriptor == null) {
            return null;
        }
        try {
            return JsonUtils.toCanonicalJson(descriptor);
        } catch (RuntimeException e) {
            return null;
        }
    }
    
    private AgentCard findValidA2aCard(String agentName, AgentVersionDetail latest) {
        if (latest.getCallInterfaces() == null) {
            return null;
        }
        for (AgentCallInterface callInterface : latest.getCallInterfaces()) {
            if (callInterface == null || !A2A_PROTOCOL.equals(callInterface.getProtocol())) {
                continue;
            }
            try {
                AgentCard card = JacksonUtils.toObj(
                    JacksonUtils.toJson(callInterface.getNativeDescriptor()), AgentCard.class);
                AgentRequestUtil.validateAgentCard(card);
                if (agentName.equals(card.getName()) && latest.getVersion().equals(
                    card.getVersion())) {
                    return card;
                }
            } catch (Exception ignored) {
                // An invalid A2A descriptor is not a complete A2A representation.
            }
        }
        return null;
    }
    
    private String sourceDigest(Agent agent, AgentVersionDetail latest,
        List<String> artifactKinds) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("status", agent.getStatus());
        source.put("displayName", agent.getDisplayName());
        source.put("description", agent.getDescription());
        source.put("iconUrl", agent.getIconUrl());
        source.put("provider", agent.getProvider());
        source.put("tags", agent.getTags());
        source.put("scope", agent.getScope());
        source.put("owner", agent.getOwner());
        source.put("versionCatalog", agent.getVersionCatalog());
        source.put("latestVersion", latest.getVersion());
        source.put("contentDigest", latest.getContentDigest());
        source.put("artifactKinds", artifactKinds);
        source.put("projectionVersion", PROJECTION_VERSION);
        return sha256(JsonUtils.toCanonicalJson(source));
    }
    
    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte each : digest) {
                result.append(String.format("%02x", each & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
    
    private String limit(String value) {
        return value.length() <= MAX_AGENT_CONTENT_CHARS ? value
            : value.substring(0, MAX_AGENT_CONTENT_CHARS);
    }
    
    private void appendLines(StringBuilder target, List<String> values) {
        if (values != null) {
            for (String value : values) {
                appendLine(target, value);
            }
        }
    }
    
    private void appendLine(StringBuilder target, String value) {
        if (StringUtils.isNotBlank(value)) {
            target.append(value).append('\n');
        }
    }
    
    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null
            && (!(value instanceof String) || StringUtils.isNotBlank((String) value))) {
            target.put(key, value);
        }
    }
    
    private void addFlag(Set<String> target, String value, Boolean enabled) {
        if (Boolean.TRUE.equals(enabled)) {
            target.add(value);
        }
    }
    
    private void addAll(Set<String> target, List<String> values) {
        if (values != null) {
            for (String value : values) {
                addIfNotBlank(target, value);
            }
        }
    }
    
    private void addIfNotBlank(Set<String> target, String value) {
        if (StringUtils.isNotBlank(value)) {
            target.add(value);
        }
    }
    
    private <T> List<T> nullToEmpty(List<T> values) {
        return values == null ? Collections.emptyList() : values;
    }
}
