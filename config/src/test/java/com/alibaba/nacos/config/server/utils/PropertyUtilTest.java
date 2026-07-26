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

package com.alibaba.nacos.config.server.utils;

import com.alibaba.nacos.sys.env.EnvUtil;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.security.util.FieldUtils;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(SpringExtension.class)
class PropertyUtilTest {
    
    MockedStatic<EnvUtil> envUtilMockedStatic;
    
    private String mockMem = "tmpmocklimitfile.txt";
    
    @BeforeEach
    void setUp() {
        envUtilMockedStatic = Mockito.mockStatic(EnvUtil.class);
        envUtilMockedStatic.when(() -> EnvUtil.getProperty(eq("memory_limit_file_path"),
            eq("/sys/fs/cgroup/memory/memory.limit_in_bytes"))).thenReturn(mockMem);
        
    }
    
    @AfterEach
    void after() throws Exception {
        envUtilMockedStatic.close();
        resetPropertyValues();
        clearAllDumpFiled();
        File file = new File(mockMem);
        if (file.exists()) {
            file.delete();
        }
    }
    
    @Test
    void testGetPropertyV1() {
        
        envUtilMockedStatic.when(() -> EnvUtil.getProperty(eq("test"))).thenReturn("test");
        assertEquals("test", new PropertyUtil().getProperty("test"));
        
    }
    
    @Test
    void testGetPropertyV2() {
        
        envUtilMockedStatic.when(() -> EnvUtil.getProperty(eq("test"), eq("default")))
            .thenReturn("default");
        assertEquals("default", new PropertyUtil().getProperty("test", "default"));
    }
    
    private void clearAllDumpFiled() throws Exception {
        Field allDumpPageSizeFiled = FieldUtils.getField(PropertyUtil.class, "allDumpPageSize");
        allDumpPageSizeFiled.setAccessible(true);
        allDumpPageSizeFiled.set(null, null);
        
        Field limitMemoryFileFiled = FieldUtils.getField(PropertyUtil.class, "limitMemoryFile");
        limitMemoryFileFiled.setAccessible(true);
        limitMemoryFileFiled.set(null, null);
    }
    
    private void resetPropertyValues() throws Exception {
        PropertyUtil.setDumpChangeOn(true);
        PropertyUtil.setDumpChangeWorkerInterval(30 * 1000L);
        PropertyUtil.setNotifyConnectTimeout(100);
        PropertyUtil.setNotifySocketTimeout(200);
        PropertyUtil.setMaxHealthCheckFailCount(12);
        PropertyUtil.setHealthCheck(true);
        PropertyUtil.setMaxContent(10 * 1024 * 1024);
        PropertyUtil.setManageCapacity(true);
        PropertyUtil.setDefaultClusterQuota(100000);
        PropertyUtil.setCapacityLimitCheck(false);
        PropertyUtil.setDefaultGroupQuota(200);
        PropertyUtil.setDefaultTenantQuota(200);
        PropertyUtil.setInitialExpansionPercent(100);
        PropertyUtil.setDefaultMaxSize(100 * 1024);
        PropertyUtil.setDefaultMaxAggrCount(10000);
        PropertyUtil.setDefaultMaxAggrSize(1024);
        PropertyUtil.setCorrectUsageDelay(10 * 60);
        Field configRententionDaysField =
            FieldUtils.getField(PropertyUtil.class, "configRententionDays");
        configRententionDaysField.setAccessible(true);
        configRententionDaysField.set(null, 30);
    }
    
    @Test
    void testGetAllDumpPageSize() throws Exception {
        
        clearAllDumpFiled();
        File file = new File(mockMem);
        
        //2G pageSize between  50 to 1000
        long gb2 = 2L * 1024L * 1024L * 1024L;
        FileUtils.writeStringToFile(file, String.valueOf(gb2));
        int allDumpPageSizeNormal = PropertyUtil.getAllDumpPageSize();
        //expect  2*2*50
        assertEquals(200, allDumpPageSizeNormal);
        
        clearAllDumpFiled();
        // 12G pageSize over 1000
        long gb12 = 12L * 1024L * 1024L * 1024L;
        FileUtils.writeStringToFile(file, String.valueOf(gb12));
        int allDumpPageSizeOverMax = PropertyUtil.getAllDumpPageSize();
        assertEquals(1000, allDumpPageSizeOverMax);
        
        clearAllDumpFiled();
        //100MB
        long mb100 = 100L * 1024L * 1024L;
        FileUtils.writeStringToFile(file, String.valueOf(mb100));
        
        int allDumpPageSizeUnderMin = PropertyUtil.getAllDumpPageSize();
        assertEquals(50, allDumpPageSizeUnderMin);
    }
    
