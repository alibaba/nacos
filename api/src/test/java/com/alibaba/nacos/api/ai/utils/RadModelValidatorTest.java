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

import java.util.List;
import java.util.LinkedHashMap;
import com.alibaba.nacos.api.ai.model.agent.AgentProvider;
import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.ai.model.agent.RuntimeVersionBinding;
import com.alibaba.nacos.api.ai.model.agent.AgentSummary;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionInfo;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionSummary;
import com.alibaba.nacos.api.ai.model.agent.AgentCallInterface;
import com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryFilter;
import com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.agent.AgentEndpointRegistrationBatch;
import com.alibaba.nacos.api.ai.model.agent.AgentReference;
import com.alibaba.nacos.api.ai.model.agent.AgentSearchRequest;
import com.alibaba.nacos.api.ai.model.agent.EndpointSet;
import com.alibaba.nacos.api.model.Page;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RadModelValidatorTest {
    
    private static final String CONTENT_DIGEST =
        "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    
    private static final String RUNTIME_REVISION =
        "murmur3-x64-128-v1:0123456789abcdef0123456789abcdef";
    
    @Test
    void shouldValidateOptionalDiscoveryCatalogMetadata() {
        AgentDiscoveryResult result = newValidDiscoveryResult();
        assertDoesNotThrow(() -> RadModelValidator.validate(result));
        result.setDescription(repeat('a', 2048));
        ArrayList<String> tags = new ArrayList<String>();
        for (int i = 0; i < 32; i++) {
            tags.add(i + repeat('x', 62));
        }
        result.setTags(tags);
        assertDoesNotThrow(() -> RadModelValidator.validate(result));
        result.setDescription(repeat('a', 2049));
        assertThrows(IllegalArgumentException.class, () -> RadModelValidator.validate(result));
        result.setDescription(null);
        tags.add("overflow");
        assertThrows(IllegalArgumentException.class, () -> RadModelValidator.validate(result));
        result.setTags(Collections.singletonList(repeat('a', 65)));
        assertThrows(IllegalArgumentException.class, () -> RadModelValidator.validate(result));
        result.setTags(Arrays.asList("duplicate", "duplicate"));
        assertThrows(IllegalArgumentException.class, () -> RadModelValidator.validate(result));
        result.setTags(Collections.singletonList(""));
        assertThrows(IllegalArgumentException.class, () -> RadModelValidator.validate(result));
        result.setTags(Collections.<String>singletonList(null));
        assertThrows(IllegalArgumentException.class, () -> RadModelValidator.validate(result));
        result.setTags(null);
        assertDoesNotThrow(() -> RadModelValidator.validate(result));
    }
    
    @Test
    void shouldAllowEndpointBindingsWithoutBatchDefaultsAndRejectMalformedDefaults() {
        AgentEndpointRegistrationBatch batch = newValidRegistrationBatch();
        batch.setRuntimeVersion(null);
        batch.setVersionRange(null);
        for (Endpoint endpoint : batch.getEndpoints()) {
            endpoint.setBindings(Collections.singletonList(newBinding("2.0.0", "[2.0.0]")));
        }
        assertDoesNotThrow(() -> RadModelValidator.validate("public", batch));
        batch.setRuntimeVersion("");
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate("public", batch));
        batch.setRuntimeVersion(null);
        batch.setVersionRange("");
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate("public", batch));
        batch.setVersionRange(null);
        batch.getEndpoints().get(0).setBindings(Collections.emptyList());
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate("public", batch));
    }
    
    @Test
    void shouldAcceptExplicitRegistrationHealth() {
        AgentEndpointRegistrationBatch batch = newValidRegistrationBatch();
        batch.getEndpoints().get(0).setHealthy(false);
        assertDoesNotThrow(() -> RadModelValidator.validate("public", batch));
        batch.getEndpoints().get(0).setHealthy(true);
        assertDoesNotThrow(() -> RadModelValidator.validate("public", batch));
    }
    
    @Test
    void shouldRejectManagementOnlyFieldsInDiscoveryAtEveryLayer() {
        AgentDiscoveryResult result = newValidDiscoveryResult();
        AgentCallInterface callInterface = result.getCallInterfaces().get(0);
        callInterface.setEndpointSourceOrder(Collections.singletonList(EndpointSource.RUNTIME));
        assertThrows(IllegalArgumentException.class, () -> RadModelValidator.validate(result));
        callInterface.setEndpointSourceOrder(null);
        EndpointSet runtime = callInterface.getEndpointSets().get(0);
        runtime.setLastUpdatedTime(1L);
        assertThrows(IllegalArgumentException.class, () -> RadModelValidator.validate(result));
        runtime.setLastUpdatedTime(null);
        Endpoint endpoint = runtime.getEndpoints().get(0);
        endpoint.setEnabled(false);
        assertThrows(IllegalArgumentException.class, () -> RadModelValidator.validate(result));
        endpoint.setEnabled(true);
        assertDoesNotThrow(() -> RadModelValidator.validate(result));
    }
    
    @Test
    void shouldAcceptCompleteRadModels() {
        assertDoesNotThrow(() -> RadModelValidator.validate("public", newValidSearchRequest()));
        assertDoesNotThrow(() -> RadModelValidator.validate(newValidCatalogEntry()));
        assertDoesNotThrow(() -> RadModelValidator.validateCatalogPage(newValidCatalogPage()));
        assertDoesNotThrow(() -> RadModelValidator.validate(newValidDiscoveryRequest()));
        assertDoesNotThrow(() -> RadModelValidator.validate(newValidDiscoveryResult()));
        assertDoesNotThrow(() -> RadModelValidator.validate("public", newValidRegistrationBatch()));
        assertDoesNotThrow(() -> RadModelValidator.validateDeregistration("public", "Demo Agent",
            "a2a", newValidDeregistrationEndpoints()));
    }
    
    @Test
    void shouldRejectManagementFieldsAndOfflineLabelsInSearchSummary() {
        AgentSummary entry = newValidCatalogEntry();
        entry.setNamespaceId("public");
        assertThrows(IllegalArgumentException.class, () -> RadModelValidator.validate(entry));
        entry.setNamespaceId(null);
        entry.setExtensions(Collections.singletonMap("private", "value"));
        assertThrows(IllegalArgumentException.class, () -> RadModelValidator.validate(entry));
        entry.setExtensions(null);
        entry.getVersionInfo().setEditingVersion("3.0.0");
        assertThrows(IllegalArgumentException.class, () -> RadModelValidator.validate(entry));
        entry.getVersionInfo().setEditingVersion(null);
        entry.getVersionInfo().getLabels().put("archived", "0.9.0");
        assertThrows(IllegalArgumentException.class, () -> RadModelValidator.validate(entry));
        entry.getVersionInfo().getLabels().remove("archived");
        entry.getVersionInfo().getOnlineVersions().get(0).setStatus("online");
        assertThrows(IllegalArgumentException.class, () -> RadModelValidator.validate(entry));
        entry.getVersionInfo().getOnlineVersions().get(0).setStatus(null);
        RadModelValidator.validate(entry);
    }
    
    @Test
    void shouldRejectReferenceWithVersionAndLabel() {
        AgentDiscoveryRequest request = newValidDiscoveryRequest();
        request.getReference().setVersion("1.1.0");
        
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate(request));
    }
    
    @Test
    void shouldRejectPresentButEmptyOptionalArrays() {
        AgentSearchRequest searchRequest = newValidSearchRequest();
        searchRequest.setTagsAll(Collections.<String>emptyList());
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate("public", searchRequest));
        
        AgentSearchRequest emptyProtocols = newValidSearchRequest();
        emptyProtocols.setProtocolsAny(Collections.<String>emptyList());
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate("public", emptyProtocols));
        
        AgentDiscoveryFilter filter = new AgentDiscoveryFilter();
        filter.setProtocols(Collections.<String>emptyList());
        assertThrows(IllegalArgumentException.class, () -> RadModelValidator.validate(filter));
        
        AgentVersionSummary catalog = newValidVersionCatalog("1.1.0", "stable");
        catalog.setLabels(Collections.<String>emptyList());
        assertThrows(IllegalArgumentException.class, () -> RadModelValidator.validate(catalog));
        
        AgentVersionSummary missingProtocols = newValidVersionCatalog("1.1.0", "stable");
        missingProtocols.setProtocols(null);
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate(missingProtocols));
    }
    
    @Test
    void shouldRejectCatalogWhoseLatestVersionIsAbsent() {
        AgentSummary entry = newValidCatalogEntry();
        entry.getVersionInfo().setLabels(new LinkedHashMap<String, String>(
            Collections.singletonMap("latest", "2.0.0")));
        
        assertThrows(IllegalArgumentException.class, () -> RadModelValidator.validate(entry));
    }
    
    @Test
    void shouldRejectCatalogThatIsNotSemverDescending() {
        AgentSummary entry = newValidCatalogEntry();
        Collections.reverse(entry.getVersionInfo().getOnlineVersions());
        
        assertThrows(IllegalArgumentException.class, () -> RadModelValidator.validate(entry));
    }
    
    @Test
    void shouldAcceptRuntimeEndpointWithDefaultHealth() {
        AgentDiscoveryResult result = newValidDiscoveryResult();
        result.getCallInterfaces().get(0).getEndpointSets().get(0).getEndpoints().get(0)
            .setHealthy(true);
        
        assertDoesNotThrow(() -> RadModelValidator.validate(result));
    }
    
    @Test
    void shouldAcceptDeclaredEndpointWithDefaultHealth() {
        AgentDiscoveryResult result = newValidDiscoveryResult();
        result.getCallInterfaces().get(0).getEndpointSets().get(1).getEndpoints().get(0)
            .setHealthy(true);
        
        assertDoesNotThrow(() -> RadModelValidator.validate(result));
    }
    
    @Test
    void shouldRejectRegistrationRangeThatExcludesRuntimeVersion() {
        AgentEndpointRegistrationBatch batch = newValidRegistrationBatch();
        batch.setVersionRange("[2.0.0,3.0.0)");
        
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate("public", batch));
    }
    
    @Test
    void shouldRejectDuplicateEndpointNaturalKey() {
        AgentEndpointRegistrationBatch batch = newValidRegistrationBatch();
        Endpoint duplicate = newEndpoint("https://RUNTIME.EXAMPLE.COM/another-path", null);
        batch.getEndpoints().add(duplicate);
        
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate("public", batch));
    }
    
    @Test
    void shouldIgnoreNonKeyDeregistrationFields() {
        List<Endpoint> endpoints = newValidDeregistrationEndpoints();
        endpoints.get(0).setMetadata(Collections.singletonMap("zone", "cn-hangzhou-a"));
        endpoints.get(0).setPriority(-1);
        endpoints.get(0).setHealthy(false);
        endpoints.get(0).setEnabled(false);
        assertDoesNotThrow(() -> RadModelValidator.validateDeregistration(
            "public", "Demo Agent", "a2a", endpoints));
    }
    
    @Test
    void shouldRejectUnsortedOrDuplicateCatalogPageItems() {
        Page<AgentSummary> unsortedPage = newValidCatalogPage();
        Collections.reverse(unsortedPage.getPageItems());
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validateCatalogPage(unsortedPage));
        
        Page<AgentSummary> duplicatePage = newValidCatalogPage();
        duplicatePage.getPageItems().get(1)
            .setAgentName(duplicatePage.getPageItems().get(0).getAgentName());
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validateCatalogPage(duplicatePage));
    }
    
    @Test
    void shouldRejectMissingNativeDescriptorAfterBinding() {
        AgentDiscoveryResult result = newValidDiscoveryResult();
        result.getCallInterfaces().get(0).setNativeDescriptor(null);
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate(result));
    }
    
    @Test
    void shouldRequireCanonicalDiscoveryEndpointButNormalizeRegistrationInput() {
        AgentDiscoveryResult nonCanonicalResult = newValidDiscoveryResult();
        nonCanonicalResult.getCallInterfaces().get(0).getEndpointSets().get(0).getEndpoints().get(0)
            .setUri("HTTPS://RUNTIME.EXAMPLE.COM/a2a");
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate(nonCanonicalResult));
        
        AgentDiscoveryResult negativePriorityResult = newValidDiscoveryResult();
        negativePriorityResult.getCallInterfaces().get(0).getEndpointSets().get(0)
            .getEndpoints().get(0)
            .setPriority(-1);
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate(negativePriorityResult));
        
        assertDoesNotThrow(() -> RadModelValidator.validate("public", newValidRegistrationBatch()));
    }
    
    @Test
    void shouldValidateSearchPaginationAndCatalogPageMetadata() {
        AgentSearchRequest invalidPageNo = newValidSearchRequest();
        invalidPageNo.setPageNo(0);
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate("public", invalidPageNo));
        
        AgentSearchRequest invalidPageSize = newValidSearchRequest();
        invalidPageSize.setPageSize(101);
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate("public", invalidPageSize));
        
        Page<AgentSummary> invalidMetadata = newValidCatalogPage();
        invalidMetadata.setTotalCount(-1);
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validateCatalogPage(invalidMetadata));
        
        Page<AgentSummary> oversizedPage = newValidCatalogPage();
        oversizedPage.setPageItems(new ArrayList<AgentSummary>(
            Collections.nCopies(101, newValidCatalogEntry())));
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validateCatalogPage(oversizedPage));
    }
    
    @Test
    void shouldRejectDuplicateCatalogVersionsAndLabels() {
        AgentSummary duplicateVersion = newValidCatalogEntry();
        duplicateVersion.getVersionInfo().getOnlineVersions().get(1).setVersion("1.1.0");
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate(duplicateVersion));
        
        AgentSummary duplicateLabel = newValidCatalogEntry();
        duplicateLabel.getVersionInfo().getOnlineVersions().get(1)
            .setLabels(Collections.singletonList("stable"));
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate(duplicateLabel));
    }
    
    @Test
    void shouldValidateReferenceByExactVersionAndPublicNestedModels() {
        AgentReference reference = new AgentReference();
        reference.setAgentName("Demo Agent");
        reference.setVersion("1.1.0");
        assertDoesNotThrow(() -> RadModelValidator.validate(reference));
        
        AgentDiscoveryResult result = newValidDiscoveryResult();
        EndpointSet endpointSet = result.getCallInterfaces().get(0).getEndpointSets().get(0);
        AgentCallInterface callInterface = result.getCallInterfaces().get(0);
        assertDoesNotThrow(() -> RadModelValidator.validate(endpointSet));
        assertDoesNotThrow(() -> RadModelValidator.validate(callInterface));
    }
    
    @Test
    void shouldUseExactRuntimeVersionWhenRangeIsAbsent() {
        AgentEndpointRegistrationBatch batch = newValidRegistrationBatch();
        batch.setVersionRange(null);
        
        assertDoesNotThrow(() -> RadModelValidator.validate("public", batch));
    }
    
    @Test
    void shouldRejectDuplicateDiscoveryProtocolsAndEndpointSources() {
        AgentDiscoveryResult duplicateProtocol = newValidDiscoveryResult();
        AgentCallInterface callInterface = duplicateProtocol.getCallInterfaces().get(0);
        duplicateProtocol.setCallInterfaces(Arrays.asList(callInterface, callInterface));
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate(duplicateProtocol));
        
        AgentCallInterface duplicateSource =
            newValidDiscoveryResult().getCallInterfaces().get(0);
        EndpointSet runtimeSet = duplicateSource.getEndpointSets().get(0);
        duplicateSource.setEndpointSets(Arrays.asList(runtimeSet, runtimeSet));
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate(duplicateSource));
    }
    
    @Test
    void shouldValidateDiscoveryEndpointOrder() {
        AgentDiscoveryResult sortedResult = newValidDiscoveryResult();
        EndpointSet sortedRuntimeSet = sortedResult.getCallInterfaces().get(0).getEndpointSets()
            .get(0);
        Endpoint first =
            newDiscoveryEndpoint("https://a.example.com:443/a2a", true, true);
        Endpoint second =
            newDiscoveryEndpoint("https://b.example.com:443/a2a", true, true);
        sortedRuntimeSet.setEndpoints(Arrays.asList(first, second));
        assertDoesNotThrow(() -> RadModelValidator.validate(sortedResult));
        
        AgentDiscoveryResult unsortedResult = newValidDiscoveryResult();
        EndpointSet unsortedRuntimeSet = unsortedResult.getCallInterfaces().get(0)
            .getEndpointSets().get(0);
        Endpoint higherPriority =
            newDiscoveryEndpoint("https://a.example.com:443/a2a", true, true);
        higherPriority.setPriority(1);
        Endpoint lowerPriority =
            newDiscoveryEndpoint("https://b.example.com:443/a2a", true, true);
        unsortedRuntimeSet.setEndpoints(Arrays.asList(higherPriority, lowerPriority));
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate(unsortedResult));
    }
    
    @Test
    void shouldRejectInvalidEndpointSourceRevisions() {
        AgentDiscoveryResult mismatchedDeclaredRevision = newValidDiscoveryResult();
        mismatchedDeclaredRevision.getCallInterfaces().get(0).getEndpointSets().get(1)
            .setSourceRevision(
                "sha256:ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate(mismatchedDeclaredRevision));
        
        AgentDiscoveryResult invalidRuntimeRevision = newValidDiscoveryResult();
        invalidRuntimeRevision.getCallInterfaces().get(0).getEndpointSets().get(0)
            .setSourceRevision("runtime-revision");
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate(invalidRuntimeRevision));
    }
    
    @Test
    void shouldValidateRuntimeDiscoveryBindings() {
        AgentDiscoveryResult missingBindings = newValidDiscoveryResult();
        missingBindings.getCallInterfaces().get(0).getEndpointSets().get(0).getEndpoints().get(0)
            .setBindings(null);
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate(missingBindings));
        
        AgentDiscoveryResult declaredBindings = newValidDiscoveryResult();
        declaredBindings.getCallInterfaces().get(0).getEndpointSets().get(1).getEndpoints().get(0)
            .setBindings(Collections.singletonList(newBinding("1.0.0", "[1.0.0]")));
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate(declaredBindings));
        
        AgentDiscoveryResult nonCanonicalRange = newValidDiscoveryResult();
        nonCanonicalRange.getCallInterfaces().get(0).getEndpointSets().get(0).getEndpoints().get(0)
            .setBindings(Collections.singletonList(newBinding("1.0.0", "[1.0.0,1.0.0]")));
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate(nonCanonicalRange));
        
        AgentDiscoveryResult excludedRuntimeVersion = newValidDiscoveryResult();
        excludedRuntimeVersion.getCallInterfaces().get(0).getEndpointSets().get(0).getEndpoints()
            .get(0).setBindings(
                Collections.singletonList(newBinding("2.0.0", "[1.0.0]")));
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate(excludedRuntimeVersion));
        
        AgentDiscoveryResult unsortedBindings = newValidDiscoveryResult();
        unsortedBindings.getCallInterfaces().get(0).getEndpointSets().get(0).getEndpoints().get(0)
            .setBindings(Arrays.asList(newBinding("2.0.0", "[2.0.0]"),
                newBinding("1.0.0", "[1.0.0]")));
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate(unsortedBindings));
        
        AgentDiscoveryResult duplicateBindings = newValidDiscoveryResult();
        duplicateBindings.getCallInterfaces().get(0).getEndpointSets().get(0).getEndpoints()
            .get(0).setBindings(Arrays.asList(newBinding("1.0.0", "[1.0.0]"),
                newBinding("1.0.0", "[1.0.0]")));
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate(duplicateBindings));
    }
    
    @Test
    void shouldAcceptAbsentOptionalCollectionsAndProvider() {
        AgentSearchRequest searchRequest = newValidSearchRequest();
        searchRequest.setTagsAll(null);
        searchRequest.setProtocolsAny(null);
        assertDoesNotThrow(() -> RadModelValidator.validate("public", searchRequest));
        
        AgentDiscoveryFilter filter = newValidDiscoveryRequest().getFilter();
        filter.setTransports(null);
        assertDoesNotThrow(() -> RadModelValidator.validate(filter));
        
        AgentSummary catalogEntry = newValidCatalogEntry();
        catalogEntry.setProvider(null);
        assertDoesNotThrow(() -> RadModelValidator.validate(catalogEntry));
    }
    
    @Test
    void shouldRejectInvalidCatalogTextAndUris() {
        AgentSummary relativeIcon = newValidCatalogEntry();
        relativeIcon.setIconUrl("icons/agent.png");
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate(relativeIcon));
        
        AgentSummary malformedIcon = newValidCatalogEntry();
        malformedIcon.setIconUrl("https://[");
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate(malformedIcon));
        
        AgentSummary oversizedDisplayName = newValidCatalogEntry();
        oversizedDisplayName.setDisplayName(repeat('a', 129));
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate(oversizedDisplayName));
        
        AgentSummary missingProviderName = newValidCatalogEntry();
        missingProviderName.getProvider().setName(null);
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate(missingProviderName));
        
        AgentSummary emptyProviderName = newValidCatalogEntry();
        emptyProviderName.getProvider().setName("");
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate(emptyProviderName));
        
        AgentSearchRequest nonAsciiSearch = newValidSearchRequest();
        nonAsciiSearch.setAgentNameContains("Demo\nAgent");
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate("public", nonAsciiSearch));
    }
    
    @Test
    void shouldRejectInvalidCollectionShapesAndNullModels() {
        Page<AgentSummary> missingItems = newValidCatalogPage();
        missingItems.setPageItems(null);
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validateCatalogPage(missingItems));
        
        AgentDiscoveryResult missingCallInterfaces = newValidDiscoveryResult();
        missingCallInterfaces.setCallInterfaces(null);
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate(missingCallInterfaces));
        
        AgentDiscoveryResult tooManyCallInterfaces = newValidDiscoveryResult();
        AgentCallInterface callInterface =
            tooManyCallInterfaces.getCallInterfaces().get(0);
        tooManyCallInterfaces.setCallInterfaces(new ArrayList<AgentCallInterface>(
            Collections.nCopies(17, callInterface)));
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate(tooManyCallInterfaces));
        
        AgentEndpointRegistrationBatch tooManyEndpoints = newValidRegistrationBatch();
        Endpoint endpoint = tooManyEndpoints.getEndpoints().get(0);
        tooManyEndpoints.setEndpoints(new ArrayList<Endpoint>(
            Collections.nCopies(1001, endpoint)));
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate("public", tooManyEndpoints));
        
        AgentSearchRequest duplicateTags = newValidSearchRequest();
        duplicateTags.setTagsAll(Arrays.asList("demo", "demo"));
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate("public", duplicateTags));
        
        AgentSearchRequest duplicateProtocols = newValidSearchRequest();
        duplicateProtocols.setProtocolsAny(Arrays.asList("a2a", "a2a"));
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate("public", duplicateProtocols));
        
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate("public", (AgentSearchRequest) null));
    }
    
    private AgentSearchRequest newValidSearchRequest() {
        AgentSearchRequest request = new AgentSearchRequest();
        request.setAgentNameContains("Agent");
        request.setTagsAll(Arrays.asList("assistant", "demo"));
        request.setProtocolsAny(Collections.singletonList("a2a"));
        request.setPageNo(1);
        request.setPageSize(20);
        return request;
    }
    
    private AgentSummary newValidCatalogEntry() {
        AgentProvider provider = new AgentProvider();
        provider.setName("Nacos");
        provider.setUrl("https://nacos.io");
        
        AgentSummary entry = new AgentSummary();
        entry.setVersionInfo(new AgentVersionInfo());
        entry.setAgentName("Demo Agent");
        entry.setDisplayName("Demo Agent 展示名");
        entry.setDescription("A complete RAD catalog entry.");
        entry.setIconUrl("https://example.com/icon.png");
        entry.setProvider(provider);
        entry.setTags(Arrays.asList("assistant", "demo"));
        entry.getVersionInfo().setLabels(new LinkedHashMap<String, String>(
            Collections.singletonMap("latest", "1.1.0")));
        entry.getVersionInfo().setOnlineVersions(new ArrayList<AgentVersionSummary>(Arrays.asList(
            newValidVersionCatalog("1.1.0", "stable"),
            newValidVersionCatalog("1.0.0", "legacy"))));
        for (AgentVersionSummary version : entry.getVersionInfo().getOnlineVersions()) {
            if (version.getLabels() != null) {
                for (String label : version.getLabels()) {
                    entry.getVersionInfo().getLabels().put(label, version.getVersion());
                }
            }
        }
        return entry;
    }
    
    private AgentVersionSummary newValidVersionCatalog(String version, String label) {
        AgentVersionSummary catalog = new AgentVersionSummary();
        catalog.setVersion(version);
        catalog.setLabels(Collections.singletonList(label));
        catalog.setProtocols(Collections.singletonList("a2a"));
        return catalog;
    }
    
    private Page<AgentSummary> newValidCatalogPage() {
        AgentSummary first = newValidCatalogEntry();
        first.setAgentName("Alpha Agent");
        AgentSummary second = newValidCatalogEntry();
        second.setAgentName("Demo Agent");
        Page<AgentSummary> page = new Page<AgentSummary>();
        page.setTotalCount(2);
        page.setPageNumber(1);
        page.setPagesAvailable(1);
        page.setPageItems(new ArrayList<AgentSummary>(Arrays.asList(first, second)));
        return page;
    }
    
    private AgentDiscoveryRequest newValidDiscoveryRequest() {
        AgentReference reference = new AgentReference();
        reference.setAgentName("Demo Agent");
        reference.setLabel("latest");
        
        AgentDiscoveryFilter filter = new AgentDiscoveryFilter();
        filter.setProtocols(Collections.singletonList("a2a"));
        filter.setProtocolVersion("1.0.0");
        filter.setTransports(Collections.singletonList("JSON-RPC"));
        filter.setEndpointSources(Arrays.asList(EndpointSource.RUNTIME, EndpointSource.DECLARED));
        filter.setMetadataSelector(Collections.singletonMap("zone", "cn-hangzhou-a"));
        
        AgentDiscoveryRequest request = new AgentDiscoveryRequest();
        request.setNamespaceId("public");
        request.setReference(reference);
        request.setFilter(filter);
        return request;
    }
    
    private AgentDiscoveryResult newValidDiscoveryResult() {
        EndpointSet runtimeSet = new EndpointSet();
        runtimeSet.setSource(EndpointSource.RUNTIME);
        runtimeSet.setSourceRevision(RUNTIME_REVISION);
        runtimeSet.setEndpoints(Collections.singletonList(
            newDiscoveryEndpoint("https://runtime.example.com:443/a2a", true, true)));
        
        EndpointSet declaredSet = new EndpointSet();
        declaredSet.setSource(EndpointSource.DECLARED);
        declaredSet.setSourceRevision(CONTENT_DIGEST);
        declaredSet.setEndpoints(Collections.singletonList(
            newDiscoveryEndpoint("https://declared.example.com:443/a2a", null, false)));
        
        AgentCallInterface callInterface = new AgentCallInterface();
        callInterface.setProtocol("a2a");
        callInterface.setProtocolVersion("1.0.0");
        callInterface.setDescriptorMediaType("application/json");
        callInterface.setNativeDescriptor(Collections.singletonMap("name", "Demo Agent"));
        callInterface.setEndpointSets(Arrays.asList(runtimeSet, declaredSet));
        
        AgentDiscoveryResult result = new AgentDiscoveryResult();
        result.setNamespaceId("public");
        result.setAgentName("Demo Agent");
        result.setVersion("1.1.0");
        result.setContentDigest(CONTENT_DIGEST);
        result.setCallInterfaces(Collections.singletonList(callInterface));
        return result;
    }
    
    private AgentEndpointRegistrationBatch newValidRegistrationBatch() {
        AgentEndpointRegistrationBatch batch = new AgentEndpointRegistrationBatch();
        batch.setAgentName("Demo Agent");
        batch.setRuntimeVersion("1.0.6");
        batch.setVersionRange("[1.0.0,2.0.0)");
        batch.setProtocol("a2a");
        batch.setEndpoints(new ArrayList<Endpoint>(Collections.singletonList(
            newEndpoint("https://runtime.example.com/a2a", null))));
        return batch;
    }
    
    private List<Endpoint> newValidDeregistrationEndpoints() {
        Endpoint key = new Endpoint();
        key.setUri("https://runtime.example.com/a2a");
        key.setTransport("JSON-RPC");
        return Collections.singletonList(key);
    }
    
    private Endpoint newEndpoint(String uri, Boolean healthy) {
        Endpoint endpoint = new Endpoint();
        endpoint.setUri(uri);
        endpoint.setTransport("JSON-RPC");
        endpoint.setPriority(0);
        endpoint.setWeight(1.0D);
        endpoint.setMetadata(Collections.singletonMap("zone", "cn-hangzhou-a"));
        if (healthy != null) {
            endpoint.setHealthy(healthy);
        }
        return endpoint;
    }
    
    private Endpoint newDiscoveryEndpoint(String uri, Boolean healthy,
        boolean runtime) {
        Endpoint source = newEndpoint(uri, healthy);
        Endpoint endpoint = new Endpoint();
        endpoint.setUri(source.getUri());
        endpoint.setTransport(source.getTransport());
        endpoint.setPriority(source.getPriority());
        endpoint.setWeight(source.getWeight());
        endpoint.setMetadata(source.getMetadata());
        endpoint.setHealthy(source.getHealthy());
        if (runtime) {
            endpoint.setBindings(Collections.singletonList(newBinding("1.0.0", "[1.0.0]")));
        }
        return endpoint;
    }
    
    private RuntimeVersionBinding newBinding(String runtimeVersion, String versionRange) {
        RuntimeVersionBinding result = new RuntimeVersionBinding();
        result.setRuntimeVersion(runtimeVersion);
        result.setVersionRange(versionRange);
        return result;
    }
    
    private String repeat(char value, int count) {
        char[] result = new char[count];
        Arrays.fill(result, value);
        return new String(result);
    }
}
