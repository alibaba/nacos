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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ConfigResponseTest {
    
    @Test
    void testGetterAndSetter() {
        ConfigResponse configResponse = new ConfigResponse();
        String dataId = "id";
        String group = "group";
        String tenant = "n";
        String content = "abc";
        String type = "yaml";
        
        configResponse.setContent(content);
        configResponse.setDataId(dataId);
        configResponse.setGroup(group);
        configResponse.setTenant(tenant);
        configResponse.setConfigType(type);
        
        assertEquals(dataId, configResponse.getDataId());
        assertEquals(group, configResponse.getGroup());
        assertEquals(tenant, configResponse.getTenant());
        assertEquals(content, configResponse.getContent());
        assertEquals(type, configResponse.getConfigType());
    }
    
    @Test
    void getParameter() {
        ConfigResponse configResponse = new ConfigResponse();
        String dataId = "id";
        String group = "group";
        String tenant = "n";
        String content = "abc";
        String custom = "custom";
        
        configResponse.setContent(content);
        configResponse.setDataId(dataId);
        configResponse.setGroup(group);
        configResponse.setTenant(tenant);
        configResponse.putParameter(custom, custom);
        
        assertEquals(dataId, configResponse.getParameter("dataId"));
        assertEquals(group, configResponse.getParameter("group"));
        assertEquals(tenant, configResponse.getParameter("tenant"));
        assertEquals(content, configResponse.getParameter("content"));
        assertEquals(custom, configResponse.getParameter("custom"));
    }
    
    @Test
    void getConfigContext() {
        ConfigResponse configResponse = new ConfigResponse();
        IConfigContext configContext = configResponse.getConfigContext();
        assertNotNull(configContext);
    }
}