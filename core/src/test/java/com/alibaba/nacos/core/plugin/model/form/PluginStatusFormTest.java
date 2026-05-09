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

package com.alibaba.nacos.core.plugin.model.form;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginStatusFormTest {
    
    @Test
    void gettersSettersAndDefaults() {
        PluginStatusForm form = new PluginStatusForm();
        assertEquals(false, form.isLocalOnly());
        
        form.setPluginType("auth");
        form.setPluginName("nacos");
        form.setEnabled(true);
        form.setLocalOnly(true);
        
        assertEquals("auth", form.getPluginType());
        assertEquals("nacos", form.getPluginName());
        assertTrue(form.isEnabled());
        assertTrue(form.isLocalOnly());
    }
    
    @Test
    void defaultLocalOnlyIsFalse() {
        PluginStatusForm form = new PluginStatusForm();
        assertFalse(form.isLocalOnly());
    }
}
