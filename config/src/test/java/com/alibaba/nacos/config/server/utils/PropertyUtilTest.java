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
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(SpringExtension.class)
class PropertyUtilTest {
    
    MockedStatic<EnvUtil> envUtilMockedStatic;
    
    private String mockMem = "tmpmocklimitfile.txt";
    
    @BeforeEach
    void setUp() {
        envUtilMockedStatic = Mockito.mockStatic(EnvUtil.class);
        envUtilMockedStatic.when(() -> EnvUtil.getProperty(eq("memory_limit_file_path"), eq("/sys/fs/cgroup/memory/memory.limit_in_bytes")))
                .thenReturn(mockMem);
        
    }
    
    @AfterEach
    void after() {
        envUtilMockedStatic.close();
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
        
        envUtilMockedStatic.when(() -> EnvUtil.getProperty(eq("test"), eq("default"))).thenReturn("default");
        assertEquals("default", new PropertyUtil().getProperty("test", "default"));
    }
    
    private void clearAllDumpFiled() throws Exception {
        Field allDumpPageSizeFiled = FieldUtils.getField(PropertyUtil.class, "allDumpPageSize");
        allDumpPageSizeFiled.setAccessible(true);
        allDumpPageSizeFiled.set(null, null);
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
    
}