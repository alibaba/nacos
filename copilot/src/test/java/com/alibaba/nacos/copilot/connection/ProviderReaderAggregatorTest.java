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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test for ProviderReaderAggregator.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
class ProviderReaderAggregatorTest {
    
    private static final ProviderReadContext CONTEXT =
        new ProviderReadContext(new CopilotProperties());
    
    @Mock
    private CopilotConnectionValidator connectionValidator;
    
    @BeforeEach
    void setUp() {
        lenient().when(connectionValidator.validate(any()))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }
    
    @Test
    void testHigherPriorityReaderWinsOnSameName() {
        CopilotModelConnection legacy = buildConnection(" prod-qwen ", "qwen-turbo", true, 100);
        CopilotModelConnection deployment =
            buildConnection("prod-qwen", "qwen-max", true, 10);
        ProviderReaderAggregator aggregator = aggregator(
            reader(100, Collections.singletonList(legacy)),
            reader(10, Collections.singletonList(deployment)));
        
        ConnectionTable table = aggregator.readAll(CONTEXT);
        
        assertEquals(1, table.size());
        assertEquals("qwen-max", table.get("PROD-QWEN").getModel());
    }
    
    @Test
    void testFirstConnectionWinsInsideOneReader() {
        ProviderReaderAggregator aggregator = aggregator(
            reader(10, Arrays.asList(buildConnection(" Legacy ", "qwen-plus", true, 10),
                buildConnection("LEGACY", "qwen-max", true, 10))));
        
        ConnectionTable table = aggregator.readAll(CONTEXT);
        
        assertEquals(1, table.size());
        assertEquals("qwen-plus", table.get("legacy").getModel());
    }
    
    @Test
    void testDistinctConnectionsAreKept() {
        ProviderReaderAggregator aggregator = aggregator(
            reader(10, Arrays.asList(buildConnection("prod-qwen", "qwen-max", true, 10),
                buildConnection("local-ollama", "qwen2.5", true, 10))));
        
        ConnectionTable table = aggregator.readAll(CONTEXT);
        
        assertEquals(2, table.size());
        assertEquals("qwen2.5", table.get("local-ollama").getModel());
    }
    
    @Test
    void testDefaultSkipsDisabledConnections() {
        ProviderReaderAggregator aggregator = aggregator(
            reader(10, Arrays.asList(buildConnection("disabled", "qwen-turbo", false, 10),
                buildConnection("enabled", "qwen-max", true, 10))));
        
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
        ProviderReaderAggregator aggregator = aggregator(broken,
            reader(100, Collections.singletonList(
                buildConnection("legacy", "qwen-turbo", true, 100))));
        
        ConnectionTable table = aggregator.readAll(CONTEXT);
        
        assertEquals(1, table.size());
        assertEquals("legacy", table.getDefault().getName());
    }
    
    @Test
    void testEmptyTableHasNoDefault() {
        ProviderReaderAggregator aggregator =
            aggregator(reader(10, Collections.emptyList()));
        
        ConnectionTable table = aggregator.readAll(CONTEXT);
        
        assertEquals(0, table.size());
        assertNull(table.getDefault());
    }
    
    @Test
    void testHigherPriorityConnectionsComeFirst() {
        ProviderReaderAggregator aggregator = aggregator(
            reader(100, Collections.singletonList(
                buildConnection("legacy", "qwen-turbo", true, 100))),
            reader(10, Collections.singletonList(
                buildConnection("prod-qwen", "qwen-max", true, 10))));
        
        ConnectionTable table = aggregator.readAll(CONTEXT);
        
        assertEquals("prod-qwen", table.getAll().iterator().next().getName());
        assertEquals("prod-qwen", table.getDefault().getName());
    }
    
    @Test
    void testSamePriorityConnectionsAreOrderedByName() {
        ProviderReaderAggregator aggregator = aggregator(
            reader(10, Arrays.asList(buildConnection(" Prod-Qwen ", "qwen-max", true, 10),
                buildConnection("alpha", "qwen2.5", true, 10))));
        
        ConnectionTable table = aggregator.readAll(CONTEXT);
        
        assertEquals("alpha", table.getAll().iterator().next().getName());
        assertEquals("alpha", table.getDefault().getName());
    }
    
    @Test
    void testEveryConnectionIsValidated() {
        CopilotModelConnection unavailable = buildConnection("prod-qwen", "qwen-max", true, 10)
            .withAvailability(false, "no provider");
        when(connectionValidator.validate(any())).thenReturn(unavailable);
        ProviderReaderAggregator aggregator = aggregator(
            reader(10, Collections.singletonList(
                buildConnection("prod-qwen", "qwen-max", true, 10))));
        
        ConnectionTable table = aggregator.readAll(CONTEXT);
        
        assertFalse(table.get("prod-qwen").isAvailable());
        assertEquals("no provider", table.get("prod-qwen").getUnavailableReason());
    }
    
    @Test
    void testValidationFailureMarksOnlyThatConnectionUnavailable() {
        when(connectionValidator.validate(any())).thenAnswer(invocation -> {
            CopilotModelConnection connection = invocation.getArgument(0);
            if ("broken".equals(connection.getName())) {
                throw new IllegalStateException("probe failed");
            }
            return connection;
        });
        ProviderReaderAggregator aggregator = aggregator(
            reader(10, Arrays.asList(buildConnection("broken", "qwen-turbo", true, 10),
                buildConnection("prod-qwen", "qwen-max", true, 10))));
        
        ConnectionTable table = aggregator.readAll(CONTEXT);
        
        assertFalse(table.get("broken").isAvailable());
        assertEquals("probe failed", table.get("broken").getUnavailableReason());
        assertTrue(table.get("prod-qwen").isAvailable());
    }
    
    @Test
    void testValidatorSeesMergedConnectionsOnly() {
        ProviderReaderAggregator aggregator = aggregator(
            reader(100, Collections.singletonList(
                buildConnection("prod-qwen", "qwen-turbo", true, 100))),
            reader(10, Collections.singletonList(
                buildConnection("prod-qwen", "qwen-max", true, 10))));
        
        aggregator.readAll(CONTEXT);
        
        verify(connectionValidator).validate(any());
    }
    
    private ProviderReaderAggregator aggregator(ProviderReader... readers) {
        return new ProviderReaderAggregator(Arrays.asList(readers), connectionValidator);
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
    
    private CopilotModelConnection buildConnection(String name, String model, boolean enabled,
        int priority) {
        return CopilotModelConnection.builder()
            .name(name)
            .providerId("dashscope")
            .model(model)
            .enabled(enabled)
            .priority(priority)
            .build();
    }
}
