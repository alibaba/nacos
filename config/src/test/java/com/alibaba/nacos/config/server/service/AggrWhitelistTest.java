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

package com.alibaba.nacos.config.server.service;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
public class AggrWhitelistTest {
    
    AggrWhitelist service;
    
    @Before
    public void before() throws Exception {
        service = new AggrWhitelist();
    }
    
    @Test
    public void testIsAggrDataId() {
        List<String> list = new ArrayList<String>();
        list.add("com.taobao.jiuren.*");
        list.add("NS_NACOS_SUBSCRIPTION_TOPIC_*");
        list.add("com.taobao.tae.AppListOnGrid-*");
        AggrWhitelist.compile(list);
        
        assertFalse(AggrWhitelist.isAggrDataId("com.abc"));
        assertFalse(AggrWhitelist.isAggrDataId("com.taobao.jiuren"));
        assertFalse(AggrWhitelist.isAggrDataId("com.taobao.jiurenABC"));
        assertTrue(AggrWhitelist.isAggrDataId("com.taobao.jiuren.abc"));
        assertTrue(AggrWhitelist.isAggrDataId("NS_NACOS_SUBSCRIPTION_TOPIC_abc"));
        assertTrue(AggrWhitelist.isAggrDataId("com.taobao.tae.AppListOnGrid-abc"));
    }
}
