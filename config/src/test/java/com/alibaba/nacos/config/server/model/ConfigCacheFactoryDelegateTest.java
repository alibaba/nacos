/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.model;

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Constructor;
import java.util.Collections;

import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigCacheFactoryDelegateTest {
    
    MockedStatic<EnvUtil> envUtilMockedStatic;
    
    MockedStatic<NacosServiceLoader> nacosServiceLoaderMockedStatic;
    
    @Mock
    NacosConfigCacheFactory nacosConfigCacheFactory;
    
    @BeforeEach
    void setUp() {
        envUtilMockedStatic = mockStatic(EnvUtil.class);
        nacosServiceLoaderMockedStatic = mockStatic(NacosServiceLoader.class);
    }
    
    @AfterEach
    void tearDown() {
        envUtilMockedStatic.close();
        nacosServiceLoaderMockedStatic.close();
    }
    
    @Test
    public void test() {
        nacosServiceLoaderMockedStatic.when(() -> NacosServiceLoader.load(ConfigCacheFactory.class))
                .thenReturn(Collections.singletonList(nacosConfigCacheFactory));
        envUtilMockedStatic.when(() -> EnvUtil.getProperty("nacos.config.cache.type", "nacos")).thenReturn("lalala");
        ConfigCache configCache = ConfigCacheFactoryDelegate.getInstance().createConfigCache();
        ConfigCache configCache1 = ConfigCacheFactoryDelegate.getInstance().createConfigCache("md5", 123456789L);
        ConfigCacheGray configCacheGray = ConfigCacheFactoryDelegate.getInstance().createConfigCacheGray("grayName");
        ConfigCacheGray configCacheGray1 = ConfigCacheFactoryDelegate.getInstance().createConfigCacheGray();
        verify(nacosConfigCacheFactory, times(0)).createConfigCache();
        verify(nacosConfigCacheFactory, times(0)).createConfigCacheGray();
    }
    
    @Test
    public void test2() throws Exception {
        when(nacosConfigCacheFactory.getName()).thenReturn("nacos");
        when(nacosConfigCacheFactory.createConfigCache()).thenReturn(new ConfigCache());
        when(nacosConfigCacheFactory.createConfigCacheGray()).thenReturn(new ConfigCacheGray());
        nacosServiceLoaderMockedStatic.when(() -> NacosServiceLoader.load(ConfigCacheFactory.class))
                .thenReturn(Collections.singletonList(nacosConfigCacheFactory));
        envUtilMockedStatic.when(() -> EnvUtil.getProperty("nacos.config.cache.type", "nacos")).thenReturn("nacos");
        Constructor constructor = ConfigCacheFactoryDelegate.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        ConfigCacheFactoryDelegate configCacheFactoryDelegate = (ConfigCacheFactoryDelegate) constructor.newInstance();
        configCacheFactoryDelegate.createConfigCache();
        configCacheFactoryDelegate.createConfigCache("md5", 123456789L);
        configCacheFactoryDelegate.createConfigCacheGray("grayName");
        configCacheFactoryDelegate.createConfigCacheGray();
        verify(nacosConfigCacheFactory, times(2)).createConfigCache();
        verify(nacosConfigCacheFactory, times(2)).createConfigCacheGray();
    }
}