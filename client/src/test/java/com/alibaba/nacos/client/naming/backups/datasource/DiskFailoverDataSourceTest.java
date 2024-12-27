/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.naming.backups.datasource;

import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.client.naming.backups.FailoverData;
import com.alibaba.nacos.client.naming.backups.FailoverSwitch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiskFailoverDataSourceTest {
    
    DiskFailoverDataSource dataSource;
    
    @BeforeEach
    void setUp() {
        dataSource = new DiskFailoverDataSource();
    }
    
    @Test
    void testGetSwitchWithNonExistFailoverSwitchFile() {
        FailoverSwitch actual = dataSource.getSwitch();
        assertFalse(actual.getEnabled());
    }
    
    @Test
    void testGetSwitchForFailoverDisabled() throws NoSuchFieldException, IllegalAccessException {
        String dir = DiskFailoverDataSourceTest.class.getResource("/").getPath() + "/failover_test/disabled";
        injectFailOverDir(dir);
        assertFalse(dataSource.getSwitch().getEnabled());
        Map<String, FailoverData> actual = dataSource.getFailoverData();
        assertTrue(actual.isEmpty());
    }
    
    @Test
    void testGetSwitchForFailoverEnabled() throws NoSuchFieldException, IllegalAccessException {
        String dir = DiskFailoverDataSourceTest.class.getResource("/").getPath() + "/failover_test/enabled";
        injectFailOverDir(dir);
        assertTrue(dataSource.getSwitch().getEnabled());
        Map<String, FailoverData> actual = dataSource.getFailoverData();
        assertEquals(1, actual.size());
        assertTrue(actual.containsKey("legal@@with_name@@file"));
        assertEquals(FailoverData.DataType.naming, actual.get("legal@@with_name@@file").getDataType());
        assertEquals("1.1.1.1",
                ((ServiceInfo) actual.get("legal@@with_name@@file").getData()).getHosts().get(0).getIp());
    }
    
    @Test
    void testGetFailoverDataForFailoverDisabled() {
        Map<String, FailoverData> actual = dataSource.getFailoverData();
        assertTrue(actual.isEmpty());
    }
    
    private void injectFailOverDir(String failoverDir) throws NoSuchFieldException, IllegalAccessException {
        Field failoverDirField = DiskFailoverDataSource.class.getDeclaredField("failoverDir");
        failoverDirField.setAccessible(true);
        failoverDirField.set(dataSource, failoverDir);
    }
    
    @Test
    void testGetSwitchForFailoverEnabledKeep() throws NoSuchFieldException, IllegalAccessException {
        String dir = DiskFailoverDataSourceTest.class.getResource("/").getPath() + "/failover_test/enabled";
        injectFailOverDir(dir);
        assertTrue(dataSource.getSwitch().getEnabled());
        assertTrue(dataSource.getSwitch().getEnabled());
    }
    
    @Test
    void testGetSwitchForFailoverDisabledKeep() throws NoSuchFieldException, IllegalAccessException {
        String dir = DiskFailoverDataSourceTest.class.getResource("/").getPath() + "/failover_test/disabled";
        injectFailOverDir(dir);
        assertFalse(dataSource.getSwitch().getEnabled());
        assertFalse(dataSource.getSwitch().getEnabled());
    }
}
