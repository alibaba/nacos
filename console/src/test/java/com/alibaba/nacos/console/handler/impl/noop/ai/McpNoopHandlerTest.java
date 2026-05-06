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

package com.alibaba.nacos.console.handler.impl.noop.ai;

import com.alibaba.nacos.api.exception.api.NacosApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class McpNoopHandlerTest {
    
    McpNoopHandler mcpNoopHandler;
    
    @BeforeEach
    void setUp() {
        mcpNoopHandler = new McpNoopHandler();
    }
    
    @AfterEach
    void tearDown() {
    }
    
    @Test
    void listMcpServers() {
        assertThrows(NacosApiException.class, () -> mcpNoopHandler.listMcpServers("", "", "", 1, 1),
                "Nacos AI MCP module and API required both `naming` and `config` module.");
    }
    
    @Test
    void getMcpServer() {
        assertThrows(NacosApiException.class, () -> mcpNoopHandler.getMcpServer("", "", "", ""),
                "Nacos AI MCP module and API required both `naming` and `config` module.");
    }
    
    @Test
    void createMcpServer() {
        assertThrows(NacosApiException.class, () -> mcpNoopHandler.createMcpServer("", null, null, null),
                "Nacos AI MCP module and API required both `naming` and `config` module.");
    }
    
    @Test
    void updateMcpServer() {
        assertThrows(NacosApiException.class, () -> mcpNoopHandler.updateMcpServer("", true, null, null, null, false),
                "Nacos AI MCP module and API required both `naming` and `config` module.");
    }
    
    @Test
    void deleteMcpServer() {
        assertThrows(NacosApiException.class, () -> mcpNoopHandler.deleteMcpServer("", "", "", ""),
                "Nacos AI MCP module and API required both `naming` and `config` module.");
    }
}