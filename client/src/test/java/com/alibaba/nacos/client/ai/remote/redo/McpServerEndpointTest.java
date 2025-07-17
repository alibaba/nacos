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

package com.alibaba.nacos.client.ai.remote.redo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class McpServerEndpointTest {
    
    @Test
    void testEquals() {
        McpServerEndpoint mcpServerEndpoint = new McpServerEndpoint("127.0.0.1", 8080, "v1");
        assertEquals(mcpServerEndpoint, mcpServerEndpoint);
        assertNotEquals(mcpServerEndpoint, null);
        assertNotEquals(mcpServerEndpoint, new Object());
        McpServerEndpoint mcpServerEndpoint1 = new McpServerEndpoint("127.0.0.1", 8080, "v1");
        assertEquals(mcpServerEndpoint, mcpServerEndpoint1);
        mcpServerEndpoint1 = new McpServerEndpoint("127.0.0.1", 8080, "v2");
        assertNotEquals(mcpServerEndpoint, mcpServerEndpoint1);
        mcpServerEndpoint1 = new McpServerEndpoint("127.0.0.2", 8080, "v1");
        assertNotEquals(mcpServerEndpoint, mcpServerEndpoint1);
        mcpServerEndpoint1 = new McpServerEndpoint("127.0.0.1", 8081, "v1");
        assertNotEquals(mcpServerEndpoint, mcpServerEndpoint1);
    }
}