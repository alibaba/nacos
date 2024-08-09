/*
 *   Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.alibaba.nacos.client.serverlist.holer;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.serverlist.holder.impl.FixedConfigNacosServerListHolder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * fixed config get nacos server list.
 *
 * @author xz
 * @since 2024/7/25 16:06
 */
public class FixedConfigNacosServerListHolderTest {
    
    @Test
    public void testGetServerList() {
        FixedConfigNacosServerListHolder holder = new FixedConfigNacosServerListHolder();
        List<String> serverList = holder.getServerList();
        
        assertTrue(serverList.isEmpty());
    }
    
    @Test
    public void testInitServerList() {
        FixedConfigNacosServerListHolder holder = new FixedConfigNacosServerListHolder();
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, "127.0.0.1:8848");
        
        boolean canApply = holder.canApply(NacosClientProperties.PROTOTYPE.derive(properties), "");
        assertTrue(canApply);
        
        List<String> serverList = holder.getServerList();
        assertEquals(1, serverList.size());
        assertEquals("127.0.0.1:8848", serverList.get(0));
    }
    
    @Test
    public void testTestGetName() {
        FixedConfigNacosServerListHolder holder = new FixedConfigNacosServerListHolder();
        
        assertEquals(holder.getName(), "fixed");
    }
}