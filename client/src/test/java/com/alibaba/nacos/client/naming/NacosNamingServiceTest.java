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

package com.alibaba.nacos.client.naming;

import com.alibaba.nacos.api.PropertyKeyConst;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;
import java.util.Properties;

import static org.junit.Assert.assertTrue;

@RunWith(MockitoJUnitRunner.class)
public class NacosNamingServiceTest {
    
    private static final String CACHE_DIR = NacosNamingServiceTest.class.getResource("/").getPath() + "cache";
    
    private String serverList = "127.0.0.1:9527";
    
    @Before
    public void setUp() throws Exception {
        Properties properties = new Properties();
        System.setProperty("nacos.cache.dir", CACHE_DIR);
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, serverList);
        properties.setProperty(PropertyKeyConst.NAMING_LOAD_CACHE_AT_START, "true");
        NacosNamingService nacosNamingService = new NacosNamingService(properties);
    }
    
    @Test
    public void testCache() throws Exception {
        File cachePath = new File(CACHE_DIR);
        assertTrue(cachePath.exists());
    }
}
