/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.namespace.model.form;

import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateNamespaceFormTest {
    
    @Test
    void testGetSetCustomNamespaceId() {
        CreateNamespaceForm form = new CreateNamespaceForm();
        form.setCustomNamespaceId("custom-id-123");
        assertEquals("custom-id-123", form.getCustomNamespaceId());
    }
    
    @Test
    void testValidateFailsWhenNamespaceNameNull() {
        CreateNamespaceForm form = new CreateNamespaceForm();
        form.setCustomNamespaceId("id");
        
        NacosApiException ex = assertThrows(NacosApiException.class, form::validate);
        assertEquals(HttpStatus.BAD_REQUEST.value(), ex.getErrCode());
        assertEquals(ErrorCode.PARAMETER_MISSING.getCode(), ex.getDetailErrCode());
        assertEquals("required parameter 'namespaceName' is missing", ex.getErrMsg());
    }
    
    @Test
    void testValidateWithBlankCustomNamespaceIdGeneratesUuid() throws NacosApiException {
        CreateNamespaceForm form = new CreateNamespaceForm();
        form.setNamespaceName("my-namespace");
        form.setCustomNamespaceId("");
        
        form.validate();
        
        assertNotNull(form.getCustomNamespaceId());
        assertTrue(form.getCustomNamespaceId().length() > 0);
        // UUID format: 8-4-4-4-12
        assertTrue(form.getCustomNamespaceId()
            .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
    }
    
    @Test
    void testValidateWithNullCustomNamespaceIdGeneratesUuid() throws NacosApiException {
        CreateNamespaceForm form = new CreateNamespaceForm();
        form.setNamespaceName("my-namespace");
        
        form.validate();
        
        assertNotNull(form.getCustomNamespaceId());
        assertTrue(form.getCustomNamespaceId()
            .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
    }
    
    @Test
    void testValidateWithNonBlankCustomNamespaceIdTrimsValue() throws NacosApiException {
        CreateNamespaceForm form = new CreateNamespaceForm();
        form.setNamespaceName("my-namespace");
        form.setCustomNamespaceId("  my-custom-id  ");
        
        form.validate();
        
        assertEquals("my-custom-id", form.getCustomNamespaceId());
    }
    
    @Test
    void testValidateSuccessWithCustomId() throws NacosApiException {
        CreateNamespaceForm form = new CreateNamespaceForm();
        form.setNamespaceName("my-namespace");
        form.setCustomNamespaceId("existing-id");
        
        form.validate();
        
        assertEquals("existing-id", form.getCustomNamespaceId());
    }
}
