/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.model.form;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceFormTest {
    
    @Test
    void testFillDefaultValueForNamespaceAndGroup() throws NacosApiException {
        ServiceForm form = new ServiceForm();
        form.setServiceName("testService");
        form.validate();
        assertEquals(Constants.DEFAULT_NAMESPACE_ID, form.getNamespaceId());
        assertEquals(Constants.DEFAULT_GROUP, form.getGroupName());
    }
    
    @Test
    void testFillDefaultValueForServiceAttributes() throws NacosApiException {
        ServiceForm form = new ServiceForm();
        form.setServiceName("testService");
        form.validate();
        assertFalse(form.getEphemeral());
        assertEquals(0.0F, form.getProtectThreshold());
        assertEquals("", form.getMetadata());
        assertEquals("", form.getSelector());
    }
    
    @Test
    void testFillDefaultValueWhenValuesAreProvided() throws NacosApiException {
        ServiceForm form = new ServiceForm();
        form.setNamespaceId("customNamespace");
        form.setGroupName("customGroup");
        form.setServiceName("testService");
        form.setEphemeral(true);
        form.setProtectThreshold(0.5F);
        form.setMetadata("{\"key\":\"value\"}");
        form.setSelector("{\"type\":\"label\"}");
        form.validate();
        assertEquals("customNamespace", form.getNamespaceId());
        assertEquals("customGroup", form.getGroupName());
        assertTrue(form.getEphemeral());
        assertEquals(0.5F, form.getProtectThreshold());
        assertEquals("{\"key\":\"value\"}", form.getMetadata());
        assertEquals("{\"type\":\"label\"}", form.getSelector());
    }
    
    @Test
    void testValidateThrowsExceptionWhenServiceNameIsBlank() {
        ServiceForm form = new ServiceForm();
        assertThrows(NacosApiException.class, () -> form.validate());
    }
}