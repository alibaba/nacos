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

import com.alibaba.nacos.common.utils.ThreadUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ActiveProfiles("empty")
@SpringBootTest(classes = EnvUtilWithConfigTest.class)
class EnvUtilWithoutConfigTest {
    
    @Autowired
    private Environment environment;
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment((ConfigurableEnvironment) environment);
    }
    
    @Test
    void testGetAvailableProcessors() {
        int expected = ThreadUtils.getSuitableThreadCount(1);
        int actual = EnvUtil.getAvailableProcessors();
        assertEquals(expected, actual);
    }
    
    @Test
    void testGetAvailableProcessorsWithMultiple() {
        int expected = ThreadUtils.getSuitableThreadCount(2);
        int actual = EnvUtil.getAvailableProcessors(2);
        assertEquals(expected, actual);
    }
    
    @Test
    void testGetAvailableProcessorsWithScale() {
        int expected = ThreadUtils.getSuitableThreadCount(1);
        int actual = EnvUtil.getAvailableProcessors(0.5);
        assertEquals((int) (expected * 0.5), actual);
    }
}
