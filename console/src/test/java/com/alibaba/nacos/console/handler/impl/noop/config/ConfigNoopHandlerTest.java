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

class ConfigNoopHandlerTest {
    
    ConfigNoopHandler configNoopHandler;
    
    @BeforeEach
    void setUp() {
        configNoopHandler = new ConfigNoopHandler();
    }
    
    @AfterEach
    void tearDown() {
    }
    
    @Test
    void getConfigList() {
        assertThrows(NacosApiException.class, () -> configNoopHandler.getConfigList(1, 1, "dataId", "group", "", null),
                "Current functionMode is `naming`, config module is disabled.");
    }
    
    @Test
    void getConfigDetail() {
        assertThrows(NacosApiException.class, () -> configNoopHandler.getConfigDetail("dataId", "group", ""),
                "Current functionMode is `naming`, config module is disabled.");
    }
    
    @Test
    void publishConfig() {
        assertThrows(NacosApiException.class, () -> configNoopHandler.publishConfig(null, null),
                "Current functionMode is `naming`, config module is disabled.");
    }
    
    @Test
    void deleteConfig() {
        assertThrows(NacosApiException.class, () -> configNoopHandler.deleteConfig("", "", "", "", "", ""),
                "Current functionMode is `naming`, config module is disabled.");
    }
    
    @Test
    void batchDeleteConfigs() {
        assertThrows(NacosApiException.class, () -> configNoopHandler.batchDeleteConfigs(null, "", ""),
                "Current functionMode is `naming`, config module is disabled.");
    }
    
    @Test
    void getConfigListByContent() {
        assertThrows(NacosApiException.class,
                () -> configNoopHandler.getConfigListByContent("", 1, 1, "", "", "", null),
                "Current functionMode is `naming`, config module is disabled.");
    }
    
    @Test
    void getListeners() {
        assertThrows(NacosApiException.class, () -> configNoopHandler.getListeners("", "", "", true),
                "Current functionMode is `naming`, config module is disabled.");
    }
    
    @Test
    void getAllSubClientConfigByIp() {
        assertThrows(NacosApiException.class, () -> configNoopHandler.getAllSubClientConfigByIp("", true, "", true),
                "Current functionMode is `naming`, config module is disabled.");
    }
    
    @Test
    void exportConfig() {
        assertThrows(NacosApiException.class, () -> configNoopHandler.exportConfig("", "", "", "", null),
                "Current functionMode is `naming`, config module is disabled.");
    }
    
    @Test
    void importAndPublishConfig() {
        assertThrows(NacosApiException.class,
                () -> configNoopHandler.importAndPublishConfig(null, "", null, null, null, ""),
                "Current functionMode is `naming`, config module is disabled.");
    }
    
    @Test
    void cloneConfig() {
        assertThrows(NacosApiException.class, () -> configNoopHandler.cloneConfig(null, "", null, null, "", ""),
                "Current functionMode is `naming`, config module is disabled.");
    }
    
    @Test
    void removeBetaConfig() {
        assertThrows(NacosApiException.class, () -> configNoopHandler.removeBetaConfig(null, "", "", "", "", ""),
                "Current functionMode is `naming`, config module is disabled.");
    }
    
    @Test
    void queryBetaConfig() {
        assertThrows(NacosApiException.class, () -> configNoopHandler.queryBetaConfig(null, "", ""),
                "Current functionMode is `naming`, config module is disabled.");
    }
}