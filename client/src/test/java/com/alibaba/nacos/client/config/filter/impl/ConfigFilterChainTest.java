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

package com.alibaba.nacos.client.config.filter.impl;

import com.alibaba.nacos.api.exception.NacosException;
import org.junit.Assert;
import org.junit.Test;

public class ConfigFilterChainTest {
    
    @Test
    public void testConfigFilterChain() {
        ConfigFilterChainManager configFilterChainManager = new ConfigFilterChainManager(null);
        configFilterChainManager.addFilter(new DemoFilter1());
        configFilterChainManager.addFilter(new DemoFilter2());
        ConfigRequest configRequest = new ConfigRequest();
        try {
            configFilterChainManager.doFilter(configRequest, null);
            Assert.assertEquals(DemoFilter1.class.getName(), configRequest.getParameter("filter1"));
            Assert.assertEquals(DemoFilter2.class.getName(), configRequest.getParameter("filter2"));
        } catch (NacosException e) {
            e.printStackTrace();
        }
    }
}
