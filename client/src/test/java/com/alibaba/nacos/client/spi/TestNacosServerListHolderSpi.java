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

package com.alibaba.nacos.client.spi;

import com.alibaba.nacos.client.serverlist.holder.NacosServerListHolder;
import com.alibaba.nacos.client.env.NacosClientProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * test nacos server list holder spi.
 *
 * @author xz
 * @since 2024/7/25 16:35
 */
public class TestNacosServerListHolderSpi implements NacosServerListHolder {
    
    public static boolean testEnable = false;
    
    private List<String> testServerList = new ArrayList<>();
    
    public TestNacosServerListHolderSpi() {
        testServerList.add("127.0.0.1:8848");
    }
    
    @Override
    public List<String> getServerList() {
        return this.testServerList;
    }
    
    @Override
    public boolean canApply(NacosClientProperties properties, String moduleName) {
        return testEnable;
    }
    
    @Override
    public String getName() {
        return "test";
    }
    
    @Override
    public int getOrder() {
        return Integer.MIN_VALUE;
    }
}
