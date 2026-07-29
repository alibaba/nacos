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

package com.alibaba.nacos.test.maintainer.ai;

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.agent.Agent;
import com.alibaba.nacos.api.ai.model.agent.AgentCallInterface;
import com.alibaba.nacos.api.ai.model.agent.AgentDraftCreateRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentDraftUpdateRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentLabelsUpdateRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentOverview;
import com.alibaba.nacos.api.ai.model.agent.AgentProvider;
import com.alibaba.nacos.api.ai.model.agent.AgentSummary;
import com.alibaba.nacos.api.ai.model.agent.AgentUpdateRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionCommand;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionSummary;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.ai.model.agent.RuntimeEndpointSnapshot;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.maintainer.client.ai.AgentMaintainerService;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerService;
import com.alibaba.nacos.test.maintainer.MaintainerSdkBaseITCase;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link AgentMaintainerService}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: an initial draft creates missing Agent metadata in default-public
 *     and explicit namespaces; bounded reads, metadata updates, draft replacement, lifecycle
 *     transitions, custom labels, and empty Runtime Endpoint snapshots work through the public
 *     Maintainer SDK.</li>
 *     <li>Boundary/validation: Request and Command payloads carry no namespace; convenience
 *     overloads always use {@code public}, explicit method arguments are the sole custom-namespace
 *     source, list filters are applied before pagination, the orderBy allowlist is preserved, and
 *     malformed identities fail with controlled SDK exceptions.</li>
 *     <li>Exception/error handling: absent resources map to HTTP not-found, invalid lifecycle
 *     transitions remain controlled parameter-state errors, and draft deletion makes the exact
 *     Version absent.</li>
 *     <li>Compatibility: {@link AiMaintainerService#a2a()} remains available; its existing
 *     lifecycle workflow stays covered by {@link AiMaintainerServiceMaintainerSdkITCase}.</li>
 *     <li>Known standalone limitation: reviewed-state publish/redraft success requires a review
 *     Pipeline plugin, so this class verifies their controlled illegal-state paths and uses
 *     force-publish or the no-Pipeline submit transition for successful publication.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
class AgentMaintainerServiceMaintainerSdkITCase extends MaintainerSdkBaseITCase {
    
    private static final String INITIAL_VERSION = "1.0.0";
    
    private static final String SECOND_VERSION = "1.1.0";
    
    private static final String PROTOCOL = "a2a";
    
    @Test
    void shouldManageAgentInDefaultNamespace() throws Exception {
        AiMaintainerService aiMaintainerService = createAiMaintainerService();
        AgentMaintainerService agentService = aiMaintainerService.agent();
        assertNotNull(agentService);
        assertNotNull(aiMaintainerService.a2a());
        
        String agentName = randomMaintainerName("agent-default");
        NacosException missing =
            assertThrows(NacosException.class, () -> agentService.getAgent(agentName));
        assertEquals(NacosException.NOT_FOUND, missing.getErrCode());
        
        AgentDraftCreateRequest createRequest =
            buildInitialDraftRequest(agentName, "Default namespace Agent", INITIAL_VERSION);
        AgentVersionDetail createdDraft = agentService.createDraft(createRequest);
        addCleanup(() -> agentService.deleteAgent(Constants.DEFAULT_NAMESPACE_ID, agentName));

        assertEquals(Constants.DEFAULT_NAMESPACE_ID, createdDraft.getNamespaceId());
        assertEquals(INITIAL_VERSION, createdDraft.getVersion());
        assertEquals(AiConstants.Agent.VERSION_STATUS_DRAFT, createdDraft.getStatus());
        AgentOverview created = agentService.getAgent(agentName);
        assertOverview(created, Constants.DEFAULT_NAMESPACE_ID, agentName, INITIAL_VERSION);
        String initialOwner = created.getAgent().getOwner();
        String initialScope = created.getAgent().getScope();
        assertEquals("Default namespace Agent", created.getAgent().getDescription());
        assertEquals("nested-value",
            ((Map<?, ?>) created.getAgent().getExtensions().get("maintainer-sdk-it"))
                .get("nested"));
        assertEquals(AiConstants.Agent.RESOURCE_STATUS_ENABLE, created.getAgent().getStatus());
        assertNotNull(created.getAgent().getOwner());
        assertEquals("PRIVATE", created.getAgent().getScope());
        
        AgentOverview queried = agentService.getAgent(agentName);
        assertOverview(queried, Constants.DEFAULT_NAMESPACE_ID, agentName, INITIAL_VERSION);
        
        Page<AgentVersionSummary> versions =
            agentService.listAgentVersions(agentName, AiConstants.Agent.VERSION_STATUS_DRAFT, 1,
                10);
        assertContainsVersion(versions, INITIAL_VERSION);
        AgentVersionDetail initial = agentService.getAgentVersion(agentName, INITIAL_VERSION);
        assertEquals(INITIAL_VERSION, initial.getVersion());
        assertEquals(PROTOCOL, initial.getCallInterfaces().get(0).getProtocol());
        
        RuntimeEndpointSnapshot snapshot =
            agentService.getRuntimeEndpoints(agentName, PROTOCOL, INITIAL_VERSION);
        assertEquals(Constants.DEFAULT_NAMESPACE_ID, snapshot.getNamespaceId());
        assertEquals(agentName, snapshot.getAgentName());
        assertEquals(PROTOCOL, snapshot.getProtocol());
        assertEquals(INITIAL_VERSION, snapshot.getVersion());
        assertNotNull(snapshot.getItems());
        assertTrue(snapshot.getItems().isEmpty());
        
        AgentUpdateRequest updateRequest =
            buildUpdateRequest(agentName, "Default namespace Agent updated");
        Agent updated = agentService.updateAgent(updateRequest);
        assertEquals("Default namespace Agent updated", updated.getDescription());
        assertEquals(initialOwner, updated.getOwner());
        assertEquals(initialScope, updated.getScope());
        assertEquals(Arrays.asList("maintainer-sdk-it", "updated"), updated.getTags());
        assertContainsAgent(agentService.listAgents(agentName,
            "maintainer-sdk-it", initialScope, initialOwner, null, 1, 10), agentName);
        
        AgentVersionCommand command = versionCommand(agentName, INITIAL_VERSION);
        AgentVersionSummary online = agentService.forcePublish(command);
        assertEquals(AiConstants.Agent.VERSION_STATUS_ONLINE, online.getStatus());
        
        AgentLabelsUpdateRequest labels = new AgentLabelsUpdateRequest();
        labels.setAgentName(agentName);
        labels.setLabels(Collections.singletonMap("stable", INITIAL_VERSION));
        Agent labeled = agentService.updateLabels(labels);
        assertEquals(INITIAL_VERSION, labeled.getVersionInfo().getLabels().get("latest"));
        assertEquals(INITIAL_VERSION, labeled.getVersionInfo().getLabels().get("stable"));
        
        AgentVersionSummary offline = agentService.offline(versionCommand(agentName,
            INITIAL_VERSION));
        assertEquals(AiConstants.Agent.VERSION_STATUS_OFFLINE, offline.getStatus());
        AgentVersionSummary onlineAgain = agentService.online(versionCommand(agentName,
            INITIAL_VERSION));
        assertEquals(AiConstants.Agent.VERSION_STATUS_ONLINE, onlineAgain.getStatus());
        
        NacosException invalidPublish = assertThrows(NacosException.class,
            () -> agentService.publish(versionCommand(agentName, INITIAL_VERSION)));
        assertEquals(NacosException.INVALID_PARAM, invalidPublish.getErrCode());
        assertFalse(String.valueOf(invalidPublish.getMessage()).isEmpty());
        
        agentService.deleteAgent(agentName);
        NacosException deleted =
            assertThrows(NacosException.class, () -> agentService.getAgent(agentName));
        assertEquals(NacosException.NOT_FOUND, deleted.getErrCode());
    }

    @Test
    void shouldApplyEveryListFilterBeforePaging() throws Exception {
        AgentMaintainerService agentService = createAiMaintainerService().agent();
        String targetName = randomMaintainerName("agent-filter");
        createListFilterAgent(agentService, targetName, null);
        createListFilterAgent(agentService, randomMaintainerName("agent-filter-name"), null);
        createListFilterAgent(agentService, targetName + "-tag",
            Collections.singletonList("other"));
        Agent target = agentService.getAgent(targetName).getAgent();

        Page<AgentSummary> page = agentService.listAgents(targetName, "maintainer-sdk-it",
            target.getScope(), target.getOwner(), "download_count", 1, 1);

        assertEquals(1, page.getTotalCount());
        assertEquals(1, page.getPageItems().size());
        assertEquals(targetName, page.getPageItems().get(0).getAgentName());
        assertEquals(0, agentService.listAgents(targetName, null, oppositeScope(target.getScope()),
            null, null, 1, 1).getTotalCount());
        assertEquals(0, agentService.listAgents(targetName, null, null, "other-owner", null, 1,
            1).getTotalCount());
    }
    
    @Test
    void shouldManageDraftLifecycleInExplicitNamespace() throws Exception {
        AgentMaintainerService agentService = createAiMaintainerService().agent();
        String namespaceId = randomMaintainerName("agent-namespace");
        String agentName = randomMaintainerName("agent-explicit");
        
        AgentDraftCreateRequest createRequest =
            buildInitialDraftRequest(agentName, "Explicit namespace Agent", INITIAL_VERSION);
        AgentVersionDetail initialDraft = agentService.createDraft(namespaceId, createRequest);
        addCleanup(() -> agentService.deleteAgent(namespaceId, agentName));
        assertEquals(namespaceId, initialDraft.getNamespaceId());
        AgentOverview created = agentService.getAgent(namespaceId, agentName);
        assertOverview(created, namespaceId, agentName, INITIAL_VERSION);
        
        agentService.forcePublish(namespaceId, versionCommand(agentName, INITIAL_VERSION));
        
        AgentDraftCreateRequest copiedDraft = new AgentDraftCreateRequest();
        copiedDraft.setAgentName(agentName);
        copiedDraft.setVersion(SECOND_VERSION);
        copiedDraft.setBasedOnVersion(INITIAL_VERSION);
        copiedDraft.setAuthor("maintainer-sdk-it");
        copiedDraft.setChangeDescription("copy the online version");
        AgentVersionDetail createdDraft = agentService.createDraft(namespaceId, copiedDraft);
        assertEquals(namespaceId, createdDraft.getNamespaceId());
        assertEquals(SECOND_VERSION, createdDraft.getVersion());
        assertEquals(AiConstants.Agent.VERSION_STATUS_DRAFT, createdDraft.getStatus());
        assertEquals(PROTOCOL, createdDraft.getCallInterfaces().get(0).getProtocol());
        
        AgentDraftUpdateRequest updateDraft = new AgentDraftUpdateRequest();
        updateDraft.setAgentName(agentName);
        updateDraft.setVersion(SECOND_VERSION);
        updateDraft.setCallInterfaces(Collections.singletonList(
            buildCallInterface(agentName, "updated-draft")));
        updateDraft.setChangeDescription("replace the copied draft");
        AgentVersionDetail updatedDraft = agentService.updateDraft(namespaceId, updateDraft);
        assertEquals("replace the copied draft", updatedDraft.getChangeDescription());
        assertEquals("updated-draft",
            ((Map<?, ?>) updatedDraft.getCallInterfaces().get(0).getNativeDescriptor()).get(
                "revision"));
        assertContainsVersion(agentService.listAgentVersions(namespaceId, agentName,
            AiConstants.Agent.VERSION_STATUS_DRAFT, 1, 10), SECOND_VERSION);
        
        NacosException invalidRedraft = assertThrows(NacosException.class,
            () -> agentService.redraft(namespaceId, versionCommand(agentName, SECOND_VERSION)));
        assertEquals(NacosException.INVALID_PARAM, invalidRedraft.getErrCode());
        
        agentService.deleteDraft(namespaceId, agentName, SECOND_VERSION);
        NacosException deletedDraft = assertThrows(NacosException.class,
            () -> agentService.getAgentVersion(namespaceId, agentName, SECOND_VERSION));
        assertEquals(NacosException.NOT_FOUND, deletedDraft.getErrCode());
        
        AgentDraftCreateRequest submittedDraft = new AgentDraftCreateRequest();
        submittedDraft.setAgentName(agentName);
        submittedDraft.setVersion(SECOND_VERSION);
        submittedDraft.setCallInterfaces(Collections.singletonList(
            buildCallInterface(agentName, "submit")));
        submittedDraft.setAuthor("maintainer-sdk-it");
        submittedDraft.setChangeDescription("submit without review pipeline");
        agentService.createDraft(namespaceId, submittedDraft);
        AgentVersionSummary submitted =
            agentService.submit(namespaceId, versionCommand(agentName, SECOND_VERSION));
        assertEquals(AiConstants.Agent.VERSION_STATUS_ONLINE, submitted.getStatus());
        assertContainsVersion(agentService.listAgentVersions(namespaceId, agentName,
            AiConstants.Agent.VERSION_STATUS_ONLINE, 1, 10), SECOND_VERSION);
    }
    
    @Test
    void shouldMapValidationErrorsWithoutBreakingLegacyDelegate() throws Exception {
        AiMaintainerService aiMaintainerService = createAiMaintainerService();
        assertNotNull(aiMaintainerService.agent());
        assertNotNull(aiMaintainerService.a2a());
        
        AgentDraftCreateRequest invalidCreate =
            buildInitialDraftRequest(null, "invalid", INITIAL_VERSION);
        NacosException invalid =
            assertThrows(NacosException.class, () -> aiMaintainerService.agent()
                .createDraft(invalidCreate));
        assertEquals(NacosException.INVALID_PARAM, invalid.getErrCode());
        assertFalse(String.valueOf(invalid.getMessage()).isEmpty());
        
        NacosException invalidRuntime = assertThrows(NacosException.class,
            () -> aiMaintainerService.agent().getRuntimeEndpoints(
                randomMaintainerName("missing-agent"), "not a protocol", null));
        assertEquals(NacosException.INVALID_PARAM, invalidRuntime.getErrCode());
    }
    
    private AgentDraftCreateRequest buildInitialDraftRequest(String agentName, String description,
        String version) {
        AgentDraftCreateRequest result = new AgentDraftCreateRequest();
        result.setAgentName(agentName);
        result.setDisplayName(agentName == null ? "invalid" : "Display " + agentName);
        result.setDescription(description);
        result.setIconUrl("https://example.com/icons/agent.svg");
        result.setProvider(provider("Nacos Maintainer SDK IT"));
        result.setTags(Collections.singletonList("maintainer-sdk-it"));
        Map<String, Object> nested = new HashMap<String, Object>();
        nested.put("nested", "nested-value");
        nested.put("values", Arrays.asList("one", "two"));
        result.setExtensions(Collections.<String, Object>singletonMap("maintainer-sdk-it", nested));
        result.setVersion(version);
        result.setCallInterfaces(Collections.singletonList(
            buildCallInterface(agentName == null ? "invalid" : agentName, "initial")));
        result.setAuthor("maintainer-sdk-it");
        result.setChangeDescription("initial Agent draft");
        return result;
    }

    private void createListFilterAgent(AgentMaintainerService agentService, String agentName,
        List<String> tags) throws NacosException {
        agentService.createDraft(
            buildInitialDraftRequest(agentName, "List filter Agent", INITIAL_VERSION));
        AgentUpdateRequest request = buildUpdateRequest(agentName, "List filter Agent");
        request.setTags(tags == null ? Arrays.asList("maintainer-sdk-it", "filter") : tags);
        agentService.updateAgent(request);
        addCleanup(
            () -> agentService.deleteAgent(Constants.DEFAULT_NAMESPACE_ID, agentName));
    }
    
    private AgentUpdateRequest buildUpdateRequest(String agentName, String description) {
        AgentUpdateRequest result = new AgentUpdateRequest();
        result.setAgentName(agentName);
        result.setDisplayName("Updated " + agentName);
        result.setDescription(description);
        result.setIconUrl("https://example.com/icons/agent-updated.svg");
        result.setProvider(provider("Nacos Maintainer SDK IT Updated"));
        result.setTags(Arrays.asList("maintainer-sdk-it", "updated"));
        result.setExtensions(Collections.<String, Object>singletonMap("maintainer-sdk-it",
            Collections.<String, Object>singletonMap("nested", "updated-value")));
        result.setStatus(AiConstants.Agent.RESOURCE_STATUS_ENABLE);
        return result;
    }
    
    private String oppositeScope(String scope) {
        return "PUBLIC".equals(scope) ? "PRIVATE" : "PUBLIC";
    }
    
    private AgentProvider provider(String name) {
        AgentProvider result = new AgentProvider();
        result.setName(name);
        result.setUrl("https://nacos.io");
        return result;
    }
    
    private AgentCallInterface buildCallInterface(String agentName, String revision) {
        Map<String, Object> descriptor = new HashMap<String, Object>();
        descriptor.put("name", agentName);
        descriptor.put("revision", revision);
        descriptor.put("capabilities",
            Collections.<String, Object>singletonMap("streaming", Boolean.TRUE));
        
        AgentCallInterface result = new AgentCallInterface();
        result.setProtocol(PROTOCOL);
        result.setProtocolVersion("1.0");
        result.setDescriptorMediaType("application/json");
        result.setNativeDescriptor(descriptor);
        result.setEndpointSourceOrder(Collections.singletonList(EndpointSource.RUNTIME));
        return result;
    }
    
    private AgentVersionCommand versionCommand(String agentName, String version) {
        AgentVersionCommand result = new AgentVersionCommand();
        result.setAgentName(agentName);
        result.setVersion(version);
        return result;
    }
    
    private void assertOverview(AgentOverview overview, String namespaceId, String agentName,
        String version) {
        assertNotNull(overview);
        assertNotNull(overview.getAgent());
        assertEquals(namespaceId, overview.getAgent().getNamespaceId());
        assertEquals(agentName, overview.getAgent().getAgentName());
        assertNotNull(overview.getVersionPage());
        assertContainsVersion(overview.getVersionPage(), version);
    }
    
    private void assertContainsAgent(Page<AgentSummary> page, String agentName) {
        assertNotNull(page);
        List<AgentSummary> items = page.getPageItems();
        assertNotNull(items);
        assertTrue(items.stream().anyMatch(each -> agentName.equals(each.getAgentName())),
            () -> "Expected Agent was not found in " + items);
    }
    
    private void assertContainsVersion(Page<AgentVersionSummary> page, String version) {
        assertNotNull(page);
        List<AgentVersionSummary> items = page.getPageItems();
        assertNotNull(items);
        assertTrue(items.stream().anyMatch(each -> version.equals(each.getVersion())),
            () -> "Expected Agent Version was not found in " + items);
    }
}
