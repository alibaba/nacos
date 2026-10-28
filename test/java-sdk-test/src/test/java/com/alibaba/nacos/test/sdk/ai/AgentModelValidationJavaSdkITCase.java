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

package com.alibaba.nacos.test.sdk.ai;

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.agent.AgentOverview;
import com.alibaba.nacos.api.ai.model.agent.AgentProvider;
import com.alibaba.nacos.api.ai.model.agent.AgentSummary;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionInfo;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionSummary;
import com.alibaba.nacos.api.ai.utils.AgentModelValidator;
import com.alibaba.nacos.api.model.Page;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Public API boundary coverage for extensible Agent scopes, without a server or plugin runtime.
 *
 * <p>Scenario coverage: unchanged Agent, summary and overview entry points retain PUBLIC/PRIVATE
 * without a provider; unrelated invalid metadata and unregistered/malformed scopes fail.
 */
public class AgentModelValidationJavaSdkITCase {
    
    private static final String CONTENT_DIGEST =
        "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    
    @Test
    void unchangedEntryPointsRetainBuiltInScopesWithoutProvider() {
        AgentSummary agent = newValidAgent();
        AgentSummary summary = newValidAgentSummary();
        AgentOverview overview = new AgentOverview();
        overview.setAgent(agent);
        overview.setVersionPage(newValidVersionSummaryPage());
        for (String scope : Arrays.asList("PUBLIC", "PRIVATE")) {
            agent.setScope(scope);
            summary.setScope(scope);
            assertDoesNotThrow(() -> AgentModelValidator.validateAgent(agent));
            assertDoesNotThrow(() -> AgentModelValidator.validateAgentSummary(summary));
            assertDoesNotThrow(() -> AgentModelValidator.validateOverview(overview));
        }
        agent.setOwner("");
        assertThrows(IllegalArgumentException.class,
            () -> AgentModelValidator.validateAgent(agent));
    }
    
    @Test
    void unregisteredAndMalformedScopesFailWithoutCallerConfiguration() {
        AgentSummary agent = newValidAgent();
        AgentSummary summary = newValidAgentSummary();
        AgentOverview overview = new AgentOverview();
        overview.setAgent(agent);
        overview.setVersionPage(newValidVersionSummaryPage());
        for (String scope : Arrays.asList("TEAM_EDIT", "UNKNOWN", "public", "", " ", null)) {
            agent.setScope(scope);
            summary.setScope(scope);
            assertThrows(IllegalArgumentException.class,
                () -> AgentModelValidator.validateAgent(agent));
            assertThrows(IllegalArgumentException.class,
                () -> AgentModelValidator.validateAgentSummary(summary));
            assertThrows(IllegalArgumentException.class,
                () -> AgentModelValidator.validateOverview(overview));
        }
    }
    
    private AgentSummary newValidAgent() {
        AgentProvider provider = new AgentProvider();
        provider.setName("Nacos");
        provider.setUrl("https://nacos.io");
        
        AgentVersionInfo versionInfo = new AgentVersionInfo();
        Map<String, String> labels = new LinkedHashMap<String, String>();
        labels.put("latest", "1.0.0");
        labels.put("stable", "1.0.0");
        versionInfo.setLabels(labels);
        
        AgentVersionSummary catalogEntry = new AgentVersionSummary();
        catalogEntry.setVersion("1.0.0");
        catalogEntry.setLabels(Collections.singletonList("stable"));
        catalogEntry.setProtocols(Collections.singletonList("a2a"));
        versionInfo.setOnlineVersions(Collections.singletonList(catalogEntry));
        
        AgentSummary agent = new AgentSummary();
        agent.setNamespaceId("public");
        agent.setAgentName("Demo Agent");
        agent.setDisplayName("Demo Agent 展示名");
        agent.setDescription("A complete Agent used by the validator contract test.");
        agent.setIconUrl("https://example.com/icon.png");
        agent.setProvider(provider);
        agent.setTags(Arrays.asList("assistant", "demo"));
        agent.setExtensions(Collections.<String, Object>singletonMap("example.com/color", "blue"));
        agent.setStatus(AiConstants.Agent.RESOURCE_STATUS_ENABLE);
        agent.setOwner("nacos");
        agent.setScope("PUBLIC");
        agent.setVersionInfo(versionInfo);
        agent.setMetaVersion(1L);
        agent.setCreateTime(1L);
        agent.setUpdateTime(2L);
        return agent;
    }
    
    private AgentSummary newValidAgentSummary() {
        AgentSummary agent = newValidAgent();
        AgentSummary summary = new AgentSummary();
        summary.setNamespaceId(agent.getNamespaceId());
        summary.setAgentName(agent.getAgentName());
        summary.setDisplayName(agent.getDisplayName());
        summary.setDescription(agent.getDescription());
        summary.setIconUrl(agent.getIconUrl());
        summary.setProvider(agent.getProvider());
        summary.setTags(agent.getTags());
        summary.setStatus(agent.getStatus());
        summary.setOwner(agent.getOwner());
        summary.setScope(agent.getScope());
        summary.setVersionInfo(agent.getVersionInfo());
        summary.setMetaVersion(agent.getMetaVersion());
        summary.setCreateTime(agent.getCreateTime());
        summary.setUpdateTime(agent.getUpdateTime());
        return summary;
    }
    
    private AgentVersionSummary newValidVersionSummary() {
        AgentVersionSummary summary = new AgentVersionSummary();
        summary.setVersion("1.0.0");
        summary.setStatus(AiConstants.Agent.VERSION_STATUS_ONLINE);
        summary.setAuthor("nacos");
        summary.setChangeDescription("Initial online version");
        summary.setContentDigest(CONTENT_DIGEST);
        summary.setCreateTime(1L);
        summary.setUpdateTime(2L);
        return summary;
    }
    
    private Page<AgentVersionSummary> newValidVersionSummaryPage() {
        Page<AgentVersionSummary> page = new Page<AgentVersionSummary>();
        page.setTotalCount(1);
        page.setPageNumber(1);
        page.setPagesAvailable(1);
        page.setPageItems(Collections.singletonList(newValidVersionSummary()));
        return page;
    }
    
}
