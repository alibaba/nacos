/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.common.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * PropertyUtils Test.
 *
 * @ClassName: PropertyUtilsTest
 * @Author: ChenHao26
 * @Date: 2022/8/22 13:28
 */
class PropertyUtilsTest {
    
    @Test
    void getProperty() {
        System.setProperty("nacos.test", "google");
        String property = PropertyUtils.getProperty("nacos.test", "xx");
        assertEquals("google", property);
    }
    
    @Test
    void getPropertyWithDefaultValue() {
        String property = PropertyUtils.getProperty("nacos.test", "xx", "test001");
        assertEquals("test001", property);
    }
    
    @Test
    void getProcessorsCount() {
        int processorsCount = PropertyUtils.getProcessorsCount();
        assertNotNull(processorsCount);
    }
}
