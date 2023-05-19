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

public class ConfigRequestTest {
    
    @Test
    public void testGetterAndSetter() {
        ConfigRequest configRequest = new ConfigRequest();
        String dataId = "id";
        String group = "group";
        String tenant = "n";
        String content = "abc";
        String type = "properties";
        
        configRequest.setContent(content);
        configRequest.setDataId(dataId);
        configRequest.setGroup(group);
        configRequest.setTenant(tenant);
        configRequest.setType(type);
        
        Assert.assertEquals(dataId, configRequest.getDataId());
        Assert.assertEquals(group, configRequest.getGroup());
        Assert.assertEquals(tenant, configRequest.getTenant());
        Assert.assertEquals(content, configRequest.getContent());
        Assert.assertEquals(type, configRequest.getType());
        
    }
    
    @Test
    public void testGetParameter() {
        ConfigRequest configRequest = new ConfigRequest();
        String dataId = "id";
        String group = "group";
        String tenant = "n";
        String content = "abc";
        
        configRequest.setContent(content);
        configRequest.setDataId(dataId);
        configRequest.setGroup(group);
        configRequest.setTenant(tenant);
        
        Assert.assertEquals(dataId, configRequest.getParameter("dataId"));
        Assert.assertEquals(group, configRequest.getParameter("group"));
        Assert.assertEquals(tenant, configRequest.getParameter("tenant"));
        Assert.assertEquals(content, configRequest.getParameter("content"));
    }
    
    @Test
    public void testGetConfigContext() {
        ConfigRequest configRequest = new ConfigRequest();
        IConfigContext configContext = configRequest.getConfigContext();
        Assert.assertNotNull(configContext);
    }
}
