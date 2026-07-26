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

package com.alibaba.nacos.ai.importer.manager;

import com.alibaba.nacos.ai.importer.config.AiResourceImportProperties;
import com.alibaba.nacos.ai.importer.config.AiResourceImportSourceConfig;
import com.alibaba.nacos.ai.importer.operator.AiResourceOperator;
import com.alibaba.nacos.ai.importer.operator.AiResourceOperatorRegistry;
import com.alibaba.nacos.ai.importer.security.AiResourceImportSecurityGuard;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportExecuteRequest;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportExecuteResponse;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportItem;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportResultItem;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportResultStatus;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportSearchRequest;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportSearchResponse;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportSourceInfo;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportValidateRequest;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportValidateResponse;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportValidationItem;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportValidationStatus;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportArtifact;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportCandidate;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportCandidatePage;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportContext;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportPayloadKind;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportSource;
import com.alibaba.nacos.plugin.ai.importer.spi.AiResourceImportService;
import com.alibaba.nacos.plugin.ai.importer.spi.AiResourceImportServiceBuilder;
import com.alibaba.nacos.plugin.ai.importer.spi.AiResourceImportSourceProvider;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiResourceImportManagerTest {
    
    @Test
    void testListSourcesFiltersResourceTypeAndMasksEndpoint() throws NacosException {
        AiResourceImportSourceManager sourceManager = newSourceManager(enabledProperties());
        
        List<AiResourceImportSourceInfo> result = sourceManager.listSourceInfos("mcp");
        
        assertEquals(1, result.size());
        assertEquals("source-1", result.get(0).getSourceId());
        assertEquals("Fake Source", result.get(0).getDisplayName());
        assertEquals("fake-importer", result.get(0).getPluginName());
        assertEquals(Collections.singletonList("mcp"), result.get(0).getResourceTypes());
        assertEquals(Arrays.asList("search", "validate", "execute"),
            result.get(0).getCapabilities());
        try (
            AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(AiResourceImportPluginManager.class,
                AiResourceImportSourceManager.class,
                AiResourceOperatorRegistry.class, AiResourceImportSecurityGuard.class,
                AiResourceImportManager.class);
            context.refresh();
            assertNotNull(context.getBean(AiResourceImportManager.class));
        }
    }
    
    @Test
    void testSearchRoutesToResolvedImporter() throws NacosException {
        FakeImportServiceBuilder builder = new FakeImportServiceBuilder();
        AiResourceImportManager manager = newManager(enabledProperties(), builder,
            Collections.singletonList(new FakeOperator()));
        AiResourceImportSearchRequest request = new AiResourceImportSearchRequest();
        request.setResourceType("mcp");
        request.setSourceId("source-1");
        request.setQuery("database");
        request.setLimit(50);
        
        AiResourceImportSearchResponse response = manager.search(request);
        
        assertEquals("source-1", response.getSourceId());
        assertEquals("mcp", response.getResourceType());
        assertEquals(1, response.getItems().size());
        assertEquals("server-1", response.getItems().get(0).getExternalId());
        assertEquals(10, builder.service.lastContext.getLimit());
        assertEquals("database", builder.service.lastContext.getQuery());
    }
    
    @Test
    void testSearchRejectsHttpEndpointByDefault() {
        AiResourceImportProperties properties = enabledProperties();
        properties.getSources().get(0).setEndpoint("http://example.com/registry");
        AiResourceImportManager manager = newManager(properties, new FakeImportServiceBuilder(),
            Collections.singletonList(new FakeOperator()));
        AiResourceImportSearchRequest request = searchRequest();
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> manager.search(request));
        
        assertTrue(exception.getErrMsg().contains("must use https"));
    }
    
    @Test
    void testSearchAllowsHttpEndpointWhenConfigured() throws NacosException {
        AiResourceImportProperties properties = enabledProperties();
        properties.getSources().get(0).setEndpoint("http://example.com/registry");
        properties.getSources().get(0).setProperties(
            Collections.singletonMap("allow-http", "true"));
        AiResourceImportManager manager = newManager(properties, new FakeImportServiceBuilder(),
            Collections.singletonList(new FakeOperator()));
        
        AiResourceImportSearchResponse response = manager.search(searchRequest());
        
        assertEquals(1, response.getItems().size());
    }
    
    @Test
    void testSearchRejectsPrivateEndpointByDefault() {
        AiResourceImportProperties properties = enabledProperties();
        properties.getSources().get(0).setEndpoint("https://127.0.0.1/registry");
        AiResourceImportManager manager = newManager(properties, new FakeImportServiceBuilder(),
            Collections.singletonList(new FakeOperator()));
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> manager.search(searchRequest()));
        
        assertTrue(exception.getErrMsg().contains("private or local target"));
    }
    
    @Test
    void testSearchAllowsPrivateEndpointWhenConfigured() throws NacosException {
        AiResourceImportProperties properties = enabledProperties();
        properties.getSources().get(0).setEndpoint("http://127.0.0.1/registry");
        Map<String, String> sourceProperties = new HashMap<>(2);
        sourceProperties.put("allow-http", "true");
        sourceProperties.put("allow-private-network", "true");
        properties.getSources().get(0).setProperties(sourceProperties);
        AiResourceImportManager manager = newManager(properties, new FakeImportServiceBuilder(),
            Collections.singletonList(new FakeOperator()));
        
        AiResourceImportSearchResponse response = manager.search(searchRequest());
        
        assertEquals(1, response.getItems().size());
    }
    
    @Test
    void testValidateFetchesArtifactAndUsesOperator() throws NacosException {
        AiResourceImportManager manager = newManager(enabledProperties(),
            new FakeImportServiceBuilder(), Collections.singletonList(new FakeOperator()));
        AiResourceImportValidateRequest request = new AiResourceImportValidateRequest();
        request.setResourceType("mcp");
        request.setSourceId("source-1");
        request.setSelectedItems(Collections.singletonList(selectedItem("server-1")));
        
        AiResourceImportValidateResponse response = manager.validate(request);
        
        assertEquals("source-1", response.getSourceId());
        assertEquals(1, response.getItems().size());
        assertEquals(AiResourceImportValidationStatus.VALID,
            response.getItems().get(0).getStatus());
        assertEquals("server-1", response.getItems().get(0).getExternalId());
    }
    
    @Test
    void testValidateReturnsInvalidItemWhenOperatorMissing() throws NacosException {
        AiResourceImportManager manager = newManager(enabledProperties(),
            new FakeImportServiceBuilder(), Collections.emptyList());
        AiResourceImportValidateRequest request = new AiResourceImportValidateRequest();
        request.setResourceType("mcp");
        request.setSourceId("source-1");
        request.setSelectedItems(Collections.singletonList(selectedItem("server-1")));
        
        AiResourceImportValidateResponse response = manager.validate(request);
        
        assertEquals(AiResourceImportValidationStatus.INVALID,
            response.getItems().get(0).getStatus());
        assertTrue(response.getItems().get(0).getErrors().get(0)
            .contains("AI resource import operator not found"));
    }
    
    @Test
    void testExecuteCountsSkippedWhenSkipInvalid() throws NacosException {
        AiResourceImportManager manager = newManager(enabledProperties(),
            new FakeImportServiceBuilder(), Collections.singletonList(new FakeOperator()));
        AiResourceImportExecuteRequest request = new AiResourceImportExecuteRequest();
        request.setResourceType("mcp");
        request.setSourceId("source-1");
        request.setSelectedItems(Collections.singletonList(selectedItem("bad")));
        request.setSkipInvalid(true);
        
        AiResourceImportExecuteResponse response = manager.execute(request);
        
        assertTrue(response.isSuccess());
        assertEquals(1, response.getTotalCount());
        assertEquals(0, response.getSuccessCount());
        assertEquals(1, response.getSkippedCount());
        assertEquals(AiResourceImportResultStatus.SKIPPED,
            response.getResults().get(0).getStatus());
    }
    
    @Test
    void testDisabledSourceManagerRejectsResolve() {
        AiResourceImportProperties properties = enabledProperties();
        properties.setEnabled(false);
        AiResourceImportSourceManager sourceManager = newSourceManager(properties);
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> sourceManager.resolveSource("source-1", "mcp"));
        
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, exception.getErrCode());
    }
    
    @Test
    void testProviderSourceWorksWhenExplicitImportDisabled() throws NacosException {
        AiResourceImportProperties properties = enabledProperties();
        properties.setEnabled(false);
        AiResourceImportSourceManager sourceManager = newProviderSourceManager(properties);
        
        List<AiResourceImportSourceInfo> result = sourceManager.listSourceInfos("mcp");
        
        assertEquals(1, result.size());
        assertEquals("provider-source", result.get(0).getSourceId());
        assertEquals("Provider Source", result.get(0).getDisplayName());
        assertEquals("provider source", result.get(0).getDescription());
        assertEquals("fake-importer", result.get(0).getPluginName());
        assertEquals(Collections.singletonList("mcp"), result.get(0).getResourceTypes());
    }
    
    @Test
    void testDuplicateSourceIdRejected() {
        AiResourceImportProperties properties = enabledProperties();
        properties.setSources(Arrays.asList(sourceConfig(), sourceConfig()));
        AiResourceImportSourceManager sourceManager = newSourceManager(properties);
        
        NacosApiException exception = assertThrows(NacosApiException.class,
            () -> sourceManager.listSourceInfos(null));
        
        assertTrue(exception.getErrMsg().contains("Duplicate AI resource import source id"));
    }
    
    @Test
    void testDuplicateImporterRejected() {
        FakeImportServiceBuilder builder = new FakeImportServiceBuilder();
        
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> new AiResourceImportPluginManager(Arrays.asList(builder, builder)));
        
        assertTrue(exception.getMessage().contains("Duplicate AI resource importer type"));
    }
    
    @Test
    void testDuplicateOperatorRejected() {
        FakeOperator operator = new FakeOperator();
        
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> new AiResourceOperatorRegistry(Arrays.asList(operator, operator)));
        
        assertTrue(exception.getMessage().contains("Duplicate AI resource import operator type"));
    }
    
    @Test
    void testArtifactSizeLimitReturnsInvalidValidation() throws NacosException {
        AiResourceImportProperties properties = enabledProperties();
        properties.getSources().get(0).setMaxArtifactSize(1);
        AiResourceImportManager manager = newManager(properties, new FakeImportServiceBuilder(),
            Collections.singletonList(new FakeOperator()));
        AiResourceImportValidateRequest request = new AiResourceImportValidateRequest();
        request.setResourceType("mcp");
        request.setSourceId("source-1");
        request.setSelectedItems(Collections.singletonList(selectedItem("server-1")));
        
        AiResourceImportValidateResponse response = manager.validate(request);
        
        assertEquals(AiResourceImportValidationStatus.INVALID,
            response.getItems().get(0).getStatus());
        assertTrue(response.getItems().get(0).getErrors().get(0).contains("size exceeds"));
    }
    
    private AiResourceImportManager newManager(AiResourceImportProperties properties,
        FakeImportServiceBuilder builder, List<AiResourceOperator> operators) {
        AiResourceImportPluginManager pluginManager =
            new AiResourceImportPluginManager(Collections.singletonList(builder));
        AiResourceImportSourceManager sourceManager =
            new AiResourceImportSourceManager(pluginManager);
        ReflectionTestUtils.setField(sourceManager, "propertiesSupplier",
            (Supplier<AiResourceImportProperties>) () -> properties);
        return new AiResourceImportManager(sourceManager, pluginManager,
            new AiResourceOperatorRegistry(operators), new AiResourceImportSecurityGuard());
    }
    
    private AiResourceImportSourceManager newSourceManager(AiResourceImportProperties properties) {
        AiResourceImportPluginManager pluginManager =
            new AiResourceImportPluginManager(Collections.singletonList(
                new FakeImportServiceBuilder()));
        AiResourceImportSourceManager result =
            new AiResourceImportSourceManager(pluginManager);
        ReflectionTestUtils.setField(result, "propertiesSupplier",
            (Supplier<AiResourceImportProperties>) () -> properties);
        return result;
    }
    
    private AiResourceImportSourceManager newProviderSourceManager(
        AiResourceImportProperties properties) {
        AiResourceImportSourceManager result = newSourceManager(properties);
        ReflectionTestUtils.setField(result, "rawPropertiesSupplier",
            (Supplier<Properties>) Properties::new);
        ReflectionTestUtils.setField(result, "sourceProvidersSupplier",
            (Supplier<List<AiResourceImportSourceProvider>>) () -> Collections.singletonList(
                new FakeSourceProvider()));
        return result;
    }
    
    private AiResourceImportProperties enabledProperties() {
        AiResourceImportProperties properties = new AiResourceImportProperties();
        properties.setEnabled(true);
        properties.setSources(Collections.singletonList(sourceConfig()));
        return properties;
    }
    
    private AiResourceImportSourceConfig sourceConfig() {
        AiResourceImportSourceConfig source = new AiResourceImportSourceConfig();
        source.setSourceId("source-1");
        source.setDisplayName("Fake Source");
        source.setPluginName("fake-importer");
        source.setResourceTypes(Collections.singletonList("mcp"));
        source.setEndpoint("https://example.com/registry");
        source.setEnabled(true);
        source.setMaxItemCount(10);
        source.setMaxArtifactSize(1024);
        return source;
    }
    
    private AiResourceImportSearchRequest searchRequest() {
        AiResourceImportSearchRequest request = new AiResourceImportSearchRequest();
        request.setResourceType("mcp");
        request.setSourceId("source-1");
        request.setQuery("database");
        request.setLimit(50);
        return request;
    }
    
    private AiResourceImportSource providerSource() {
        AiResourceImportSource source = new AiResourceImportSource();
        source.setSourceId("provider-source");
        source.setDisplayName("Provider Source");
        source.setDescription("provider source");
        source.setPluginName("fake-importer");
        source.setResourceTypes(Collections.singletonList("mcp"));
        source.setEndpoint("https://example.com/provider");
        source.setEnabled(true);
        source.setMaxItemCount(10);
        source.setMaxArtifactSize(1024);
        return source;
    }
    
    private AiResourceImportItem selectedItem(String externalId) {
        AiResourceImportItem item = new AiResourceImportItem();
        item.setExternalId(externalId);
        item.setName("server");
        item.setVersion("1.0.0");
        return item;
    }
    
    private static class FakeImportServiceBuilder implements AiResourceImportServiceBuilder {
        
        private final FakeImportService service = new FakeImportService();
        
        @Override
        public String importerType() {
            return "fake-importer";
        }
        
        @Override
        public AiResourceImportService build(Properties properties) {
            return service;
        }
    }
    
    private class FakeSourceProvider implements AiResourceImportSourceProvider {
        
        @Override
        public List<AiResourceImportSource> loadSources(Properties properties) {
            return Collections.singletonList(providerSource());
        }
    }
    
    private static class FakeImportService implements AiResourceImportService {
        
        private AiResourceImportContext lastContext;
        
        @Override
        public String importerType() {
            return "fake-importer";
        }
        
        @Override
        public Set<String> supportedResourceTypes() {
            return Collections.singleton("mcp");
        }
        
        @Override
        public AiResourceImportCandidatePage search(AiResourceImportContext context) {
            lastContext = context;
            AiResourceImportCandidate candidate = new AiResourceImportCandidate();
            candidate.setResourceType("mcp");
            candidate.setExternalId("server-1");
            candidate.setName("server");
            candidate.setVersion("1.0.0");
            candidate.setDescription("fake server");
            AiResourceImportCandidatePage page = new AiResourceImportCandidatePage();
            page.setItems(Collections.singletonList(candidate));
            page.setHasMore(false);
            return page;
        }
        
        @Override
        public AiResourceImportArtifact fetch(AiResourceImportContext context,
            com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportItem item)
            throws NacosException {
            if ("bad".equals(item.getExternalId())) {
                throw new NacosException(NacosException.SERVER_ERROR, "fetch failed");
            }
            AiResourceImportArtifact artifact = new AiResourceImportArtifact();
            artifact.setResourceType(context.getResourceType());
            artifact.setExternalId(item.getExternalId());
            artifact.setName(item.getName());
            artifact.setVersion(item.getVersion());
            artifact.setPayloadKind(AiResourceImportPayloadKind.JSON);
            artifact.setPayloadJson("{}");
            return artifact;
        }
    }
    
    private static class FakeOperator implements AiResourceOperator {
        
        @Override
        public String resourceType() {
            return "mcp";
        }
        
        @Override
        public AiResourceImportValidationItem validate(String namespaceId,
            AiResourceImportArtifact artifact, boolean overwriteExisting) {
            AiResourceImportValidationItem result = new AiResourceImportValidationItem();
            result.setExternalId(artifact.getExternalId());
            result.setName(artifact.getName());
            result.setVersion(artifact.getVersion());
            result.setStatus(AiResourceImportValidationStatus.VALID);
            return result;
        }
        
        @Override
        public AiResourceImportResultItem importResource(String namespaceId,
            AiResourceImportArtifact artifact, boolean overwriteExisting) {
            AiResourceImportResultItem result = new AiResourceImportResultItem();
            result.setExternalId(artifact.getExternalId());
            result.setResourceName(artifact.getName());
            result.setVersion(artifact.getVersion());
            result.setStatus(AiResourceImportResultStatus.SUCCESS);
            return result;
        }
    }
}
