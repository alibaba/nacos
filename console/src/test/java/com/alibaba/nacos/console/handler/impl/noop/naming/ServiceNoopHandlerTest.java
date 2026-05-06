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

package com.alibaba.nacos.console.handler.impl.noop.naming;

import com.alibaba.nacos.api.exception.api.NacosApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ServiceNoopHandlerTest {
    
    ServiceNoopHandler serviceNoopHandler;
    
    @BeforeEach
    void setUp() {
        serviceNoopHandler = new ServiceNoopHandler();
    }
    
    @AfterEach
    void tearDown() {
    }
    
    @Test
    void createService() {
        assertThrows(NacosApiException.class, () -> serviceNoopHandler.createService(null, null),
                "Current functionMode is `config`, naming module is disabled.");
    }
    
    @Test
    void deleteService() {
        assertThrows(NacosApiException.class, () -> serviceNoopHandler.deleteService("", "", ""),
                "Current functionMode is `config`, naming module is disabled.");
    }
    
    @Test
    void updateService() {
        assertThrows(NacosApiException.class, () -> serviceNoopHandler.updateService(null, null),
                "Current functionMode is `config`, naming module is disabled.");
    }
    
    @Test
    void getSelectorTypeList() {
        assertThrows(NacosApiException.class, serviceNoopHandler::getSelectorTypeList,
                "Current functionMode is `config`, naming module is disabled.");
    }
    
    @Test
    void getSubscribers() {
        assertThrows(NacosApiException.class, () -> serviceNoopHandler.getSubscribers(0, 0, "", "", "", false),
                "Current functionMode is `config`, naming module is disabled.");
    }
    
    @Test
    void getServiceList() {
        assertThrows(NacosApiException.class, () -> serviceNoopHandler.getServiceList(false, "", 0, 0, "", "", false),
                "Current functionMode is `config`, naming module is disabled.");
    }
    
    @Test
    void getServiceDetail() {
        assertThrows(NacosApiException.class, () -> serviceNoopHandler.getServiceDetail("", "", ""),
                "Current functionMode is `config`, naming module is disabled.");
    }
    
    @Test
    void updateClusterMetadata() {
        assertThrows(NacosApiException.class, () -> serviceNoopHandler.updateClusterMetadata("", "", "", "", null),
                "Current functionMode is `config`, naming module is disabled.");
    }
}