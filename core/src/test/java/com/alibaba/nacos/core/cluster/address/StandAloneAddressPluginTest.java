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
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Date 2022/8/16.
 *
 * @author GuoJiangFu
 */
public class StandAloneAddressPluginTest {
    
    private StandAloneAddressPlugin standAloneAddressPlugin;
    
    @Before
    public void setUp() throws NacosException {
        EnvUtil.setEnvironment(new MockEnvironment());
        standAloneAddressPlugin = new StandAloneAddressPlugin();
        standAloneAddressPlugin.start();
    }
    
    @Test
    public void testGetServerList() {
        List<String> serverList = standAloneAddressPlugin.getServerList();
        assertEquals(1, serverList.size());
        assertEquals(EnvUtil.getLocalAddress(), serverList.get(0));
    }
    
    @Test
    public void testGetPluginName() {
        assertEquals("standalone", standAloneAddressPlugin.getPluginName());
    }
}
