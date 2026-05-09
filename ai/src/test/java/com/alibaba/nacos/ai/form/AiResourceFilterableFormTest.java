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

package com.alibaba.nacos.ai.form;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link AiResourceFilterableForm}.
 */
class AiResourceFilterableFormTest {
    
    @Test
    void validateShouldPassWhenBothFieldsNull() {
        AiResourceFilterableForm form = new AiResourceFilterableForm();
        assertDoesNotThrow(form::validate);
    }
    
    @Test
    void validateShouldPassWhenBothFieldsEmpty() {
        AiResourceFilterableForm form = new AiResourceFilterableForm();
        form.setOwner("");
        form.setScope("");
        assertDoesNotThrow(form::validate);
    }
    
    @Test
    void validateShouldPassWhenScopeIsPublicLowercase() {
        AiResourceFilterableForm form = new AiResourceFilterableForm();
        form.setScope("public");
        assertDoesNotThrow(form::validate);
    }
    
    @Test
    void validateShouldPassWhenScopeIsPublicUppercase() {
        AiResourceFilterableForm form = new AiResourceFilterableForm();
        form.setScope("PUBLIC");
        assertDoesNotThrow(form::validate);
    }
    
    @Test
    void validateShouldPassWhenScopeIsPublicMixedCase() {
        AiResourceFilterableForm form = new AiResourceFilterableForm();
        form.setScope("Public");
        assertDoesNotThrow(form::validate);
    }
    
    @Test
    void validateShouldPassWhenScopeIsPrivateLowercase() {
        AiResourceFilterableForm form = new AiResourceFilterableForm();
        form.setScope("private");
        assertDoesNotThrow(form::validate);
    }
    
    @Test
    void validateShouldPassWhenScopeIsPrivateUppercase() {
        AiResourceFilterableForm form = new AiResourceFilterableForm();
        form.setScope("PRIVATE");
        assertDoesNotThrow(form::validate);
    }
    
    @Test
    void validateShouldThrowWhenScopeIsInvalid() {
        AiResourceFilterableForm form = new AiResourceFilterableForm();
        form.setScope("INVALID_SCOPE");
        NacosApiException exception = assertThrows(NacosApiException.class, form::validate);
        assertEquals(NacosException.INVALID_PARAM, exception.getErrCode());
    }
    
    @Test
    void validateShouldThrowWhenScopeIsRandomString() {
        AiResourceFilterableForm form = new AiResourceFilterableForm();
        form.setScope("all");
        NacosApiException exception = assertThrows(NacosApiException.class, form::validate);
        assertEquals(NacosException.INVALID_PARAM, exception.getErrCode());
    }
    
    @Test
    void validateShouldPassWithOwnerSetAndNoScope() {
        AiResourceFilterableForm form = new AiResourceFilterableForm();
        form.setOwner("alice");
        assertDoesNotThrow(form::validate);
    }
    
    @Test
    void validateShouldPassWithOwnerAndValidScope() {
        AiResourceFilterableForm form = new AiResourceFilterableForm();
        form.setOwner("alice");
        form.setScope("PUBLIC");
        assertDoesNotThrow(form::validate);
    }
    
    @Test
    void gettersAndSettersShouldWork() {
        AiResourceFilterableForm form = new AiResourceFilterableForm();
        form.setOwner("bob");
        form.setScope("PRIVATE");
        form.setBizTag("retail");
        assertEquals("bob", form.getOwner());
        assertEquals("PRIVATE", form.getScope());
        assertEquals("retail", form.getBizTag());
    }
}
