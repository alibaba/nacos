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

import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.naming.cache.ServiceInfoHolder;
import com.alibaba.nacos.client.naming.utils.CacheDirUtil;
import junit.framework.TestCase;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Properties;

public class DiskFailoverDataSourceTest extends TestCase {
    
    @Test
    public void testGetSwitch() {
        Properties prop = new Properties();
        ServiceInfoHolder holder = Mockito.mock(ServiceInfoHolder.class);
        Mockito.when(holder.getServiceInfoMap()).thenReturn(new HashMap<>());
        final NacosClientProperties properties = NacosClientProperties.PROTOTYPE.derive(prop);
        String cacheDir = CacheDirUtil.initCacheDir("public", properties);
        DiskFailoverDataSource diskFailoverDataSource = new DiskFailoverDataSource();
        diskFailoverDataSource.getSwitch();
    }
    
    @Test
    public void testGetFailoverData() {
        Properties prop = new Properties();
        ServiceInfoHolder holder = Mockito.mock(ServiceInfoHolder.class);
        Mockito.when(holder.getServiceInfoMap()).thenReturn(new HashMap<>());
        final NacosClientProperties properties = NacosClientProperties.PROTOTYPE.derive(prop);
        String cacheDir = CacheDirUtil.initCacheDir("public", properties);
        DiskFailoverDataSource diskFailoverDataSource = new DiskFailoverDataSource();
        diskFailoverDataSource.getFailoverData();
    }
    
}