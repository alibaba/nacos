/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.copilot.connection;

import com.alibaba.nacos.copilot.config.CopilotProperties;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Test for ProviderReaderAggregator.
 *
 * @author nacos
 */
class ProviderReaderAggregatorTest {
    
    private static final ProviderReadContext CONTEXT =
        new ProviderReadContext(new CopilotProperties());
    
    @Test
    void testHigherPriorityReaderWinsOnSameName() {
        CopilotModelConnection legacy = buildConnection(" prod-qwen ", "qwen-turbo", true);
        CopilotModelConnection deployment = buildConnection("prod-qwen", "qwen-max", true);
        ProviderReaderAggregator aggregator = new ProviderReaderAggregator(
            Arrays.asList(reader(100, Collections.singletonList(legacy)),
                reader(10, Collections.singletonList(deployment))));
        
        ConnectionTable table = aggregator.readAll(CONTEXT);
        
        assertEquals(1, table.size());
        assertEquals("qwen-max", table.get("PROD-QWEN").getModel());
    }
    
    @Test
    void testFirstConnectionWinsInsideOneReader() {
        ProviderReaderAggregator aggregator = new ProviderReaderAggregator(
            Collections.singletonList(reader(10, Arrays.asList(
                buildConnection(" Legacy ", "qwen-plus", true),
                buildConnection("LEGACY", "qwen-max", true)))));
        
        ConnectionTable table = aggregator.readAll(CONTEXT);
        
        assertEquals(1, table.size());
        assertEquals("qwen-plus", table.get("legacy").getModel());
    }
    
    @Test
    void testDistinctConnectionsAreKept() {
        ProviderReaderAggregator aggregator = new ProviderReaderAggregator(
            Arrays.asList(reader(10, Arrays.asList(
                buildConnection("prod-qwen", "qwen-max", true),
                buildConnection("local-ollama", "qwen2.5", true)))));
        
        ConnectionTable table = aggregator.readAll(CONTEXT);
        
        assertEquals(2, table.size());
        assertEquals("qwen2.5", table.get("local-ollama").getModel());
    }
    
    @Test
    void testDefaultSkipsDisabledConnections() {
        ProviderReaderAggregator aggregator = new ProviderReaderAggregator(
            Arrays.asList(reader(10, Arrays.asList(
                buildConnection("disabled", "qwen-turbo", false),
                buildConnection("enabled", "qwen-max", true)))));
        
        ConnectionTable table = aggregator.readAll(CONTEXT);
        
        assertEquals("enabled", table.getDefault().getName());
    }
    
    @Test
    void testBrokenReaderDoesNotHideOtherReaders() {
        ProviderReader broken = new ProviderReader() {
            
            @Override
            public int order() {
                return 10;
            }
            
            @Override
            public List<CopilotModelConnection> read(ProviderReadContext context) {
                throw new IllegalStateException("broken source");
            }
        };
        ProviderReaderAggregator aggregator = new ProviderReaderAggregator(
            Arrays.asList(broken,
                reader(100, Collections.singletonList(
                    buildConnection("legacy", "qwen-turbo", true)))));
        
        ConnectionTable table = aggregator.readAll(CONTEXT);
        
        assertEquals(1, table.size());
        assertEquals("legacy", table.getDefault().getName());
    }
    
    @Test
    void testEmptyTableHasNoDefault() {
        ProviderReaderAggregator aggregator = new ProviderReaderAggregator(
            Collections.singletonList(reader(10, Collections.emptyList())));
        
        ConnectionTable table = aggregator.readAll(CONTEXT);
        
        assertEquals(0, table.size());
        assertNull(table.getDefault());
    }
    
    private ProviderReader reader(int order, List<CopilotModelConnection> connections) {
        return new ProviderReader() {
            
            @Override
            public int order() {
                return order;
            }
            
            @Override
            public List<CopilotModelConnection> read(ProviderReadContext context) {
                return connections;
            }
        };
    }
    
    private CopilotModelConnection buildConnection(String name, String model, boolean enabled) {
        return CopilotModelConnection.builder()
            .name(name)
            .providerId("dashscope")
            .model(model)
            .enabled(enabled)
            .build();
    }
}
