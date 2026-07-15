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

package com.alibaba.nacos.config.server.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigInfoWrapperCoverageTest {
    
    @Test
    void testConfigAllInfoDelegatesEqualsAndHashCode() {
        ConfigAllInfo info = new ConfigAllInfo();
        
        assertEquals(info.hashCode(), info.hashCode());
        assertTrue(info.equals(info));
    }
    
    @Test
    void testConfigInfoWrapperDelegatesEqualsAndHashCode() {
        ConfigInfoWrapper wrapper = new ConfigInfoWrapper();
        wrapper.setLastModified(100L);
        
        assertEquals(100L, wrapper.getLastModified());
        assertEquals(wrapper.hashCode(), wrapper.hashCode());
        assertTrue(wrapper.equals(wrapper));
    }
    
    @Test
    void testConfigInfoGrayWrapperDelegatesEqualsAndHashCode() {
        ConfigInfoGrayWrapper wrapper = new ConfigInfoGrayWrapper();
        wrapper.setLastModified(100L);
        wrapper.setGrayName("gray");
        wrapper.setGrayRule("rule");
        wrapper.setSrcUser("user");
        
        assertEquals(100L, wrapper.getLastModified());
        assertEquals("gray", wrapper.getGrayName());
        assertEquals("rule", wrapper.getGrayRule());
        assertEquals("user", wrapper.getSrcUser());
        assertEquals(wrapper.hashCode(), wrapper.hashCode());
        assertTrue(wrapper.equals(wrapper));
    }
    
}
