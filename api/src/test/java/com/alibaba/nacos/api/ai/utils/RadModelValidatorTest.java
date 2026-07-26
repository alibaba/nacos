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

import com.alibaba.nacos.api.ai.model.agent.AgentProvider;
import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.ai.model.rad.AgentCatalogEntry;
import com.alibaba.nacos.api.ai.model.rad.AgentCatalogVersion;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryCallInterface;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryFilter;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.rad.AgentEndpointDeregistrationBatch;
import com.alibaba.nacos.api.ai.model.rad.AgentEndpointRegistrationBatch;
import com.alibaba.nacos.api.ai.model.rad.AgentReference;
import com.alibaba.nacos.api.ai.model.rad.AgentSearchRequest;
import com.alibaba.nacos.api.ai.model.rad.EndpointSet;
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
    void shouldAcceptCompleteRadModels() {
        assertDoesNotThrow(() -> RadModelValidator.validate(newValidSearchRequest()));
        assertDoesNotThrow(() -> RadModelValidator.validate(newValidCatalogEntry()));
        assertDoesNotThrow(() -> RadModelValidator.validateCatalogPage(newValidCatalogPage()));
        assertDoesNotThrow(() -> RadModelValidator.validate(newValidDiscoveryRequest()));
        assertDoesNotThrow(() -> RadModelValidator.validate(newValidDiscoveryResult()));
        assertDoesNotThrow(() -> RadModelValidator.validate(newValidRegistrationBatch()));
        assertDoesNotThrow(() -> RadModelValidator.validate(newValidDeregistrationBatch()));
    }
    
    @Test
    void shouldRejectReferenceWithVersionAndLabel() {
        AgentDiscoveryRequest request = newValidDiscoveryRequest();
        request.getReference().setVersion("1.1.0");
        
        assertThrows(IllegalArgumentException.class, () -> RadModelValidator.validate(request));
    }
    
    @Test
    void shouldRejectPresentButEmptyOptionalArrays() {
        AgentSearchRequest searchRequest = newValidSearchRequest();
        searchRequest.setTagsAll(Collections.<String>emptyList());
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate(searchRequest));
        
        AgentSearchRequest emptyProtocols = newValidSearchRequest();
        emptyProtocols.setProtocolsAny(Collections.<String>emptyList());
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate(emptyProtocols));
        
        AgentDiscoveryFilter filter = new AgentDiscoveryFilter();
        filter.setProtocols(Collections.<String>emptyList());
        assertThrows(IllegalArgumentException.class, () -> RadModelValidator.validate(filter));
        
        AgentCatalogVersion catalog = newValidVersionCatalog("1.1.0", "stable");
        catalog.setLabels(Collections.<String>emptyList());
        assertThrows(IllegalArgumentException.class, () -> RadModelValidator.validate(catalog));
        
        AgentCatalogVersion missingProtocols = newValidVersionCatalog("1.1.0", "stable");
        missingProtocols.setProtocols(null);
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate(missingProtocols));
    }
    
    @Test
    void shouldRejectCatalogWhoseLatestVersionIsAbsent() {
        AgentCatalogEntry entry = newValidCatalogEntry();
        entry.setLatestVersion("2.0.0");
        
        assertThrows(IllegalArgumentException.class, () -> RadModelValidator.validate(entry));
    }
    
    @Test
    void shouldRejectCatalogThatIsNotSemverDescending() {
        AgentCatalogEntry entry = newValidCatalogEntry();
        Collections.reverse(entry.getVersions());
        
        assertThrows(IllegalArgumentException.class, () -> RadModelValidator.validate(entry));
    }
    
    @Test
    void shouldRejectRuntimeEndpointWithoutHealthy() {
        AgentDiscoveryResult result = newValidDiscoveryResult();
        result.getCallInterfaces().get(0).getEndpointSets().get(0).getEndpoints().get(0)
            .setHealthy(null);
        
        assertThrows(IllegalArgumentException.class, () -> RadModelValidator.validate(result));
    }
    
    @Test
    void shouldRejectDeclaredEndpointWithHealthy() {
        AgentDiscoveryResult result = newValidDiscoveryResult();
        result.getCallInterfaces().get(0).getEndpointSets().get(1).getEndpoints().get(0)
            .setHealthy(true);
        
        assertThrows(IllegalArgumentException.class, () -> RadModelValidator.validate(result));
    }
    
    @Test
    void shouldRejectRegistrationRangeThatExcludesRuntimeVersion() {
        AgentEndpointRegistrationBatch batch = newValidRegistrationBatch();
        batch.setVersionRange("[2.0.0,3.0.0)");
        
        assertThrows(IllegalArgumentException.class, () -> RadModelValidator.validate(batch));
    }
    
    @Test
    void shouldRejectDuplicateEndpointNaturalKey() {
        AgentEndpointRegistrationBatch batch = newValidRegistrationBatch();
        Endpoint duplicate = newEndpoint("https://RUNTIME.EXAMPLE.COM/another-path", null);
        batch.getEndpoints().add(duplicate);
        
        assertThrows(IllegalArgumentException.class, () -> RadModelValidator.validate(batch));
    }
    
    @Test
    void shouldRejectDeregistrationEndpointWithMetadata() {
        AgentEndpointDeregistrationBatch batch = newValidDeregistrationBatch();
        batch.getEndpoints().get(0).setMetadata(
            Collections.singletonMap("zone", "cn-hangzhou-a"));
        
        assertThrows(IllegalArgumentException.class, () -> RadModelValidator.validate(batch));
    }
    
    @Test
    void shouldRejectUnsortedOrDuplicateCatalogPageItems() {
        Page<AgentCatalogEntry> unsortedPage = newValidCatalogPage();
        Collections.reverse(unsortedPage.getPageItems());
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validateCatalogPage(unsortedPage));
        
        Page<AgentCatalogEntry> duplicatePage = newValidCatalogPage();
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
        
        AgentDiscoveryResult missingEffectiveValueResult = newValidDiscoveryResult();
        missingEffectiveValueResult.getCallInterfaces().get(0).getEndpointSets().get(0)
            .getEndpoints().get(0)
            .setPriority(null);
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate(missingEffectiveValueResult));
        
        assertDoesNotThrow(() -> RadModelValidator.validate(newValidRegistrationBatch()));
    }
    
    @Test
    void shouldValidateSearchPaginationAndCatalogPageMetadata() {
        AgentSearchRequest invalidPageNo = newValidSearchRequest();
        invalidPageNo.setPageNo(0);
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate(invalidPageNo));
        
        AgentSearchRequest invalidPageSize = newValidSearchRequest();
        invalidPageSize.setPageSize(101);
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate(invalidPageSize));
        
        Page<AgentCatalogEntry> invalidMetadata = newValidCatalogPage();
        invalidMetadata.setTotalCount(-1);
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validateCatalogPage(invalidMetadata));
        
        Page<AgentCatalogEntry> oversizedPage = newValidCatalogPage();
        oversizedPage.setPageItems(new ArrayList<AgentCatalogEntry>(
            Collections.nCopies(101, newValidCatalogEntry())));
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validateCatalogPage(oversizedPage));
    }
    
    @Test
    void shouldRejectDuplicateCatalogVersionsAndLabels() {
        AgentCatalogEntry duplicateVersion = newValidCatalogEntry();
        duplicateVersion.getVersions().get(1).setVersion("1.1.0");
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate(duplicateVersion));
        
        AgentCatalogEntry duplicateLabel = newValidCatalogEntry();
        duplicateLabel.getVersions().get(1).setLabels(Collections.singletonList("stable"));
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
        AgentDiscoveryCallInterface callInterface = result.getCallInterfaces().get(0);
        assertDoesNotThrow(() -> RadModelValidator.validate(endpointSet));
        assertDoesNotThrow(() -> RadModelValidator.validate(callInterface));
    }
    
    @Test
    void shouldUseExactRuntimeVersionWhenRangeIsAbsent() {
        AgentEndpointRegistrationBatch batch = newValidRegistrationBatch();
        batch.setVersionRange(null);
        
        assertDoesNotThrow(() -> RadModelValidator.validate(batch));
    }
    
    @Test
    void shouldRejectDuplicateDiscoveryProtocolsAndEndpointSources() {
        AgentDiscoveryResult duplicateProtocol = newValidDiscoveryResult();
        AgentDiscoveryCallInterface callInterface = duplicateProtocol.getCallInterfaces().get(0);
        duplicateProtocol.setCallInterfaces(Arrays.asList(callInterface, callInterface));
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate(duplicateProtocol));
        
        AgentDiscoveryCallInterface duplicateSource =
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
        Endpoint first = newEndpoint("https://a.example.com:443/a2a", true);
        Endpoint second = newEndpoint("https://b.example.com:443/a2a", true);
        sortedRuntimeSet.setEndpoints(Arrays.asList(first, second));
        assertDoesNotThrow(() -> RadModelValidator.validate(sortedResult));
        
        AgentDiscoveryResult unsortedResult = newValidDiscoveryResult();
        EndpointSet unsortedRuntimeSet = unsortedResult.getCallInterfaces().get(0)
            .getEndpointSets().get(0);
        Endpoint higherPriority = newEndpoint("https://a.example.com:443/a2a", true);
        higherPriority.setPriority(1);
        Endpoint lowerPriority = newEndpoint("https://b.example.com:443/a2a", true);
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
    void shouldAcceptAbsentOptionalCollectionsAndProvider() {
        AgentSearchRequest searchRequest = newValidSearchRequest();
        searchRequest.setTagsAll(null);
        searchRequest.setProtocolsAny(null);
        assertDoesNotThrow(() -> RadModelValidator.validate(searchRequest));
        
        AgentDiscoveryFilter filter = newValidDiscoveryRequest().getFilter();
        filter.setTransports(null);
        assertDoesNotThrow(() -> RadModelValidator.validate(filter));
        
        AgentCatalogEntry catalogEntry = newValidCatalogEntry();
        catalogEntry.setProvider(null);
        assertDoesNotThrow(() -> RadModelValidator.validate(catalogEntry));
    }
    
    @Test
    void shouldRejectInvalidCatalogTextAndUris() {
        AgentCatalogEntry relativeIcon = newValidCatalogEntry();
        relativeIcon.setIconUrl("icons/agent.png");
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate(relativeIcon));
        
        AgentCatalogEntry malformedIcon = newValidCatalogEntry();
        malformedIcon.setIconUrl("https://[");
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate(malformedIcon));
        
        AgentCatalogEntry oversizedDisplayName = newValidCatalogEntry();
        oversizedDisplayName.setDisplayName(repeat('a', 129));
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate(oversizedDisplayName));
        
        AgentCatalogEntry missingProviderName = newValidCatalogEntry();
        missingProviderName.getProvider().setName(null);
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate(missingProviderName));
        
        AgentCatalogEntry emptyProviderName = newValidCatalogEntry();
        emptyProviderName.getProvider().setName("");
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate(emptyProviderName));
        
        AgentSearchRequest nonAsciiSearch = newValidSearchRequest();
        nonAsciiSearch.setAgentNameContains("Demo\nAgent");
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate(nonAsciiSearch));
    }
    
    @Test
    void shouldRejectInvalidCollectionShapesAndNullModels() {
        Page<AgentCatalogEntry> missingItems = newValidCatalogPage();
        missingItems.setPageItems(null);
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validateCatalogPage(missingItems));
        
        AgentDiscoveryResult missingCallInterfaces = newValidDiscoveryResult();
        missingCallInterfaces.setCallInterfaces(null);
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate(missingCallInterfaces));
        
        AgentDiscoveryResult tooManyCallInterfaces = newValidDiscoveryResult();
        AgentDiscoveryCallInterface callInterface =
            tooManyCallInterfaces.getCallInterfaces().get(0);
        tooManyCallInterfaces.setCallInterfaces(new ArrayList<AgentDiscoveryCallInterface>(
            Collections.nCopies(17, callInterface)));
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate(tooManyCallInterfaces));
        
        AgentEndpointRegistrationBatch tooManyEndpoints = newValidRegistrationBatch();
        Endpoint endpoint = tooManyEndpoints.getEndpoints().get(0);
        tooManyEndpoints.setEndpoints(new ArrayList<Endpoint>(
            Collections.nCopies(1001, endpoint)));
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate(tooManyEndpoints));
        
        AgentSearchRequest duplicateTags = newValidSearchRequest();
        duplicateTags.setTagsAll(Arrays.asList("demo", "demo"));
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate(duplicateTags));
        
        AgentSearchRequest duplicateProtocols = newValidSearchRequest();
        duplicateProtocols.setProtocolsAny(Arrays.asList("a2a", "a2a"));
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate(duplicateProtocols));
        
        assertThrows(IllegalArgumentException.class,
            () -> RadModelValidator.validate((AgentSearchRequest) null));
    }
    
    private AgentSearchRequest newValidSearchRequest() {
        AgentSearchRequest request = new AgentSearchRequest();
        request.setNamespaceId("public");
        request.setAgentNameContains("Agent");
        request.setTagsAll(Arrays.asList("assistant", "demo"));
        request.setProtocolsAny(Collections.singletonList("a2a"));
        request.setPageNo(1);
        request.setPageSize(20);
        return request;
    }
    
    private AgentCatalogEntry newValidCatalogEntry() {
        AgentProvider provider = new AgentProvider();
        provider.setName("Nacos");
        provider.setUrl("https://nacos.io");
        
        AgentCatalogEntry entry = new AgentCatalogEntry();
        entry.setAgentName("Demo Agent");
        entry.setDisplayName("Demo Agent 展示名");
        entry.setDescription("A complete RAD catalog entry.");
        entry.setIconUrl("https://example.com/icon.png");
        entry.setProvider(provider);
        entry.setTags(Arrays.asList("assistant", "demo"));
        entry.setLatestVersion("1.1.0");
        entry.setVersions(new ArrayList<AgentCatalogVersion>(Arrays.asList(
            newValidVersionCatalog("1.1.0", "stable"),
            newValidVersionCatalog("1.0.0", "legacy"))));
        return entry;
    }
    
    private AgentCatalogVersion newValidVersionCatalog(String version, String label) {
        AgentCatalogVersion catalog = new AgentCatalogVersion();
        catalog.setVersion(version);
        catalog.setLabels(Collections.singletonList(label));
        catalog.setProtocols(Collections.singletonList("a2a"));
        return catalog;
    }
    
    private Page<AgentCatalogEntry> newValidCatalogPage() {
        AgentCatalogEntry first = newValidCatalogEntry();
        first.setAgentName("Alpha Agent");
        AgentCatalogEntry second = newValidCatalogEntry();
        second.setAgentName("Demo Agent");
        Page<AgentCatalogEntry> page = new Page<AgentCatalogEntry>();
        page.setTotalCount(2);
        page.setPageNumber(1);
        page.setPagesAvailable(1);
        page.setPageItems(new ArrayList<AgentCatalogEntry>(Arrays.asList(first, second)));
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
            newEndpoint("https://runtime.example.com:443/a2a", true)));
        
        EndpointSet declaredSet = new EndpointSet();
        declaredSet.setSource(EndpointSource.DECLARED);
        declaredSet.setSourceRevision(CONTENT_DIGEST);
        declaredSet.setEndpoints(Collections.singletonList(
            newEndpoint("https://declared.example.com:443/a2a", null)));
        
        AgentDiscoveryCallInterface callInterface = new AgentDiscoveryCallInterface();
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
        batch.setNamespaceId("public");
        batch.setAgentName("Demo Agent");
        batch.setRuntimeVersion("1.0.6");
        batch.setVersionRange("[1.0.0,2.0.0)");
        batch.setProtocol("a2a");
        batch.setEndpoints(new ArrayList<Endpoint>(Collections.singletonList(
            newEndpoint("https://runtime.example.com/a2a", null))));
        return batch;
    }
    
    private AgentEndpointDeregistrationBatch newValidDeregistrationBatch() {
        Endpoint key = new Endpoint();
        key.setUri("https://runtime.example.com/a2a");
        key.setTransport("JSON-RPC");
        
        AgentEndpointDeregistrationBatch batch = new AgentEndpointDeregistrationBatch();
        batch.setNamespaceId("public");
        batch.setAgentName("Demo Agent");
        batch.setProtocol("a2a");
        batch.setEndpoints(Collections.singletonList(key));
        return batch;
    }
    
    private Endpoint newEndpoint(String uri, Boolean healthy) {
        Endpoint endpoint = new Endpoint();
        endpoint.setUri(uri);
        endpoint.setTransport("JSON-RPC");
        endpoint.setPriority(0);
        endpoint.setWeight(1.0D);
        endpoint.setMetadata(Collections.singletonMap("zone", "cn-hangzhou-a"));
        endpoint.setHealthy(healthy);
        return endpoint;
    }
    
    private String repeat(char value, int count) {
        char[] result = new char[count];
        Arrays.fill(result, value);
        return new String(result);
    }
}
