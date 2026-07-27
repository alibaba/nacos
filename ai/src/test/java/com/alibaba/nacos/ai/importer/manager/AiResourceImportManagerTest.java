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
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportValidateRequest;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportValidateResponse;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportValidationItem;
import com.alibaba.nacos.api.ai.model.importer.AiResourceImportValidationStatus;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.plugin.PluginStateCheckerHolder;
import com.alibaba.nacos.plugin.ai.importer.AiResourceImportConstants;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportArtifact;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportCandidate;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportCandidatePage;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportContext;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportPayloadKind;
import com.alibaba.nacos.plugin.ai.importer.spi.AiResourceImportService;
import com.alibaba.nacos.plugin.ai.importer.spi.AiResourceImportServiceBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiResourceImportManagerTest {
    
    @AfterEach
    void tearDown() {
        PluginStateCheckerHolder.setInstance(null);
    }
    
    @Test
    void testListSourcesDelegatesToManagedPluginMetadata() throws Exception {
        FakeImportServiceBuilder builder = new FakeImportServiceBuilder();
        AiResourceImportManager manager = newManager(builder,
            Collections.singletonList(new FakeOperator(false)));
        
        assertEquals("source-1", manager.listSources("mcp").get(0).getSourceId());
        assertTrue(manager.listSources("skill").isEmpty());
    }
    
    @Test
    void testSearchBuildsClosesAndUsesBuilderLimits() throws Exception {
        FakeImportServiceBuilder builder = new FakeImportServiceBuilder();
        builder.currentConfig.put(AiResourceImportConstants.CONFIG_MAX_ITEM_COUNT, "10");
        AiResourceImportManager manager = newManager(builder,
            Collections.singletonList(new FakeOperator(false)));
        AiResourceImportSearchRequest request = searchRequest();
        request.setLimit(50);
        request.setCursor("cursor");
        request.setOptions(Collections.singletonMap("option", "value"));
        
        AiResourceImportSearchResponse response = manager.search(request);
        
        assertEquals("source-1", response.getSourceId());
        assertEquals("mcp", response.getResourceType());
        assertEquals(1, response.getItems().size());
        assertEquals("server-1", response.getItems().get(0).getExternalId());
        assertFalse(response.isHasMore());
        assertEquals("next", response.getNextCursor());
        FakeImportService service = builder.lastService;
        assertEquals(1, builder.buildCount);
        assertTrue(service.closed);
        assertEquals(10, service.lastContext.getLimit());
        assertEquals("database", service.lastContext.getQuery());
        assertEquals("cursor", service.lastContext.getCursor());
        assertEquals("value", service.lastContext.getOptions().get("option"));
        assertEquals("public", service.lastContext.getNamespaceId());
    }
    
    @Test
    void testSearchDefaultsLimitAndHandlesNullPage() throws Exception {
        FakeImportServiceBuilder builder = new FakeImportServiceBuilder();
        builder.currentConfig.clear();
        builder.nullSearchPage = true;
        AiResourceImportManager manager = newManager(builder, Collections.emptyList());
        AiResourceImportSearchRequest request = searchRequest();
        request.setLimit(null);
        request.setNamespaceId("custom");
        
        AiResourceImportSearchResponse response = manager.search(request);
        
        assertTrue(response.getItems().isEmpty());
        assertEquals(500, builder.lastService.lastContext.getLimit());
        assertEquals("custom", builder.lastService.lastContext.getNamespaceId());
        assertTrue(builder.lastService.closed);
    }
    
    @Test
    void testSearchFailuresStillCloseRequestScopedService() {
        FakeImportServiceBuilder builder = new FakeImportServiceBuilder();
        builder.searchFailure = true;
        AiResourceImportManager manager = newManager(builder, Collections.emptyList());
        
        assertThrows(NacosException.class, () -> manager.search(searchRequest()));
        assertTrue(builder.lastService.closed);
        
        builder.searchFailure = false;
        builder.closeFailure = true;
        assertNotNull(assertDoesNotThrowSearch(manager));
        assertTrue(builder.lastService.closed);
        
        builder.returnNullService = true;
        NacosApiException nullService = assertThrows(NacosApiException.class,
            () -> manager.search(searchRequest()));
        assertEquals(NacosException.SERVER_ERROR, nullService.getErrCode());
    }
    
    @Test
    void testOperationRoutingFailures() {
        AiResourceImportManager manager =
            newManager(new FakeImportServiceBuilder(), Collections.emptyList());
        AiResourceImportSearchRequest searchRequest = searchRequest();
        searchRequest.setSourceId("missing");
        AiResourceImportValidateRequest validateRequest = validateRequest("server-1");
        validateRequest.setSourceId("missing");
        AiResourceImportExecuteRequest executeRequest = executeRequest("server-1");
        executeRequest.setSourceId("missing");
        
        assertThrows(NacosException.class, () -> manager.search(searchRequest));
        assertThrows(NacosException.class, () -> manager.validate(validateRequest));
        assertThrows(NacosException.class, () -> manager.execute(executeRequest));
    }
    
    @Test
    void testValidateUsesSingleServiceAndOperator() throws Exception {
        FakeImportServiceBuilder builder = new FakeImportServiceBuilder();
        FakeOperator operator = new FakeOperator(false);
        AiResourceImportManager manager =
            newManager(builder, Collections.singletonList(operator));
        AiResourceImportValidateRequest request = validateRequest("server-1");
        request.setOverwriteExisting(true);
        
        AiResourceImportValidateResponse response = manager.validate(request);
        
        assertEquals("source-1", response.getSourceId());
        assertEquals(AiResourceImportValidationStatus.VALID,
            response.getItems().get(0).getStatus());
        assertTrue(operator.lastOverwriteExisting);
        assertTrue(builder.lastService.closed);
        assertEquals(1, builder.buildCount);
    }
    
    @Test
    void testValidateDefaultsAndConvertsFailuresToInvalidItems() throws Exception {
        FakeImportServiceBuilder builder = new FakeImportServiceBuilder();
        AiResourceImportManager defaultManager =
            newManager(builder, Collections.singletonList(new FakeOperator(true)));
        
        AiResourceImportValidateResponse defaultResponse =
            defaultManager.validate(validateRequest("server-1"));
        assertEquals(AiResourceImportValidationStatus.VALID,
            defaultResponse.getItems().get(0).getStatus());
        
        AiResourceImportValidateResponse fetchFailure =
            defaultManager.validate(validateRequest("bad"));
        assertEquals(AiResourceImportValidationStatus.INVALID,
            fetchFailure.getItems().get(0).getStatus());
        
        AiResourceImportManager noOperator = newManager(new FakeImportServiceBuilder(),
            Collections.emptyList());
        AiResourceImportValidateResponse missingOperator =
            noOperator.validate(validateRequest("server-1"));
        assertTrue(missingOperator.getItems().get(0).getErrors().get(0)
            .contains("operator not found"));
        
        FakeImportServiceBuilder smallLimit = new FakeImportServiceBuilder();
        smallLimit.currentConfig.put(AiResourceImportConstants.CONFIG_MAX_ARTIFACT_SIZE, "1");
        AiResourceImportManager guarded = newManager(smallLimit,
            Collections.singletonList(new FakeOperator(false)));
        AiResourceImportValidateResponse oversized =
            guarded.validate(validateRequest("large"));
        assertTrue(oversized.getItems().get(0).getErrors().get(0).contains("size exceeds"));
    }
    
    @Test
    void testExecuteReturnsDefaultSuccessAndCountsFailures() throws Exception {
        FakeImportServiceBuilder builder = new FakeImportServiceBuilder();
        AiResourceImportManager manager =
            newManager(builder, Collections.singletonList(new FakeOperator(true)));
        AiResourceImportExecuteRequest successRequest = executeRequest("server-1");
        
        AiResourceImportExecuteResponse success = manager.execute(successRequest);
        
        assertTrue(success.isSuccess());
        assertEquals(1, success.getSuccessCount());
        assertEquals(AiResourceImportResultStatus.SUCCESS,
            success.getResults().get(0).getStatus());
        assertTrue(builder.lastService.closed);
        
        AiResourceImportExecuteRequest failedRequest = executeRequest("bad");
        AiResourceImportExecuteResponse failed = manager.execute(failedRequest);
        assertFalse(failed.isSuccess());
        assertEquals(1, failed.getFailedCount());
        assertEquals(AiResourceImportResultStatus.FAILED,
            failed.getResults().get(0).getStatus());
        
        failedRequest.setSkipInvalid(true);
        AiResourceImportExecuteResponse skipped = manager.execute(failedRequest);
        assertTrue(skipped.isSuccess());
        assertEquals(1, skipped.getSkippedCount());
        assertEquals(AiResourceImportResultStatus.SKIPPED,
            skipped.getResults().get(0).getStatus());
    }
    
    @Test
    void testExecuteUsesOperatorResultAndRequestFlags() throws Exception {
        FakeImportServiceBuilder builder = new FakeImportServiceBuilder();
        FakeOperator operator = new FakeOperator(false);
        AiResourceImportManager manager =
            newManager(builder, Collections.singletonList(operator));
        AiResourceImportExecuteRequest request = executeRequest("server-1");
        request.setOverwriteExisting(true);
        
        AiResourceImportExecuteResponse response = manager.execute(request);
        
        assertEquals("imported", response.getResults().get(0).getResourceName());
        assertTrue(operator.lastOverwriteExisting);
    }
    
    @Test
    void testRequestValidation() {
        AiResourceImportManager manager =
            newManager(new FakeImportServiceBuilder(), Collections.emptyList());
        
        assertThrows(NacosException.class, () -> manager.search(null));
        AiResourceImportSearchRequest search = new AiResourceImportSearchRequest();
        assertThrows(NacosException.class, () -> manager.search(search));
        search.setResourceType("mcp");
        assertThrows(NacosException.class, () -> manager.search(search));
        
        assertThrows(NacosException.class, () -> manager.validate(null));
        AiResourceImportValidateRequest validate = new AiResourceImportValidateRequest();
        assertThrows(NacosException.class, () -> manager.validate(validate));
        validate.setResourceType("mcp");
        assertThrows(NacosException.class, () -> manager.validate(validate));
        validate.setSourceId("source-1");
        assertThrows(NacosException.class, () -> manager.validate(validate));
        
        assertThrows(NacosException.class, () -> manager.execute(null));
        AiResourceImportExecuteRequest execute = new AiResourceImportExecuteRequest();
        assertThrows(NacosException.class, () -> manager.execute(execute));
        execute.setResourceType("mcp");
        assertThrows(NacosException.class, () -> manager.execute(execute));
        execute.setSourceId("source-1");
        assertThrows(NacosException.class, () -> manager.execute(execute));
    }
    
    @Test
    void testDuplicateAndBlankOperatorsRejected() {
        FakeOperator operator = new FakeOperator(false);
        assertThrows(IllegalStateException.class,
            () -> new AiResourceOperatorRegistry(Arrays.asList(operator, operator)));
        assertThrows(IllegalStateException.class,
            () -> new AiResourceOperatorRegistry(Collections.singletonList(
                new FakeOperator(false) {
                    
                    @Override
                    public String resourceType() {
                        return " ";
                    }
                })));
    }
    
    private AiResourceImportSearchResponse assertDoesNotThrowSearch(
        AiResourceImportManager manager) {
        try {
            return manager.search(searchRequest());
        } catch (NacosException e) {
            throw new AssertionError(e);
        }
    }
    
    private AiResourceImportManager newManager(FakeImportServiceBuilder builder,
        List<AiResourceOperator> operators) {
        AiResourceImportPluginManager pluginManager =
            new AiResourceImportPluginManager(() -> Collections.singletonList(builder));
        pluginManager.loadPlugins();
        AiResourceImportProperties properties = new AiResourceImportProperties();
        properties.setEnabled(true);
        ReflectionTestUtils.setField(pluginManager, "propertiesSupplier",
            (Supplier<AiResourceImportProperties>) () -> properties);
        PluginStateCheckerHolder.setInstance((type, name) -> true);
        return new AiResourceImportManager(pluginManager,
            new AiResourceOperatorRegistry(operators), new AiResourceImportSecurityGuard());
    }
    
    private AiResourceImportSearchRequest searchRequest() {
        AiResourceImportSearchRequest request = new AiResourceImportSearchRequest();
        request.setResourceType("mcp");
        request.setSourceId("source-1");
        request.setQuery("database");
        return request;
    }
    
    private AiResourceImportValidateRequest validateRequest(String externalId) {
        AiResourceImportValidateRequest request = new AiResourceImportValidateRequest();
        request.setResourceType("mcp");
        request.setSourceId("source-1");
        request.setSelectedItems(Collections.singletonList(selectedItem(externalId)));
        return request;
    }
    
    private AiResourceImportExecuteRequest executeRequest(String externalId) {
        AiResourceImportExecuteRequest request = new AiResourceImportExecuteRequest();
        request.setResourceType("mcp");
        request.setSourceId("source-1");
        request.setSelectedItems(Collections.singletonList(selectedItem(externalId)));
        return request;
    }
    
    private AiResourceImportItem selectedItem(String externalId) {
        AiResourceImportItem item = new AiResourceImportItem();
        item.setExternalId(externalId);
        item.setName("server");
        item.setVersion("1.0.0");
        item.setMetadata(Collections.singletonMap("key", "value"));
        return item;
    }
    
    private static class FakeImportServiceBuilder implements AiResourceImportServiceBuilder {
        
        private final Map<String, String> currentConfig = new HashMap<>();
        
        private int buildCount;
        
        private FakeImportService lastService;
        
        private boolean nullSearchPage;
        
        private boolean searchFailure;
        
        private boolean closeFailure;
        
        private boolean returnNullService;
        
        FakeImportServiceBuilder() {
            currentConfig.put(AiResourceImportConstants.CONFIG_MAX_ITEM_COUNT, "10");
            currentConfig.put(AiResourceImportConstants.CONFIG_MAX_ARTIFACT_SIZE, "1024");
        }
        
        @Override
        public String pluginName() {
            return "source-1";
        }
        
        @Override
        public String importerType() {
            return "fake-importer";
        }
        
        @Override
        public String displayName() {
            return "Fake source";
        }
        
        @Override
        public String description() {
            return "Fake source description";
        }
        
        @Override
        public Set<String> supportedResourceTypes() {
            return Collections.singleton("mcp");
        }
        
        @Override
        public Map<String, String> getCurrentConfig() {
            return new HashMap<>(currentConfig);
        }
        
        @Override
        public AiResourceImportService build() {
            buildCount++;
            if (returnNullService) {
                return null;
            }
            lastService = new FakeImportService();
            lastService.nullSearchPage = nullSearchPage;
            lastService.searchFailure = searchFailure;
            lastService.closeFailure = closeFailure;
            return lastService;
        }
    }
    
    private static class FakeImportService implements AiResourceImportService {
        
        private AiResourceImportContext lastContext;
        
        private boolean nullSearchPage;
        
        private boolean searchFailure;
        
        private boolean closeFailure;
        
        private boolean closed;
        
        @Override
        public AiResourceImportCandidatePage search(AiResourceImportContext context)
            throws NacosException {
            lastContext = context;
            if (searchFailure) {
                throw new NacosException(NacosException.SERVER_ERROR, "search failed");
            }
            if (nullSearchPage) {
                return null;
            }
            AiResourceImportCandidate candidate = new AiResourceImportCandidate();
            candidate.setResourceType("mcp");
            candidate.setExternalId("server-1");
            candidate.setName("server");
            candidate.setVersion("1.0.0");
            candidate.setDescription("fake server");
            candidate.setMetadata(Collections.singletonMap("key", "value"));
            AiResourceImportCandidatePage page = new AiResourceImportCandidatePage();
            page.setItems(Collections.singletonList(candidate));
            page.setNextCursor("next");
            page.setHasMore(false);
            return page;
        }
        
        @Override
        public AiResourceImportArtifact fetch(AiResourceImportContext context,
            com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportItem item)
            throws NacosException {
            lastContext = context;
            if ("bad".equals(item.getExternalId())) {
                throw new NacosException(NacosException.SERVER_ERROR, "fetch failed");
            }
            AiResourceImportArtifact artifact = new AiResourceImportArtifact();
            artifact.setResourceType(context.getResourceType());
            artifact.setExternalId(item.getExternalId());
            artifact.setName(item.getName());
            artifact.setVersion(item.getVersion());
            artifact.setPayloadKind(AiResourceImportPayloadKind.JSON);
            artifact.setPayloadJson("large".equals(item.getExternalId()) ? "too-large" : "{}");
            return artifact;
        }
        
        @Override
        public void close() {
            closed = true;
            if (closeFailure) {
                throw new IllegalStateException("close failed");
            }
        }
    }
    
    private static class FakeOperator implements AiResourceOperator {
        
        private final boolean returnNull;
        
        private boolean lastOverwriteExisting;
        
        FakeOperator(boolean returnNull) {
            this.returnNull = returnNull;
        }
        
        @Override
        public String resourceType() {
            return "mcp";
        }
        
        @Override
        public AiResourceImportValidationItem validate(String namespaceId,
            AiResourceImportArtifact artifact, boolean overwriteExisting) {
            lastOverwriteExisting = overwriteExisting;
            if (returnNull) {
                return null;
            }
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
            lastOverwriteExisting = overwriteExisting;
            if (returnNull) {
                return null;
            }
            AiResourceImportResultItem result = new AiResourceImportResultItem();
            result.setExternalId(artifact.getExternalId());
            result.setResourceName("imported");
            result.setVersion(artifact.getVersion());
            result.setStatus(AiResourceImportResultStatus.SUCCESS);
            return result;
        }
    }
}
