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

package com.alibaba.nacos.config.server.controller.parameters;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SameNamespaceCloneConfigBeanTest {
    
    @Test
    void testGettersAndSetters() {
        SameNamespaceCloneConfigBean bean = new SameNamespaceCloneConfigBean();
        assertNull(bean.getCfgId());
        assertNull(bean.getDataId());
        assertNull(bean.getGroup());
        
        bean.setCfgId(1L);
        bean.setDataId("testDataId");
        bean.setGroup("testGroup");
        
        assertEquals(1L, bean.getCfgId());
        assertEquals("testDataId", bean.getDataId());
        assertEquals("testGroup", bean.getGroup());
    }
}
