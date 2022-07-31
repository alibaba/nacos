/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.cluster.address;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.Assert.assertEquals;

/**
 * Date 2022/7/31.
 *
 * @author GuoJiangFu
 */
@RunWith(MockitoJUnitRunner.class)
public class ServerConfAddressPluginTest {
    
    private ServerConfAddressPlugin serverConfAddressPlugin;
    
    
    @Before
    public void setUp() throws NacosException {
        EnvUtil.setEnvironment(new MockEnvironment());
        serverConfAddressPlugin = new ServerConfAddressPlugin();
        serverConfAddressPlugin.start();
    }
    
    @After
    public void tearDown() throws NacosException {
        serverConfAddressPlugin.shutdown();
    }
    
    @Test
    public void testGetPluginName() {
        assertEquals("file", serverConfAddressPlugin.getPluginName());
    }
}
