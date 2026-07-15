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
import static org.junit.jupiter.api.Assertions.assertThrows;

class NamespaceFormTest {
    
    @Test
    void testDefaultConstructorAndGettersSetters() {
        NamespaceForm form = new NamespaceForm();
        form.setNamespaceId("ns-1");
        form.setNamespaceName("my-namespace");
        form.setNamespaceDesc("description");
        
        assertEquals("ns-1", form.getNamespaceId());
        assertEquals("my-namespace", form.getNamespaceName());
        assertEquals("description", form.getNamespaceDesc());
    }
    
    @Test
    void testAllArgsConstructor() {
        NamespaceForm form = new NamespaceForm("id1", "name1", "desc1");
        assertEquals("id1", form.getNamespaceId());
        assertEquals("name1", form.getNamespaceName());
        assertEquals("desc1", form.getNamespaceDesc());
    }
    
    @Test
    void testValidateSuccess() throws NacosApiException {
        NamespaceForm form = new NamespaceForm("ns-1", "my-namespace", "desc");
        form.validate();
        // no exception
    }
    
    @Test
    void testValidateFailsWhenNamespaceIdNull() {
        NamespaceForm form = new NamespaceForm();
        form.setNamespaceName("name");
        form.setNamespaceDesc("desc");
        
        NacosApiException ex = assertThrows(NacosApiException.class, form::validate);
        assertEquals(HttpStatus.BAD_REQUEST.value(), ex.getErrCode());
        assertEquals(ErrorCode.PARAMETER_MISSING.getCode(), ex.getDetailErrCode());
        assertEquals("required parameter 'namespaceId' is missing", ex.getErrMsg());
    }
    
    @Test
    void testValidateFailsWhenNamespaceNameNull() {
        NamespaceForm form = new NamespaceForm();
        form.setNamespaceId("ns-1");
        form.setNamespaceDesc("desc");
        
        NacosApiException ex = assertThrows(NacosApiException.class, form::validate);
        assertEquals(HttpStatus.BAD_REQUEST.value(), ex.getErrCode());
        assertEquals(ErrorCode.PARAMETER_MISSING.getCode(), ex.getDetailErrCode());
        assertEquals("required parameter 'namespaceName' is missing", ex.getErrMsg());
    }
}
