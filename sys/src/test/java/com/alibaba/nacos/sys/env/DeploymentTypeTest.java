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

package com.alibaba.nacos.sys.env;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class DeploymentTypeTest {
    
    @Test
    void testGetTypeName() {
        assertEquals("merged", DeploymentType.MERGED.getTypeName());
        assertEquals("server", DeploymentType.SERVER.getTypeName());
        assertEquals("console", DeploymentType.CONSOLE.getTypeName());
        assertEquals("serverWithMcp", DeploymentType.SERVER_WITH_MCP.getTypeName());
        assertEquals("unknown", DeploymentType.ILLEGAL.getTypeName());
    }
    
    @Test
    void testGetTypeValid() {
        assertSame(DeploymentType.MERGED, DeploymentType.getType("merged"));
        assertSame(DeploymentType.MERGED, DeploymentType.getType("MERGED"));
        assertSame(DeploymentType.MERGED, DeploymentType.getType("Merged"));
        
        assertSame(DeploymentType.SERVER, DeploymentType.getType("server"));
        assertSame(DeploymentType.SERVER, DeploymentType.getType("SERVER"));
        
        assertSame(DeploymentType.CONSOLE, DeploymentType.getType("console"));
        assertSame(DeploymentType.CONSOLE, DeploymentType.getType("CONSOLE"));
        
        assertSame(DeploymentType.SERVER_WITH_MCP, DeploymentType.getType("server_with_mcp"));
        assertSame(DeploymentType.SERVER_WITH_MCP, DeploymentType.getType("SERVER_WITH_MCP"));
    }
    
    @Test
    void testGetTypeInvalid() {
        assertSame(DeploymentType.ILLEGAL, DeploymentType.getType("invalid"));
        assertSame(DeploymentType.ILLEGAL, DeploymentType.getType("nonexistent"));
        assertSame(DeploymentType.ILLEGAL, DeploymentType.getType(""));
    }
}
