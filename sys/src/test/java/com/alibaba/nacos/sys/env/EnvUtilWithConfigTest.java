/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.sys.env;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ActiveProfiles("test")
@SpringBootTest(classes = EnvUtilWithConfigTest.class)
class EnvUtilWithConfigTest {
    
    private static final int SETTING_PROCESSORS = 10;
    
    @Autowired
    private Environment environment;
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment((ConfigurableEnvironment) environment);
    }
    
    @Test
    void testGetAvailableProcessors() {
        int actual = EnvUtil.getAvailableProcessors();
        assertEquals(SETTING_PROCESSORS, actual);
    }
    
    @Test
    void testGetAvailableProcessorsWithMultiple() {
        int actual = EnvUtil.getAvailableProcessors(2);
        assertEquals(SETTING_PROCESSORS * 2, actual);
    }
    
    @Test
    void testGetAvailableProcessorsWithScale() {
        int actual = EnvUtil.getAvailableProcessors(0.5);
        assertEquals((int) (SETTING_PROCESSORS * 0.5), actual);
    }
}
