/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.model.form;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * ConfigFormTest
 *
 * @author Ken
 */
public class ConfigFormTest {
    
    private ConfigForm original;
    
    @BeforeEach
    void setUp() {
        // 1. 初始化并填充原始对象的所有类型字段
        original = new ConfigForm();
        original.setDataId("dataId");
        original.setGroup("groupId");
        original.setNamespaceId("namespaceId");
        original.setContent("content");
        original.setTag("tag");
        original.setAppName("appName");
        original.setSrcUser("srcUser");
        original.setConfigTags("configTag");
        original.setDesc("desc");
        original.setUse("use");
        original.setEffect("effect");
        original.setType("yaml");
        original.setSchema("schema");
        original.setEncryptedDataKey("encryptedDataKey");
        original.setGrayName("grayName");
        original.setGrayRuleExp("grayRuleExp");
        original.setGrayVersion("grayVersion");
        original.setGrayPriority(5);
    }
    
    @Test
    void testCloneInstance() {
        ConfigForm cloned = original.clone();
        
        // 验证非空
        assertNotNull(cloned);
        // 验证内存地址不同（不是同一个引用）
        assertNotSame(original, cloned);
        // 验证类类型一致
        assertEquals(original.getClass(), cloned.getClass());
    }
    
    @Test
    void testCloneFields() {
        ConfigForm cloned = original.clone();
        
        // 逐一验证关键属性
        assertEquals(original.getDataId(), cloned.getDataId());
        assertEquals(original.getGroup(), cloned.getGroup());
        assertEquals(original.getNamespaceId(), cloned.getNamespaceId());
        assertEquals(original.getContent(), cloned.getContent());
        assertEquals(original.getAppName(), cloned.getAppName());
        assertEquals(original.getGrayPriority(), cloned.getGrayPriority());
        assertEquals(original.getGrayName(), cloned.getGrayName());
        assertEquals(original.getDesc(), cloned.getDesc());
    }
    
    @Test
    void testDeepCopyIndependence() {
        ConfigForm cloned = original.clone();
        
        // 修改克隆体的属性
        String newContent = "new-content-modified";
        cloned.setContent(newContent);
        
        // 断言原对象的属性没有改变
        assertNotEquals(original.getContent(), cloned.getContent());
        assertEquals("content", original.getContent());
    }
}
