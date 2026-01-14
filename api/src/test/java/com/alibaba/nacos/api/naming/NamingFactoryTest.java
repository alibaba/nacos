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

package com.alibaba.nacos.api.naming;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.naming.NacosNamingService;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NamingFactoryTest {
    
    @Test
    void testCreateNamingServiceByPropertiesSuccess() throws NacosException {
        NacosNamingService.IS_THROW_EXCEPTION.set(false);
        assertNotNull(NamingFactory.createNamingService(new Properties()));
    }
    
    @Test
    void testCreateNamingServiceByPropertiesFailure() {
        NacosNamingService.IS_THROW_EXCEPTION.set(true);
        assertThrows(NacosException.class, () -> NamingFactory.createNamingService(new Properties()));
    }
    
    @Test
    void testCreateNamingServiceByServerAddrSuccess() throws NacosException {
        NacosNamingService.IS_THROW_EXCEPTION.set(false);
        assertNotNull(NamingFactory.createNamingService("localhost:8848"));
    }
    
    @Test
    void testCreateNamingServiceByServerAddrFailure() {
        NacosNamingService.IS_THROW_EXCEPTION.set(true);
        assertThrows(NacosException.class, () -> NamingFactory.createNamingService("localhost:8848"));
    }
}