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

package com.alibaba.nacos.api.lock;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.lock.NacosLockService;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NacosLockFactoryTest {
    
    @Test
    void createAiServiceWithException() {
        NacosLockService.IS_THROW_EXCEPTION.set(true);
        assertThrows(NacosException.class, () -> NacosLockFactory.createLockService(new Properties()));
    }
    
    @Test
    void createAiServiceSuccess() throws NacosException {
        NacosLockService.IS_THROW_EXCEPTION.set(false);
        assertNotNull(NacosLockFactory.createLockService(new Properties()));
    }
}