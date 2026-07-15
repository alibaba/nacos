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

package com.alibaba.nacos.ai.form.prompt;

import com.alibaba.nacos.api.exception.api.NacosApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PromptListFormTest {
    
    @Test
    void validateShouldThrowWhenSearchInvalid() {
        PromptListForm form = new PromptListForm();
        form.setSearch("invalid");
        assertThrows(NacosApiException.class, form::validate);
    }
    
    @Test
    void validateShouldNormalizePageDefaultsWhenPageInvalid() throws NacosApiException {
        PromptListForm form = new PromptListForm();
        form.setPageNo(0);
        form.setPageSize(0);
        
        form.validate();
        
        assertEquals(1, form.getPageNo());
        assertEquals(10, form.getPageSize());
        assertEquals("public", form.getNamespaceId());
    }
    
    @Test
    void validateShouldCapPageSizeToMax() throws NacosApiException {
        PromptListForm form = new PromptListForm();
        form.setPageSize(999);
        
        form.validate();
        
        assertEquals(50, form.getPageSize());
    }
}
