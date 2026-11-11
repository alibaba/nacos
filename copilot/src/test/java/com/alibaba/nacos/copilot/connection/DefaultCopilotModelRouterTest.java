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

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Test for DefaultCopilotModelRouter.
 *
 * @author nacos
 */
class DefaultCopilotModelRouterTest {
    
    private final DefaultCopilotModelRouter router = new DefaultCopilotModelRouter();
    
    @Test
    void testSelectedConnectionWins() {
        ConnectionTable table = table(
            deployment("prod-qwen", true, true),
            legacy("legacy", true, true));
        
        CopilotModelConnection selected = router.route(table, "LEGACY");
        
        assertEquals("legacy", selected.getName());
    }
    
    @Test
    void testSelectedConnectionIgnoresCaseAndSpaces() {
        ConnectionTable table = table(
            deployment("prod-qwen", true, true),
            legacy("legacy", true, true));
        
        CopilotModelConnection selected = router.route(table, "  PROD-QWEN ");
        
        assertEquals("prod-qwen", selected.getName());
    }
    
    @Test
    void testUnavailableSelectionFallsBackToDeployment() {
        ConnectionTable table = table(
            deployment("prod-qwen", true, true),
            legacy("legacy", false, true));
        
        CopilotModelConnection selected = router.route(table, "legacy");
        
        assertEquals("prod-qwen", selected.getName());
    }
    
    @Test
    void testPrefersDeploymentOverLegacy() {
        ConnectionTable table = table(
            legacy("legacy", true, true),
            deployment("prod-qwen", true, true));
        
        CopilotModelConnection selected = router.route(table, null);
        
        assertEquals("prod-qwen", selected.getName());
    }
    
    @Test
    void testFallsBackToLegacy() {
        ConnectionTable table = table(
            deployment("prod-qwen", false, true),
            legacy("legacy", true, true));
        
        CopilotModelConnection selected = router.route(table, null);
        
        assertEquals("legacy", selected.getName());
    }
    
    @Test
    void testSkipsUnavailableConnections() {
        ConnectionTable table = table(
            deployment("unavailable", true, false),
            deployment("prod-qwen", true, true));
        
        CopilotModelConnection selected = router.route(table, "unavailable");
        
        assertEquals("prod-qwen", selected.getName());
    }
    
    @Test
    void testReturnsNullWhenNothingIsSelectable() {
        ConnectionTable table = table(
            deployment("disabled", false, true),
            legacy("broken", true, false));
        
        assertNull(router.route(table, "broken"));
    }
    
    @Test
    void testReturnsNullForEmptyTable() {
        assertNull(router.route(ConnectionTable.empty(), "legacy"));
    }
    
    private ConnectionTable table(CopilotModelConnection... connections) {
        List<CopilotModelConnection> list = Arrays.asList(connections);
        return new ConnectionTable(list);
    }
    
    private CopilotModelConnection deployment(String name, boolean enabled, boolean available) {
        return CopilotModelConnection.builder()
            .name(name)
            .providerId("dashscope")
            .model("qwen-max")
            .source(ConnectionSource.DEPLOYMENT)
            .priority(DeploymentConfigProviderReader.ORDER)
            .enabled(enabled)
            .available(available)
            .build();
    }
    
    private CopilotModelConnection legacy(String name, boolean enabled, boolean available) {
        return CopilotModelConnection.builder()
            .name(name)
            .providerId("dashscope")
            .model("qwen-turbo")
            .source(ConnectionSource.LEGACY)
            .priority(100)
            .enabled(enabled)
            .available(available)
            .build();
    }
}
