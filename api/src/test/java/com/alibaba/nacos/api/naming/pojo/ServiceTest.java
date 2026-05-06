/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.naming.pojo;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.selector.NoneSelector;
import com.alibaba.nacos.api.selector.Selector;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServiceTest {
    
    @Test
    void testSetAndGet() {
        Service service = new Service();
        assertNull(service.getName());
        assertNull(service.getGroupName());
        assertEquals(0.0f, service.getProtectThreshold(), 0.1);
        assertTrue(service.getMetadata().isEmpty());
        service.setName("service");
        service.setGroupName("group");
        service.setProtectThreshold(1.0f);
        HashMap<String, String> metadata = new HashMap<>();
        service.setMetadata(metadata);
        service.addMetadata("a", "b");
        assertEquals("service", service.getName());
        assertEquals("group", service.getGroupName());
        assertEquals(1.0f, service.getProtectThreshold(), 0.1);
        assertEquals(1, service.getMetadata().size());
        assertEquals("b", service.getMetadata().get("a"));
    }
    
    @Test
    void testToString() {
        Service service = new Service();
        service.setName("service");
        service.setGroupName("group");
        service.setProtectThreshold(1.0f);
        service.setMetadata(Collections.singletonMap("a", "b"));
    }
    
    @Test
    void testNamespaceId() {
        Service service = new Service();
        assertNull(service.getNamespaceId());
        
        service.setNamespaceId("namespace");
        assertEquals("namespace", service.getNamespaceId());
    }
    
    @Test
    void testEphemeral() {
        Service service = new Service();
        // 默认值应为 false
        assertEquals(false, service.isEphemeral());
        
        service.setEphemeral(true);
        assertEquals(true, service.isEphemeral());
    }
    
    @Test
    void testSelector() {
        Service service = new Service();
        // 默认选择器应该是 NoneSelector
        assertNotNull(service.getSelector());
        assertTrue(service.getSelector() instanceof NoneSelector);
        
        Selector selector = new NoneSelector();
        service.setSelector(selector);
        assertEquals(selector, service.getSelector());
    }
    
    @Test
    void testFillDefaultValue() {
        Service service = new Service();
        // 初始时 namespaceId 和 groupName 都是 null
        assertNull(service.getNamespaceId());
        assertNull(service.getGroupName());
        
        // 调用 fillDefaultValue 方法
        service.fillDefaultValue();
        
        // 应该被设置为默认值
        assertEquals(Constants.DEFAULT_NAMESPACE_ID, service.getNamespaceId());
        assertEquals(Constants.DEFAULT_GROUP, service.getGroupName());
    }
    
    @Test
    void testValidateSuccess() throws NacosApiException {
        Service service = new Service();
        service.setName("service");
        
        // 验证应该成功，不会抛出异常
        service.validate();
    }
    
    @Test
    void testValidateFailure() {
        Service service = new Service();
        // 没有设置 name，验证应该失败
        
        assertThrows(NacosApiException.class, () -> {
            service.validate();
        });
    }
    
    @Test
    void testValidateWithFillDefaultValue() throws NacosApiException {
        Service service = new Service();
        service.setName("service");
        // namespaceId 和 groupName 未设置
        
        // 验证时会调用 fillDefaultValue
        service.validate();
        
        // 验证后应该填充默认值
        assertEquals(Constants.DEFAULT_NAMESPACE_ID, service.getNamespaceId());
        assertEquals(Constants.DEFAULT_GROUP, service.getGroupName());
    }
}