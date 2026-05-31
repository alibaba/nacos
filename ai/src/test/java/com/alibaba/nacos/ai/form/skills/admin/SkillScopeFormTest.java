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

package com.alibaba.nacos.ai.form.skills.admin;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SkillScopeFormTest {
    
    @Test
    void testValidateSuccess() {
        SkillScopeForm form = new SkillScopeForm();
        form.setSkillName("my-skill");
        form.setScope("PUBLIC");
        assertDoesNotThrow(form::validate);
    }
    
    @Test
    void testValidateSuccessPrivateLowerCase() {
        SkillScopeForm form = new SkillScopeForm();
        form.setSkillName("my-skill");
        form.setScope("private");
        assertDoesNotThrow(form::validate);
    }
    
    @Test
    void testValidateMissingSkillName() {
        SkillScopeForm form = new SkillScopeForm();
        form.setScope("PUBLIC");
        NacosApiException ex = assertThrows(NacosApiException.class, form::validate);
        assertEquals(NacosException.INVALID_PARAM, ex.getErrCode());
    }
    
    @Test
    void testValidateMissingScope() {
        SkillScopeForm form = new SkillScopeForm();
        form.setSkillName("my-skill");
        NacosApiException ex = assertThrows(NacosApiException.class, form::validate);
        assertEquals(NacosException.INVALID_PARAM, ex.getErrCode());
    }
    
    @Test
    void testValidateInvalidScope() {
        SkillScopeForm form = new SkillScopeForm();
        form.setSkillName("my-skill");
        form.setScope("INVALID");
        NacosApiException ex = assertThrows(NacosApiException.class, form::validate);
        assertEquals(NacosException.INVALID_PARAM, ex.getErrCode());
    }
}
