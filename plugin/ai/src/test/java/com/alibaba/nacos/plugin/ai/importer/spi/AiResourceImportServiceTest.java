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

package com.alibaba.nacos.plugin.ai.importer.spi;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportArtifact;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportCandidate;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportCandidatePage;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportContext;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportItem;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportPayloadKind;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class AiResourceImportServiceTest {
    
    @Test
    void testBuilderCreatesImporterWithProperties() {
        Properties properties = new Properties();
        properties.setProperty("endpoint", "https://example.com");
        FakeImportServiceBuilder builder = new FakeImportServiceBuilder();
        
        AiResourceImportService service = builder.build(properties);
        
        assertEquals("fake-importer", builder.importerType());
        assertEquals("fake-importer", service.importerType());
        assertEquals(Collections.singleton("mcp"), service.supportedResourceTypes());
        assertSame(properties, ((FakeImportService) service).properties);
    }
    
    @Test
    void testSearchAndFetchContract() throws NacosException {
        AiResourceImportService service = new FakeImportService(new Properties());
        AiResourceImportContext context = new AiResourceImportContext();
        context.setResourceType("mcp");
        AiResourceImportItem item = new AiResourceImportItem();
        item.setExternalId("server-1");
        
        AiResourceImportCandidatePage page = service.search(context);
        AiResourceImportArtifact artifact = service.fetch(context, item);
        
        assertEquals(1, page.getItems().size());
        assertEquals("server-1", page.getItems().get(0).getExternalId());
        assertEquals("server-1", artifact.getExternalId());
        assertEquals(AiResourceImportPayloadKind.MCP_DETAIL, artifact.getPayloadKind());
    }
    
    private static class FakeImportServiceBuilder implements AiResourceImportServiceBuilder {
        
        @Override
        public String importerType() {
            return "fake-importer";
        }
        
        @Override
        public AiResourceImportService build(Properties properties) {
            return new FakeImportService(properties);
        }
    }
    
    private static class FakeImportService implements AiResourceImportService {
        
        private final Properties properties;
        
        private FakeImportService(Properties properties) {
            this.properties = properties;
        }
        
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
            AiResourceImportCandidate candidate = new AiResourceImportCandidate();
            candidate.setResourceType(context.getResourceType());
            candidate.setExternalId("server-1");
            candidate.setName("server");
            AiResourceImportCandidatePage page = new AiResourceImportCandidatePage();
            page.setItems(Collections.singletonList(candidate));
            return page;
        }
        
        @Override
        public AiResourceImportArtifact fetch(AiResourceImportContext context,
            AiResourceImportItem item) {
            AiResourceImportArtifact artifact = new AiResourceImportArtifact();
            artifact.setResourceType(context.getResourceType());
            artifact.setExternalId(item.getExternalId());
            artifact.setName(item.getName());
            artifact.setPayloadKind(AiResourceImportPayloadKind.MCP_DETAIL);
            artifact.setPayloadJson("{}");
            return artifact;
        }
    }
}
