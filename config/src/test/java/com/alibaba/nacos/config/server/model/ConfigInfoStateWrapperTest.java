/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ConfigInfoStateWrapperTest {
    
    @Test
    void testEqualsAndHashCode() {
        ConfigInfoStateWrapper wrapper = newWrapper();
        ConfigInfoStateWrapper sameWrapper = newWrapper();
        
        assertEquals(wrapper, wrapper);
        assertEquals(wrapper, sameWrapper);
        assertEquals(wrapper.hashCode(), sameWrapper.hashCode());
        assertNotEquals(wrapper, null);
        assertNotEquals(wrapper, "wrapper");
        
        sameWrapper.setMd5("different");
        assertNotEquals(wrapper, sameWrapper);
    }
    
    @Test
    void testAccessors() {
        ConfigInfoStateWrapper wrapper = newWrapper();
        
        assertEquals(1L, wrapper.getId());
        assertEquals("dataId", wrapper.getDataId());
        assertEquals("group", wrapper.getGroup());
        assertEquals("tenant", wrapper.getTenant());
        assertEquals(123L, wrapper.getLastModified());
        assertEquals("md5", wrapper.getMd5());
        assertEquals("gray", wrapper.getGrayName());
    }
    
    private ConfigInfoStateWrapper newWrapper() {
        ConfigInfoStateWrapper wrapper = new ConfigInfoStateWrapper();
        wrapper.setId(1L);
        wrapper.setDataId("dataId");
        wrapper.setGroup("group");
        wrapper.setTenant("tenant");
        wrapper.setLastModified(123L);
        wrapper.setMd5("md5");
        wrapper.setGrayName("gray");
        return wrapper;
    }
}
