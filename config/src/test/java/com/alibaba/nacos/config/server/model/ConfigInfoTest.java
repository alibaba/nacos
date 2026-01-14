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

package com.alibaba.nacos.config.server.model;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.consistency.IdGenerator;
import com.alibaba.nacos.core.distributed.id.SnowFlowerIdGenerator;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigInfoTest {
    
    @Test
    void testPrecisionIssue() throws Exception {
        EnvUtil.setEnvironment(new StandardEnvironment());
        IdGenerator generator = new SnowFlowerIdGenerator();
        long expected = generator.nextId();
        ConfigInfo configInfo = new ConfigInfo();
        configInfo.setId(expected);
        String json = JacksonUtils.toJson(configInfo);
        ConfigInfo actual = JacksonUtils.toObj(json, ConfigInfo.class);
        assertEquals(expected, actual.getId());
        
    }
    
    @Test
    void testConfigInfoWithDescAndTags() {
        ConfigInfo configInfo = new ConfigInfo();
        configInfo.setDataId("test.properties");
        configInfo.setGroup("DEFAULT_GROUP");
        configInfo.setTenant("public");
        configInfo.setContent("key=value");
        configInfo.setDesc("测试配置描述");
        configInfo.setConfigTags("tag1,tag2,tag3");
        
        assertEquals("test.properties", configInfo.getDataId());
        assertEquals("DEFAULT_GROUP", configInfo.getGroup());
        assertEquals("public", configInfo.getTenant());
        assertEquals("key=value", configInfo.getContent());
        assertEquals("测试配置描述", configInfo.getDesc());
        assertEquals("tag1,tag2,tag3", configInfo.getConfigTags());
    }
    
    @Test
    void testConfigInfoWithNullDescAndTags() {
        ConfigInfo configInfo = new ConfigInfo();
        configInfo.setDataId("test.properties");
        configInfo.setGroup("DEFAULT_GROUP");
        configInfo.setTenant("public");
        configInfo.setContent("key=value");
        configInfo.setDesc(null);
        configInfo.setConfigTags(null);
        
        assertEquals("test.properties", configInfo.getDataId());
        assertEquals("DEFAULT_GROUP", configInfo.getGroup());
        assertEquals("public", configInfo.getTenant());
        assertEquals("key=value", configInfo.getContent());
        assertEquals(null, configInfo.getDesc());
        assertEquals(null, configInfo.getConfigTags());
    }
    
    @Test
    void testConfigInfoInheritance() {
        // 测试 ConfigAllInfo 继承 ConfigInfo 后的字段访问
        ConfigAllInfo configAllInfo = new ConfigAllInfo();
        configAllInfo.setDataId("test.properties");
        configAllInfo.setGroup("DEFAULT_GROUP");
        configAllInfo.setDesc("继承的描述字段");
        configAllInfo.setConfigTags("inherited,tags");
        
        assertEquals("test.properties", configAllInfo.getDataId());
        assertEquals("DEFAULT_GROUP", configAllInfo.getGroup());
        assertEquals("继承的描述字段", configAllInfo.getDesc());
        assertEquals("inherited,tags", configAllInfo.getConfigTags());
    }
    
}
