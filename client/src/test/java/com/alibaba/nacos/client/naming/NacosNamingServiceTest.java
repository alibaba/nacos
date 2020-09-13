/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.client.naming;

import com.alibaba.nacos.api.PropertyKeyConst;

import com.alibaba.nacos.common.utils.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;
import java.util.Properties;

import static org.junit.Assert.assertTrue;

@RunWith(MockitoJUnitRunner.class)
public class NacosNamingServiceTest {
    
    private String namingCacheDirProperty = null;
    
    private String nacosCacheDirProperty = null;
    
    private String userHomeProperty = null;
    
    @Before
    public void setUp() throws Exception {
        namingCacheDirProperty = System.getProperty("com.alibaba.nacos.naming.cache.dir");
        nacosCacheDirProperty = System.getProperty("nacos.cache.dir");
        userHomeProperty = System.getProperty("user.home");
        
        Properties properties = new Properties();
        String serverList = "127.0.0.1:9527";
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, serverList);
        properties.setProperty(PropertyKeyConst.NAMING_LOAD_CACHE_AT_START, "true");
        new NacosNamingService(properties);
    }
    
    @Test
    public void testCaches() {
        if (!StringUtils.isBlank(namingCacheDirProperty)) {
            File specificCacheDir = new File(namingCacheDirProperty);
            assertTrue(specificCacheDir.isDirectory());
        } else {
            if (!StringUtils.isBlank(nacosCacheDirProperty)) {
                File generatedNamingDir = new File(nacosCacheDirProperty + File.separator + "naming");
                assertTrue(generatedNamingDir.isDirectory());
            } else {
                File homeNamingDir = new File(userHomeProperty + File.separator
                        + "nacos" + File.separator + "naming");
                assertTrue(homeNamingDir.isDirectory());
            }
        }
    }
}