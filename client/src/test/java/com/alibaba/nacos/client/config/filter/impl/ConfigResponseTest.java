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

package com.alibaba.nacos.client.config.filter.impl;

import com.alibaba.nacos.api.config.filter.IConfigContext;
import org.junit.Assert;
import org.junit.Test;

public class ConfigResponseTest {
    
    @Test
    public void testGetterAndSetter() {
        ConfigResponse configResponse = new ConfigResponse();
        String dataId = "id";
        String group = "group";
        String tenant = "n";
        String content = "abc";
        
        configResponse.setContent(content);
        configResponse.setDataId(dataId);
        configResponse.setGroup(group);
        configResponse.setTenant(tenant);
        
        Assert.assertEquals(dataId, configResponse.getDataId());
        Assert.assertEquals(group, configResponse.getGroup());
        Assert.assertEquals(tenant, configResponse.getTenant());
        Assert.assertEquals(content, configResponse.getContent());
        
    }
    
    @Test
    public void getParameter() {
        ConfigResponse configResponse = new ConfigResponse();
        String dataId = "id";
        String group = "group";
        String tenant = "n";
        String content = "abc";
        
        configResponse.setContent(content);
        configResponse.setDataId(dataId);
        configResponse.setGroup(group);
        configResponse.setTenant(tenant);
        
        Assert.assertEquals(dataId, configResponse.getParameter("dataId"));
        Assert.assertEquals(group, configResponse.getParameter("group"));
        Assert.assertEquals(tenant, configResponse.getParameter("tenant"));
        Assert.assertEquals(content, configResponse.getParameter("content"));
    }
    
    @Test
    public void getConfigContext() {
        ConfigResponse configResponse = new ConfigResponse();
        IConfigContext configContext = configResponse.getConfigContext();
        Assert.assertNotNull(configContext);
    }
}