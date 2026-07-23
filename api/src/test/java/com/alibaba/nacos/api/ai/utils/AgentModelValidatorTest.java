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

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.agent.Agent;
import com.alibaba.nacos.api.ai.model.agent.AgentCallInterface;
import com.alibaba.nacos.api.ai.model.agent.AgentOverview;
import com.alibaba.nacos.api.ai.model.agent.AgentProvider;
import com.alibaba.nacos.api.ai.model.agent.AgentSummary;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionCatalog;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionCatalogEntry;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionInfo;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionSummary;
import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.ai.model.agent.RuntimeEndpointSnapshot;
import com.alibaba.nacos.api.ai.model.agent.RuntimeEndpointSnapshotItem;
import com.alibaba.nacos.api.ai.model.agent.RuntimeEndpointState;
import com.alibaba.nacos.api.ai.model.agent.RuntimeVersionBinding;
import com.alibaba.nacos.api.model.Page;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentModelValidatorTest {
    
    private static final String CONTENT_DIGEST =
        "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    
    @Test
    void testValidAgentVersionAndRuntimeSnapshot() {
        assertDoesNotThrow(() -> AgentModelValidator.validateAgent(newValidAgent()));
        assertDoesNotThrow(
            () -> AgentModelValidator.validateVersionDetail(newValidVersionDetail()));
        assertDoesNotThrow(
            () -> AgentModelValidator.validateCallInterface(newValidCallInterface()));
        assertDoesNotThrow(() -> AgentModelValidator.validateRuntimeEndpointSnapshot(
            newValidRuntimeEndpointSnapshot()));
    }
    
    @Test
    void testValidVersionSummaryPage() {
        Page<AgentVersionSummary> page = newValidVersionSummaryPage();
        
        assertDoesNotThrow(() -> AgentModelValidator.validateVersionSummaryPage(page));
    }
    
    @Test
    void testValidSummaryOverviewAndOptionalCollections() {
        AgentSummary summary = newValidAgentSummary();
        assertDoesNotThrow(() -> AgentModelValidator.validateAgentSummary(summary));
        
        AgentOverview overview = new AgentOverview();
        overview.setAgent(newValidAgent());
        overview.setVersionPage(newValidVersionSummaryPage());
        assertDoesNotThrow(() -> AgentModelValidator.validateOverview(overview));
        
        Agent agent = newValidAgent();
        agent.setProvider(null);
        agent.setTags(null);
        agent.setExtensions(null);
        agent.getVersionInfo().getLabels().put("candidate", "2.0.0");
        assertDoesNotThrow(() -> AgentModelValidator.validateAgent(agent));
        
        AgentCallInterface callInterface = newValidCallInterface();
        callInterface.setDeclaredEndpoints(null);
        assertDoesNotThrow(() -> AgentModelValidator.validateCallInterface(callInterface));
        
        RuntimeEndpointSnapshot unhealthySnapshot = newValidRuntimeEndpointSnapshot();
        RuntimeEndpointSnapshotItem unhealthyItem = unhealthySnapshot.getItems().get(0);
        unhealthyItem.setHealthy(false);
        unhealthyItem.setState(RuntimeEndpointState.UNHEALTHY);
        assertDoesNotThrow(() -> AgentModelValidator.validateRuntimeEndpointSnapshot(
            unhealthySnapshot));
        
        RuntimeEndpointSnapshot disabledSnapshot = newValidRuntimeEndpointSnapshot();
        RuntimeEndpointSnapshotItem disabledItem = disabledSnapshot.getItems().get(0);
        disabledItem.setEnabled(false);
        disabledItem.setState(RuntimeEndpointState.DISABLED);
        assertDoesNotThrow(() -> AgentModelValidator.validateRuntimeEndpointSnapshot(
            disabledSnapshot));
    }
    
    @Test
    void testEmptyVersionCatalogRules() {
        AgentVersionCatalog emptyCatalog = new AgentVersionCatalog();
        emptyCatalog.setOnlineVersions(Collections.<AgentVersionCatalogEntry>emptyList());
        assertDoesNotThrow(() -> AgentModelValidator.validateVersionCatalog(emptyCatalog));
        
        emptyCatalog.setLatestVersion("1.0.0");
        assertThrows(IllegalArgumentException.class,
            () -> AgentModelValidator.validateVersionCatalog(emptyCatalog));
        
        Agent agent = newValidAgent();
        agent.getVersionInfo().setOnlineCnt(0);
        agent.getVersionInfo().setLabels(Collections.<String, String>emptyMap());
        agent.setVersionCatalog(new AgentVersionCatalog());
        agent.getVersionCatalog().setOnlineVersions(
            Collections.<AgentVersionCatalogEntry>emptyList());
        assertDoesNotThrow(() -> AgentModelValidator.validateAgent(agent));
        
        agent.getVersionInfo().setLabels(
            Collections.singletonMap("latest", "1.0.0"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentModelValidator.validateAgent(agent));
    }
    
    @Test
    void testRejectsInvalidVersionCatalogEntries() {
        AgentVersionCatalog duplicateVersions = newValidAgent().getVersionCatalog();
        duplicateVersions.setOnlineVersions(Arrays.asList(
            duplicateVersions.getOnlineVersions().get(0),
            duplicateVersions.getOnlineVersions().get(0)));
        assertThrows(IllegalArgumentException.class,
            () -> AgentModelValidator.validateVersionCatalog(duplicateVersions));
        
        AgentVersionCatalog missingLatest = newValidAgent().getVersionCatalog();
        missingLatest.setLatestVersion("2.0.0");
        assertThrows(IllegalArgumentException.class,
            () -> AgentModelValidator.validateVersionCatalog(missingLatest));
        
        AgentVersionCatalog duplicateLabels = newValidAgent().getVersionCatalog();
        duplicateLabels.getOnlineVersions().get(0)
            .setLabels(Arrays.asList("stable", "stable"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentModelValidator.validateVersionCatalog(duplicateLabels));
        
        AgentVersionCatalog emptyProtocols = newValidAgent().getVersionCatalog();
        emptyProtocols.getOnlineVersions().get(0)
            .setProtocols(Collections.<String>emptyList());
        assertThrows(IllegalArgumentException.class,
            () -> AgentModelValidator.validateVersionCatalog(emptyProtocols));
        
        AgentVersionCatalog duplicateProtocols = newValidAgent().getVersionCatalog();
        duplicateProtocols.getOnlineVersions().get(0)
            .setProtocols(Arrays.asList("a2a", "a2a"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentModelValidator.validateVersionCatalog(duplicateProtocols));
    }
    
    @Test
    void testRejectsInvalidVersionSummaryPages() {
        Page<AgentVersionSummary> invalidMetadata = newValidVersionSummaryPage();
        invalidMetadata.setPageNumber(0);
        assertThrows(IllegalArgumentException.class,
            () -> AgentModelValidator.validateVersionSummaryPage(invalidMetadata));
        
        Page<AgentVersionSummary> oversized = newValidVersionSummaryPage();
        oversized.setTotalCount(101);
        oversized.setPageItems(Collections.nCopies(101, newValidVersionSummary()));
        assertThrows(IllegalArgumentException.class,
            () -> AgentModelValidator.validateVersionSummaryPage(oversized));
    }
    
    @Test
    void testRejectsInvalidAgentCollectionsAndLifecycleCounts() {
        Agent oversizedTags = newValidAgent();
        oversizedTags.setTags(Collections.nCopies(33, "tag"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentModelValidator.validateAgent(oversizedTags));
        
        Agent duplicateTags = newValidAgent();
        duplicateTags.setTags(Arrays.asList("demo", "demo"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentModelValidator.validateAgent(duplicateTags));
        
        Agent oversizedExtensions = newValidAgent();
        Map<String, Object> extensions = new LinkedHashMap<String, Object>();
        for (int i = 0; i < 33; i++) {
            extensions.put("example.com/key-" + i, i);
        }
        oversizedExtensions.setExtensions(extensions);
        assertThrows(IllegalArgumentException.class,
            () -> AgentModelValidator.validateAgent(oversizedExtensions));
        
        Agent missingOnlineCount = newValidAgent();
        missingOnlineCount.getVersionInfo().setOnlineCnt(null);
        assertThrows(IllegalArgumentException.class,
            () -> AgentModelValidator.validateAgent(missingOnlineCount));
        
        Agent inconsistentOnlineCount = newValidAgent();
        inconsistentOnlineCount.getVersionInfo().setOnlineCnt(0);
        assertThrows(IllegalArgumentException.class,
            () -> AgentModelValidator.validateAgent(inconsistentOnlineCount));
    }
    
    @Test
    void testAcceptsUserDefinedTagPrefixes() {
        Agent agent = newValidAgent();
        agent.setTags(Collections.singletonList("__nacos.agent.internal"));
        
        assertDoesNotThrow(() -> AgentModelValidator.validateAgent(agent));
    }
    
    @Test
    void testRejectsUnsupportedVisibilityScope() {
        Agent lowercaseScope = newValidAgent();
        lowercaseScope.setScope("public");
        assertThrows(IllegalArgumentException.class,
            () -> AgentModelValidator.validateAgent(lowercaseScope));
        
        Agent customScope = newValidAgent();
        customScope.setScope("TEAM");
        assertThrows(IllegalArgumentException.class,
            () -> AgentModelValidator.validateAgent(customScope));
    }
    
    @Test
    void testRejectsInconsistentCatalogLabels() {
        Agent catalogLabelMismatch = newValidAgent();
        catalogLabelMismatch.getVersionInfo().getLabels().put("stable", "2.0.0");
        assertThrows(IllegalArgumentException.class,
            () -> AgentModelValidator.validateAgent(catalogLabelMismatch));
        
        Agent catalogMissingOnlineLabel = newValidAgent();
        catalogMissingOnlineLabel.getVersionInfo().getLabels().put("canary", "1.0.0");
        assertThrows(IllegalArgumentException.class,
            () -> AgentModelValidator.validateAgent(catalogMissingOnlineLabel));
    }
    
    @Test
    void testRejectsInvalidCallInterfaceCollections() {
        AgentVersionDetail noCallInterface = newValidVersionDetail();
        noCallInterface.setCallInterfaces(Collections.<AgentCallInterface>emptyList());
        assertThrows(IllegalArgumentException.class,
            () -> AgentModelValidator.validateVersionDetail(noCallInterface));
        
        AgentCallInterface noEndpointSource = newValidCallInterface();
        noEndpointSource.setEndpointSourceOrder(Collections.<EndpointSource>emptyList());
        assertThrows(IllegalArgumentException.class,
            () -> AgentModelValidator.validateCallInterface(noEndpointSource));
        
        AgentCallInterface duplicateEndpointSource = newValidCallInterface();
        duplicateEndpointSource.setEndpointSourceOrder(
            Arrays.asList(EndpointSource.RUNTIME, EndpointSource.RUNTIME));
        assertThrows(IllegalArgumentException.class,
            () -> AgentModelValidator.validateCallInterface(duplicateEndpointSource));
        
        AgentCallInterface tooManyEndpoints = newValidCallInterface();
        tooManyEndpoints.setDeclaredEndpoints(
            Collections.nCopies(65, newDeclaredEndpoint("https://agent.example.com/a2a")));
        assertThrows(IllegalArgumentException.class,
            () -> AgentModelValidator.validateCallInterface(tooManyEndpoints));
    }
    
    @Test
    void testRejectsInvalidRuntimeEndpointCollections() {
        RuntimeEndpointSnapshot oversized = newValidRuntimeEndpointSnapshot();
        oversized.setItems(Collections.nCopies(1001, oversized.getItems().get(0)));
        assertThrows(IllegalArgumentException.class,
            () -> AgentModelValidator.validateRuntimeEndpointSnapshot(oversized));
        
        RuntimeEndpointSnapshot duplicateEndpoint = newValidRuntimeEndpointSnapshot();
        duplicateEndpoint.setItems(Arrays.asList(duplicateEndpoint.getItems().get(0),
            duplicateEndpoint.getItems().get(0)));
        assertThrows(IllegalArgumentException.class,
            () -> AgentModelValidator.validateRuntimeEndpointSnapshot(duplicateEndpoint));
        
        RuntimeEndpointSnapshot noBindings = newValidRuntimeEndpointSnapshot();
        noBindings.getItems().get(0).setBindings(
            Collections.<RuntimeVersionBinding>emptyList());
        assertThrows(IllegalArgumentException.class,
            () -> AgentModelValidator.validateRuntimeEndpointSnapshot(noBindings));
        
        RuntimeEndpointSnapshot selectedVersionMismatch = newValidRuntimeEndpointSnapshot();
        selectedVersionMismatch.setVersion("3.0.0");
        assertThrows(IllegalArgumentException.class,
            () -> AgentModelValidator.validateRuntimeEndpointSnapshot(selectedVersionMismatch));
        
        RuntimeEndpointSnapshot duplicateBinding = newValidRuntimeEndpointSnapshot();
        RuntimeVersionBinding binding = duplicateBinding.getItems().get(0).getBindings().get(0);
        duplicateBinding.getItems().get(0).setBindings(Arrays.asList(binding, binding));
        assertThrows(IllegalArgumentException.class,
            () -> AgentModelValidator.validateRuntimeEndpointSnapshot(duplicateBinding));
        
        RuntimeEndpointSnapshot endpointHealth = newValidRuntimeEndpointSnapshot();
        endpointHealth.getItems().get(0).getEndpoint().setHealthy(true);
        assertThrows(IllegalArgumentException.class,
            () -> AgentModelValidator.validateRuntimeEndpointSnapshot(endpointHealth));
    }
    
    @Test
    void testRejectsInvalidUrisAndLengths() {
        Agent emptyIconUrl = newValidAgent();
        emptyIconUrl.setIconUrl("");
        assertThrows(IllegalArgumentException.class,
            () -> AgentModelValidator.validateAgent(emptyIconUrl));
        
        Agent relativeIconUrl = newValidAgent();
        relativeIconUrl.setIconUrl("icons/agent.png");
        assertThrows(IllegalArgumentException.class,
            () -> AgentModelValidator.validateAgent(relativeIconUrl));
        
        Agent malformedProviderUrl = newValidAgent();
        malformedProviderUrl.getProvider().setUrl("https://[");
        assertThrows(IllegalArgumentException.class,
            () -> AgentModelValidator.validateAgent(malformedProviderUrl));
        
        Agent emptyOwner = newValidAgent();
        emptyOwner.setOwner("");
        assertThrows(IllegalArgumentException.class,
            () -> AgentModelValidator.validateAgent(emptyOwner));
        
        Agent oversizedDisplayName = newValidAgent();
        oversizedDisplayName.setDisplayName(repeated('a', 129));
        assertThrows(IllegalArgumentException.class,
            () -> AgentModelValidator.validateAgent(oversizedDisplayName));
    }
    
    @Test
    void testRejectsLatestVersionCatalogMismatch() {
        Agent agent = newValidAgent();
        agent.getVersionInfo().getLabels().put("latest", "1.1.0");
        
        assertThrows(IllegalArgumentException.class,
            () -> AgentModelValidator.validateAgent(agent));
    }
    
    @Test
    void testRejectsDuplicateCallInterfaceProtocol() {
        AgentVersionDetail detail = newValidVersionDetail();
        detail.getCallInterfaces().add(newValidCallInterface());
        
        assertThrows(IllegalArgumentException.class,
            () -> AgentModelValidator.validateVersionDetail(detail));
    }
    
    @Test
    void testRejectsDuplicateDeclaredEndpointNaturalKey() {
        AgentVersionDetail detail = newValidVersionDetail();
        Endpoint duplicate = newDeclaredEndpoint("https://AGENT.EXAMPLE.COM/another-path");
        detail.getCallInterfaces().get(0).getDeclaredEndpoints().add(duplicate);
        
        assertThrows(IllegalArgumentException.class,
            () -> AgentModelValidator.validateVersionDetail(detail));
    }
    
    @Test
    void testRejectsVersionRangeThatDoesNotContainRuntimeVersion() {
        RuntimeEndpointSnapshot snapshot = newValidRuntimeEndpointSnapshot();
        RuntimeVersionBinding binding = snapshot.getItems().get(0).getBindings().get(0);
        binding.setRuntimeVersion("2.0.0");
        
        assertThrows(IllegalArgumentException.class,
            () -> AgentModelValidator.validateRuntimeEndpointSnapshot(snapshot));
    }
    
    @Test
    void testRejectsInconsistentRuntimeEndpointState() {
        RuntimeEndpointSnapshot snapshot = newValidRuntimeEndpointSnapshot();
        RuntimeEndpointSnapshotItem item = snapshot.getItems().get(0);
        item.setEnabled(false);
        item.setState(RuntimeEndpointState.AVAILABLE);
        
        assertThrows(IllegalArgumentException.class,
            () -> AgentModelValidator.validateRuntimeEndpointSnapshot(snapshot));
    }
    
    @Test
    void testRejectsMissingRequiredWrapperValues() {
        Agent agent = newValidAgent();
        agent.setMetaVersion(null);
        assertThrows(IllegalArgumentException.class,
            () -> AgentModelValidator.validateAgent(agent));
        
        AgentVersionDetail detail = newValidVersionDetail();
        detail.setCreateTime(null);
        assertThrows(IllegalArgumentException.class,
            () -> AgentModelValidator.validateVersionDetail(detail));
        
        RuntimeEndpointSnapshot snapshot = newValidRuntimeEndpointSnapshot();
        snapshot.getItems().get(0).setHealthy(null);
        assertThrows(IllegalArgumentException.class,
            () -> AgentModelValidator.validateRuntimeEndpointSnapshot(snapshot));
    }
    
    @Test
    void testRejectsInvalidStatusValues() {
        Agent missingResourceStatus = newValidAgent();
        missingResourceStatus.setStatus(null);
        assertThrows(IllegalArgumentException.class,
            () -> AgentModelValidator.validateAgent(missingResourceStatus));
        
        Agent invalidResourceStatus = newValidAgent();
        invalidResourceStatus.setStatus("ENABLE");
        assertThrows(IllegalArgumentException.class,
            () -> AgentModelValidator.validateAgent(invalidResourceStatus));
        
        AgentVersionDetail missingVersionStatus = newValidVersionDetail();
        missingVersionStatus.setStatus(null);
        assertThrows(IllegalArgumentException.class,
            () -> AgentModelValidator.validateVersionDetail(missingVersionStatus));
        
        AgentVersionDetail invalidVersionStatus = newValidVersionDetail();
        invalidVersionStatus.setStatus("published");
        assertThrows(IllegalArgumentException.class,
            () -> AgentModelValidator.validateVersionDetail(invalidVersionStatus));
    }
    
    @Test
    void testRejectsInconsistentLifecyclePointersAndLabels() {
        Agent sameWorkingVersion = newValidAgent();
        sameWorkingVersion.getVersionInfo().setEditingVersion("2.0.0");
        sameWorkingVersion.getVersionInfo().setReviewingVersion("2.0.0");
        assertThrows(IllegalArgumentException.class,
            () -> AgentModelValidator.validateAgent(sameWorkingVersion));
        
        Agent workingVersionOnline = newValidAgent();
        workingVersionOnline.getVersionInfo().setEditingVersion("1.0.0");
        assertThrows(IllegalArgumentException.class,
            () -> AgentModelValidator.validateAgent(workingVersionOnline));
        
        Agent labelTargetsReviewing = newValidAgent();
        labelTargetsReviewing.getVersionInfo().setReviewingVersion("2.0.0");
        labelTargetsReviewing.getVersionInfo().getLabels().put("candidate", "2.0.0");
        assertThrows(IllegalArgumentException.class,
            () -> AgentModelValidator.validateAgent(labelTargetsReviewing));
        
    }
    
    @Test
    void testRejectsMissingNativeDescriptorAfterBinding() {
        AgentCallInterface callInterface = newValidCallInterface();
        callInterface.setNativeDescriptor(null);
        assertThrows(IllegalArgumentException.class,
            () -> AgentModelValidator.validateCallInterface(callInterface));
    }
    
    @Test
    void testRejectsNonCanonicalRuntimeVersionRange() {
        RuntimeEndpointSnapshot snapshot = newValidRuntimeEndpointSnapshot();
        snapshot.getItems().get(0).getBindings().get(0)
            .setVersionRange("[1.0.6,1.0.6]");
        
        assertThrows(IllegalArgumentException.class,
            () -> AgentModelValidator.validateRuntimeEndpointSnapshot(snapshot));
    }
    
    private Agent newValidAgent() {
        AgentProvider provider = new AgentProvider();
        provider.setName("Nacos");
        provider.setUrl("https://nacos.io");
        
        AgentVersionInfo versionInfo = new AgentVersionInfo();
        versionInfo.setOnlineCnt(1);
        Map<String, String> labels = new LinkedHashMap<String, String>();
        labels.put("latest", "1.0.0");
        labels.put("stable", "1.0.0");
        versionInfo.setLabels(labels);
        
        AgentVersionCatalogEntry catalogEntry = new AgentVersionCatalogEntry();
        catalogEntry.setVersion("1.0.0");
        catalogEntry.setLabels(Collections.singletonList("stable"));
        catalogEntry.setProtocols(Collections.singletonList("a2a"));
        AgentVersionCatalog catalog = new AgentVersionCatalog();
        catalog.setLatestVersion("1.0.0");
        catalog.setOnlineVersions(Collections.singletonList(catalogEntry));
        
        Agent agent = new Agent();
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
        agent.setVersionCatalog(catalog);
        agent.setMetaVersion(1L);
        agent.setCreateTime(1L);
        agent.setUpdateTime(2L);
        return agent;
    }
    
    private AgentVersionDetail newValidVersionDetail() {
        AgentVersionDetail detail = new AgentVersionDetail();
        detail.setNamespaceId("public");
        detail.setAgentName("Demo Agent");
        detail.setVersion("1.0.0");
        detail.setStatus(AiConstants.Agent.VERSION_STATUS_ONLINE);
        detail.setCallInterfaces(new ArrayList<AgentCallInterface>(
            Collections.singletonList(newValidCallInterface())));
        detail.setAuthor("nacos");
        detail.setChangeDescription("Initial online version");
        detail.setContentDigest(CONTENT_DIGEST);
        detail.setCreateTime(1L);
        detail.setUpdateTime(2L);
        return detail;
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
    
    private AgentSummary newValidAgentSummary() {
        Agent agent = newValidAgent();
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
        summary.setVersionCatalog(agent.getVersionCatalog());
        summary.setMetaVersion(agent.getMetaVersion());
        summary.setCreateTime(agent.getCreateTime());
        summary.setUpdateTime(agent.getUpdateTime());
        return summary;
    }
    
    private AgentCallInterface newValidCallInterface() {
        AgentCallInterface callInterface = new AgentCallInterface();
        callInterface.setProtocol("a2a");
        callInterface.setProtocolVersion("1.0.0");
        callInterface.setDescriptorMediaType("application/json");
        callInterface.setNativeDescriptor(Collections.singletonMap("name", "Demo Agent"));
        callInterface.setEndpointSourceOrder(
            Arrays.asList(EndpointSource.RUNTIME, EndpointSource.DECLARED));
        List<Endpoint> endpoints = new ArrayList<Endpoint>();
        endpoints.add(newDeclaredEndpoint("https://agent.example.com/a2a"));
        callInterface.setDeclaredEndpoints(endpoints);
        return callInterface;
    }
    
    private RuntimeEndpointSnapshot newValidRuntimeEndpointSnapshot() {
        RuntimeVersionBinding binding = new RuntimeVersionBinding();
        binding.setRuntimeVersion("1.0.6");
        binding.setVersionRange("[1.0.0,2.0.0)");
        
        RuntimeEndpointSnapshotItem item = new RuntimeEndpointSnapshotItem();
        item.setEndpoint(newDeclaredEndpoint("https://runtime.example.com/a2a"));
        item.setBindings(Collections.singletonList(binding));
        item.setState(RuntimeEndpointState.AVAILABLE);
        item.setEnabled(true);
        item.setHealthy(true);
        item.setLastUpdatedTime(2L);
        
        RuntimeEndpointSnapshot snapshot = new RuntimeEndpointSnapshot();
        snapshot.setNamespaceId("public");
        snapshot.setAgentName("Demo Agent");
        snapshot.setProtocol("a2a");
        snapshot.setVersion("1.0.0");
        snapshot.setItems(Collections.singletonList(item));
        return snapshot;
    }
    
    private Endpoint newDeclaredEndpoint(String uri) {
        Endpoint endpoint = new Endpoint();
        endpoint.setUri(uri);
        endpoint.setTransport("JSON-RPC");
        endpoint.setPriority(0);
        endpoint.setWeight(1.0D);
        endpoint.setMetadata(Collections.singletonMap("zone", "cn-hangzhou-a"));
        return endpoint;
    }
    
    private String repeated(char value, int count) {
        char[] result = new char[count];
        Arrays.fill(result, value);
        return new String(result);
    }
}