    @Test
    void testGetAllDumpPageSizeWithJvmArgs() throws Exception {
        
        File file = new File(mockMem);
        if (file.exists()) {
            file.delete();
        }
        int allDumpPageSizeUnderMin = PropertyUtil.initAllDumpPageSize();
        long maxMem = Runtime.getRuntime().maxMemory();
        long pageSize = maxMem / 1024 / 1024 / 512 * 50;
        if (pageSize < 50) {
            assertEquals(50, allDumpPageSizeUnderMin);
        } else if (pageSize > 1000) {
            assertEquals(1000, allDumpPageSizeUnderMin);
        } else {
            assertEquals(pageSize, allDumpPageSizeUnderMin);
        }
    }
    
    @Test
    void testIsStandaloneMode() {
        envUtilMockedStatic.when(EnvUtil::getStandaloneMode).thenReturn(true);
        assertTrue(PropertyUtil.isStandaloneMode());
        
        envUtilMockedStatic.when(EnvUtil::getStandaloneMode).thenReturn(false);
        assertFalse(PropertyUtil.isStandaloneMode());
    }
    
    @Test
    void testSetConfigRententionDaysInvalid() throws Exception {
        envUtilMockedStatic.when(
            () -> EnvUtil.getProperty(eq("nacos.config.retention.days")))
            .thenReturn("not_a_number");
        
        int originalDays = PropertyUtil.getConfigRententionDays();
        
        java.lang.reflect.Method m =
            PropertyUtil.class.getDeclaredMethod("setConfigRententionDays");
        m.setAccessible(true);
        m.invoke(new PropertyUtil());
        
        assertEquals(originalDays, PropertyUtil.getConfigRententionDays());
    }
    
    @Test
    void testSetConfigRententionDaysZero() throws Exception {
        envUtilMockedStatic.when(
            () -> EnvUtil.getProperty(eq("nacos.config.retention.days")))
            .thenReturn("0");
        
        int originalDays = PropertyUtil.getConfigRententionDays();
        
        java.lang.reflect.Method m =
            PropertyUtil.class.getDeclaredMethod("setConfigRententionDays");
        m.setAccessible(true);
        m.invoke(new PropertyUtil());
        
        assertEquals(originalDays, PropertyUtil.getConfigRententionDays());
    }
    
    @Test
    void testGetStringNonNull() throws Exception {
        envUtilMockedStatic.when(
            () -> EnvUtil.getProperty(eq("test.key.existing")))
            .thenReturn("actual_value");
        
        java.lang.reflect.Method m =
            PropertyUtil.class.getDeclaredMethod("getString", String.class,
                String.class);
        m.setAccessible(true);
        String result = (String) m.invoke(new PropertyUtil(), "test.key.existing",
            "default");
        assertEquals("actual_value", result);
    }
    
    @Test
    void testGetStringDefaultValue() throws Exception {
        envUtilMockedStatic.when(() -> EnvUtil.getProperty(eq("test.key.missing")))
            .thenReturn(null);
        
        java.lang.reflect.Method method = PropertyUtil.class.getDeclaredMethod("getString",
            String.class, String.class);
        method.setAccessible(true);
        
        String result = (String) method.invoke(new PropertyUtil(), "test.key.missing",
            "default");
        
        assertEquals("default", result);
    }
    
