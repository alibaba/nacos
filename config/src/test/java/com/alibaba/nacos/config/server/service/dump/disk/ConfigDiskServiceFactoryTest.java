/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.service.dump.disk;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class ConfigDiskServiceFactoryTest {
    
    @BeforeEach
    void before() throws Exception {
        clearDiskInstance();
    }
    
    @AfterEach
    void after() {
    
    }
    
    @Test
    void getRawDiskInstance() {
        System.setProperty("config_disk_type", "rawdisk");
        ConfigDiskService instance = ConfigDiskServiceFactory.getInstance();
        assertTrue(instance instanceof ConfigRawDiskService);
    }
    
    @Test
    void getRockDbDiskInstance() {
        System.setProperty("config_disk_type", "rocksdb");
        ConfigDiskService instance = ConfigDiskServiceFactory.getInstance();
        assertTrue(instance instanceof ConfigRocksDbDiskService);
    }
    
    @Test
    void getDefaultRawDiskInstance() {
        System.setProperty("config_disk_type", "123");
        ConfigDiskService instance = ConfigDiskServiceFactory.getInstance();
        assertTrue(instance instanceof ConfigRawDiskService);
    }
    
    private void clearDiskInstance() throws Exception {
        Field configDiskService = ConfigDiskServiceFactory.class.getDeclaredField("configDiskService");
        configDiskService.setAccessible(true);
        configDiskService.set(null, null);
    }
}
