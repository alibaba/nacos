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

package com.alibaba.nacos.console.handler.impl;

import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConditionFunctionEnabledTest {
    
    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(EnvUtil.class, "functionModeType", "");
    }
    
    @Test
    void matchesForAll() {
        ReflectionTestUtils.setField(EnvUtil.class, "functionModeType", "");
        assertTrue(new ConditionFunctionEnabled.ConditionAiEnabled().matches(null, null));
    }
    
    @Test
    void matchesForConfigMatch() {
        ReflectionTestUtils.setField(EnvUtil.class, "functionModeType", "config");
        assertTrue(new ConditionFunctionEnabled.ConditionConfigEnabled().matches(null, null));
    }
    
    @Test
    void matchesForConfigNotMatch() {
        ReflectionTestUtils.setField(EnvUtil.class, "functionModeType", "config");
        assertFalse(new ConditionFunctionEnabled.ConditionAiEnabled().matches(null, null));
    }
    
    @Test
    void matchesForNaming() {
        ReflectionTestUtils.setField(EnvUtil.class, "functionModeType", "naming");
        assertTrue(new ConditionFunctionEnabled.ConditionNamingEnabled().matches(null, null));
    }
}