    @Test
    void testAccessors() {
        PropertyUtil.setDumpChangeWorkerInterval(1L);
        PropertyUtil.setNotifyConnectTimeout(2);
        PropertyUtil.setNotifySocketTimeout(3);
        PropertyUtil.setMaxHealthCheckFailCount(4);
        PropertyUtil.setHealthCheck(false);
        PropertyUtil.setMaxContent(5);
        PropertyUtil.setManageCapacity(false);
        PropertyUtil.setDefaultClusterQuota(6);
        PropertyUtil.setCapacityLimitCheck(true);
        PropertyUtil.setDefaultGroupQuota(7);
        PropertyUtil.setDefaultTenantQuota(8);
        PropertyUtil.setInitialExpansionPercent(9);
        PropertyUtil.setDefaultMaxSize(10);
        PropertyUtil.setDefaultMaxAggrCount(11);
        PropertyUtil.setDefaultMaxAggrSize(12);
        PropertyUtil.setCorrectUsageDelay(13);
        
        assertEquals(1L, PropertyUtil.getDumpChangeWorkerInterval());
        assertEquals(2, PropertyUtil.getNotifyConnectTimeout());
        assertEquals(3, PropertyUtil.getNotifySocketTimeout());
        assertEquals(4, PropertyUtil.getMaxHealthCheckFailCount());
        assertFalse(PropertyUtil.isHealthCheck());
        assertEquals(5, PropertyUtil.getMaxContent());
        assertFalse(PropertyUtil.isManageCapacity());
        assertEquals(6, PropertyUtil.getDefaultClusterQuota());
        assertTrue(PropertyUtil.isCapacityLimitCheck());
        assertEquals(7, PropertyUtil.getDefaultGroupQuota());
        assertEquals(8, PropertyUtil.getDefaultTenantQuota());
        assertEquals(9, PropertyUtil.getInitialExpansionPercent());
        assertEquals(10, PropertyUtil.getDefaultMaxSize());
        assertEquals(11, PropertyUtil.getDefaultMaxAggrCount());
        assertEquals(12, PropertyUtil.getDefaultMaxAggrSize());
        assertEquals(13, PropertyUtil.getCorrectUsageDelay());
    }
    
    @Test
    void testInitialize() {
        mockPropertyWithDefault("notifyConnectTimeout", "101");
        mockPropertyWithDefault("notifySocketTimeout", "202");
        mockPropertyWithDefault("isHealthCheck", "false");
        mockPropertyWithDefault("maxHealthCheckFailCount", "3");
        mockPropertyWithDefault("maxContent", "123");
        mockPropertyWithDefault("isManageCapacity", "false");
        mockPropertyWithDefault("isCapacityLimitCheck", "true");
        mockPropertyWithDefault("defaultClusterQuota", "10");
        mockPropertyWithDefault("defaultGroupQuota", "11");
        mockPropertyWithDefault("defaultTenantQuota", "12");
        mockPropertyWithDefault("defaultMaxSize", "13");
        mockPropertyWithDefault("defaultMaxAggrCount", "14");
        mockPropertyWithDefault("defaultMaxAggrSize", "15");
        mockPropertyWithDefault("correctUsageDelay", "16");
        mockPropertyWithDefault("initialExpansionPercent", "17");
        mockPropertyWithDefault("dumpChangeOn", "false");
        mockPropertyWithDefault("dumpChangeWorkerInterval", "18000");
        envUtilMockedStatic.when(() -> EnvUtil.getProperty(eq("nacos.config.retention.days")))
            .thenReturn("19");
        
        new PropertyUtil().initialize(null);
        
        assertEquals(101, PropertyUtil.getNotifyConnectTimeout());
        assertEquals(202, PropertyUtil.getNotifySocketTimeout());
        assertFalse(PropertyUtil.isHealthCheck());
        assertEquals(3, PropertyUtil.getMaxHealthCheckFailCount());
        assertEquals(123, PropertyUtil.getMaxContent());
        assertFalse(PropertyUtil.isManageCapacity());
        assertTrue(PropertyUtil.isCapacityLimitCheck());
        assertEquals(10, PropertyUtil.getDefaultClusterQuota());
        assertEquals(11, PropertyUtil.getDefaultGroupQuota());
        assertEquals(12, PropertyUtil.getDefaultTenantQuota());
        assertEquals(13, PropertyUtil.getDefaultMaxSize());
        assertEquals(14, PropertyUtil.getDefaultMaxAggrCount());
        assertEquals(15, PropertyUtil.getDefaultMaxAggrSize());
        assertEquals(16, PropertyUtil.getCorrectUsageDelay());
        assertEquals(17, PropertyUtil.getInitialExpansionPercent());
        assertEquals(19, PropertyUtil.getConfigRententionDays());
        assertFalse(PropertyUtil.isDumpChangeOn());
        assertEquals(18000L, PropertyUtil.getDumpChangeWorkerInterval());
    }
    
    @Test
    void testInitializeWhenPropertyInvalidThrowsException() {
        envUtilMockedStatic.when(() -> EnvUtil.getProperty(eq("notifyConnectTimeout"),
            anyString())).thenReturn("invalid");
        
        assertThrows(NumberFormatException.class, () -> new PropertyUtil().initialize(null));
    }
    
    private void mockPropertyWithDefault(String key, String value) {
        envUtilMockedStatic.when(() -> EnvUtil.getProperty(eq(key), anyString()))
            .thenReturn(value);
        envUtilMockedStatic.when(() -> EnvUtil.getProperty(eq(key))).thenReturn(value);
    }
}
