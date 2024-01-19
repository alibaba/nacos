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
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.security.util.FieldUtils;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.File;
import java.lang.reflect.Field;

import static org.mockito.ArgumentMatchers.eq;

@RunWith(SpringJUnit4ClassRunner.class)
public class PropertyUtilTest {
    
    MockedStatic<EnvUtil> envUtilMockedStatic;
    
    private String mockMem = "tmpmocklimitfile.txt";
    
    @Before
    public void setUp() {
        envUtilMockedStatic = Mockito.mockStatic(EnvUtil.class);
        envUtilMockedStatic.when(() -> EnvUtil.getProperty(eq("memory_limit_file_path"),
                eq("/sys/fs/cgroup/memory/memory.limit_in_bytes"))).thenReturn(mockMem);
        
    }
    
    @After
    public void after() {
        envUtilMockedStatic.close();
        File file = new File(mockMem);
        if (file.exists()) {
            file.delete();
        }
    }
    
    @Test
    public void testGetPropertyV1() {
        
        envUtilMockedStatic.when(() -> EnvUtil.getProperty(eq("test"))).thenReturn("test");
        Assert.assertEquals("test", new PropertyUtil().getProperty("test"));
        
    }
    
    @Test
    public void testGetPropertyV2() {
        
        envUtilMockedStatic.when(() -> EnvUtil.getProperty(eq("test"), eq("default"))).thenReturn("default");
        Assert.assertEquals("default", new PropertyUtil().getProperty("test", "default"));
    }
    
    private void clearAllDumpFiled() throws Exception {
        Field allDumpPageSizeFiled = FieldUtils.getField(PropertyUtil.class, "allDumpPageSize");
        allDumpPageSizeFiled.setAccessible(true);
        allDumpPageSizeFiled.set(null, null);
    }
    
    @Test
    public void testGetAllDumpPageSize() throws Exception {
        
        clearAllDumpFiled();
        File file = new File(mockMem);
        
        //2G pageSize between  50 to 1000
        long gb2 = 2L * 1024L * 1024L * 1024L;
        FileUtils.writeStringToFile(file, String.valueOf(gb2));
        int allDumpPageSizeNormal = PropertyUtil.getAllDumpPageSize();
        //expect  2*2*50
        Assert.assertEquals(200, allDumpPageSizeNormal);
        
        clearAllDumpFiled();
        // 12G pageSize over 1000
        long gb12 = 12L * 1024L * 1024L * 1024L;
        FileUtils.writeStringToFile(file, String.valueOf(gb12));
        int allDumpPageSizeOverMax = PropertyUtil.getAllDumpPageSize();
        Assert.assertEquals(1000, allDumpPageSizeOverMax);
        
        clearAllDumpFiled();
        //100MB
        long mb100 = 100L * 1024L * 1024L;
        FileUtils.writeStringToFile(file, String.valueOf(mb100));
        
        int allDumpPageSizeUnderMin = PropertyUtil.getAllDumpPageSize();
        Assert.assertEquals(50, allDumpPageSizeUnderMin);
    }
    
    @Test
    public void testGetAllDumpPageSizeWithJvmArgs() throws Exception {
        
        File file = new File(mockMem);
        if (file.exists()) {
            file.delete();
        }
        int allDumpPageSizeUnderMin = PropertyUtil.initAllDumpPageSize();
        long maxMem = Runtime.getRuntime().maxMemory();
        long pageSize = maxMem / 1024 / 1024 / 512 * 50;
        if (pageSize < 50) {
            Assert.assertEquals(50, allDumpPageSizeUnderMin);
        } else if (pageSize > 1000) {
            Assert.assertEquals(1000, allDumpPageSizeUnderMin);
        } else {
            Assert.assertEquals(pageSize, allDumpPageSizeUnderMin);
        }
    }
    
}