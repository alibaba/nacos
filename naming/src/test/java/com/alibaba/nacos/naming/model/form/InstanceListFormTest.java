/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InstanceListFormTest {
    
    @Test
    void testFillDefaultValueWhenClusterNameIsBlank() throws NacosApiException {
        InstanceListForm form = new InstanceListForm();
        form.setServiceName("testService");
        form.validate();
        assertEquals("", form.getClusterName());
    }
    
    @Test
    void testFillDefaultValueWhenClusterNameIsProvided() throws NacosApiException {
        InstanceListForm form = new InstanceListForm();
        form.setServiceName("testService");
        form.setClusterName("myCluster");
        form.validate();
        assertEquals("myCluster", form.getClusterName());
    }
    
    @Test
    void testFillDefaultValueForNamespaceAndGroup() throws NacosApiException {
        InstanceListForm form = new InstanceListForm();
        form.setServiceName("testService");
        form.validate();
        assertEquals(Constants.DEFAULT_NAMESPACE_ID, form.getNamespaceId());
        assertEquals(Constants.DEFAULT_GROUP, form.getGroupName());
    }
    
    @Test
    void testValidateThrowsExceptionWhenServiceNameIsBlank() {
        InstanceListForm form = new InstanceListForm();
        assertThrows(NacosApiException.class, () -> form.validate());
    }
    
    @Test
    void testObjectMethods() throws NacosApiException {
        InstanceListForm form = createForm("service");
        InstanceListForm same = createForm("service");
        InstanceListForm different = createForm("other");
        form.validate();
        same.validate();
        
        assertEquals(form, form);
        assertEquals(form, same);
        assertEquals(form.hashCode(), same.hashCode());
        assertNotEquals(form, different);
        assertNotEquals(form, null);
        assertNotEquals(form, new Object());
    }
    
    private InstanceListForm createForm(String serviceName) {
        InstanceListForm form = new InstanceListForm();
        form.setServiceName(serviceName);
        return form;
    }
}
