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

package com.alibaba.nacos.console.handler.impl.noop.config;

import com.alibaba.nacos.api.exception.api.NacosApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class HistoryNoopHandlerTest {
    
    HistoryNoopHandler historyNoopHandler;
    
    @BeforeEach
    void setUp() {
        historyNoopHandler = new HistoryNoopHandler();
    }
    
    @AfterEach
    void tearDown() {
    }
    
    @Test
    void getConfigHistoryInfo() {
        assertThrows(NacosApiException.class, () -> historyNoopHandler.getConfigHistoryInfo("", "", "", 1L),
                "Current functionMode is `naming`, config module is disabled.");
    }
    
    @Test
    void listConfigHistory() {
        assertThrows(NacosApiException.class, () -> historyNoopHandler.listConfigHistory("", "", "", 1, 1),
                "Current functionMode is `naming`, config module is disabled.");
    }
    
    @Test
    void getPreviousConfigHistoryInfo() {
        assertThrows(NacosApiException.class, () -> historyNoopHandler.getPreviousConfigHistoryInfo("", "", "", 1L),
                "Current functionMode is `naming`, config module is disabled.");
    }
    
    @Test
    void getConfigsByTenant() {
        assertThrows(NacosApiException.class, () -> historyNoopHandler.getConfigsByTenant(""),
                "Current functionMode is `naming`, config module is disabled.");
    }
}