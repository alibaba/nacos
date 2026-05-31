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

import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.config.server.service.capacity.CapacityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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
    
    @Test
    void testFullConstructor() {
        ConfigForm form = new ConfigForm("d", "g", "ns", "c", "t",
            "app", "user", "tags", "desc", "use", "effect", "json",
            "schema");
        assertEquals("d", form.getDataId());
        assertEquals("g", form.getGroup());
        assertEquals("ns", form.getNamespaceId());
        assertEquals("c", form.getContent());
        assertEquals("t", form.getTag());
        assertEquals("app", form.getAppName());
        assertEquals("user", form.getSrcUser());
        assertEquals("tags", form.getConfigTags());
        assertEquals("desc", form.getDesc());
        assertEquals("use", form.getUse());
        assertEquals("effect", form.getEffect());
        assertEquals("json", form.getType());
        assertEquals("schema", form.getSchema());
    }
    
    @Test
    void testValidateBlankDataId() {
        ConfigForm form = new ConfigForm();
        form.setGroup("g");
        assertThrows(NacosApiException.class, form::validate);
    }
    
    @Test
    void testValidateBlankGroup() {
        ConfigForm form = new ConfigForm();
        form.setDataId("d");
        assertThrows(NacosApiException.class, form::validate);
    }
    
    @Test
    void testValidateSuccess() {
        ConfigForm form = new ConfigForm();
        form.setDataId("d");
        form.setGroup("g");
        assertDoesNotThrow(form::validate);
    }
    
    @Test
    void testValidateWithContentBlank() {
        ConfigForm form = new ConfigForm();
        form.setDataId("d");
        form.setGroup("g");
        assertThrows(NacosApiException.class,
            form::validateWithContent);
    }
    
    @Test
    void testValidateWithContentSuccess() {
        ConfigForm form = new ConfigForm();
        form.setDataId("d");
        form.setGroup("g");
        form.setContent("content");
        assertDoesNotThrow(form::validateWithContent);
    }
    
    @Test
    void testConfigFormV3ValidateMissingGroupName() {
        ConfigFormV3 form = new ConfigFormV3();
        form.setDataId("d");
        
        assertThrows(NacosApiException.class, form::validate);
    }
    
    @Test
    void testConfigFormV3ValidateUsesGroupName() {
        ConfigFormV3 form = new ConfigFormV3();
        form.setDataId("d");
        form.setGroupName("groupName");
        
        assertDoesNotThrow(form::validate);
        assertEquals("groupName", form.getGroup());
    }
    
    @Test
    void testConfigFormV3BlurSearchValidateDefaultsNullFields() throws NacosApiException {
        ConfigFormV3 form = new ConfigFormV3();
        
        form.blurSearchValidate();
        
        assertEquals("", form.getGroupName());
        assertEquals("", form.getGroup());
        assertEquals("", form.getDataId());
    }
    
    @Test
    void testUpdateCapacityFormValidateRequiresCapacityField() {
        UpdateCapacityForm form = new UpdateCapacityForm();
        
        assertThrows(NacosApiException.class, form::validate);
    }
    
    @Test
    void testUpdateCapacityFormValidateWithCapacityField() {
        UpdateCapacityForm form = new UpdateCapacityForm();
        form.setQuota(1);
        form.setMaxSize(2);
        form.setMaxAggrCount(3);
        form.setMaxAggrSize(4);
        
        assertDoesNotThrow(form::validate);
        assertEquals(1, form.getQuota());
        assertEquals(2, form.getMaxSize());
        assertEquals(3, form.getMaxAggrCount());
        assertEquals(4, form.getMaxAggrSize());
    }
    
    @Test
    void testUpdateCapacityFormChecksNamespaceOrGroupName() {
        UpdateCapacityForm form = new UpdateCapacityForm();
        CapacityService capacityService = mock(CapacityService.class);
        
        assertThrows(NacosApiException.class,
            () -> form.checkNamespaceIdAndGroupName(capacityService));
        verify(capacityService).initAllCapacity();
        
        form.setGroupName("group");
        form.setNamespaceId("namespace");
        assertDoesNotThrow(() -> form.checkNamespaceIdAndGroupName(capacityService));
        assertEquals("group", form.getGroupName());
        assertEquals("namespace", form.getNamespaceId());
    }
    
    @Test
    void testGettersSetters() {
        ConfigForm form = new ConfigForm();
        form.setEncryptedDataKey("ek");
        form.setGrayName("gn");
        form.setGrayRuleExp("exp");
        form.setGrayVersion("1.0");
        form.setGrayPriority(10);
        assertEquals("ek", form.getEncryptedDataKey());
        assertEquals("gn", form.getGrayName());
        assertEquals("exp", form.getGrayRuleExp());
        assertEquals("1.0", form.getGrayVersion());
        assertEquals(10, form.getGrayPriority());
    }
}
