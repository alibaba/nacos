/*
 *   Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.alibaba.nacos.client.config.impl;

import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ConfigHttpClientManagerTest {
    
    @Test
    void test() {
        final ConfigHttpClientManager instance1 = ConfigHttpClientManager.getInstance();
        final ConfigHttpClientManager instance2 = ConfigHttpClientManager.getInstance();
        
        assertEquals(instance1, instance2);
        
        final NacosRestTemplate nacosRestTemplate = instance1.getNacosRestTemplate();
        assertNotNull(nacosRestTemplate);
        
        final int time1 = instance1.getConnectTimeoutOrDefault(10);
        assertEquals(1000, time1);
        final int time2 = instance1.getConnectTimeoutOrDefault(2000);
        assertEquals(2000, time2);
        
        Assertions.assertDoesNotThrow(() -> {
            instance1.shutdown();
        });
    }
    
}
