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

package com.alibaba.nacos.core.plugin.condition;

import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConditionOnClusterModeTest {
    
    private ConditionOnClusterMode condition;
    
    @BeforeEach
    void setUp() {
        condition = new ConditionOnClusterMode();
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(null);
    }
    
    @Test
    void testMatchesWhenCluster() {
        EnvUtil.setIsStandalone(false);
        boolean result = condition.matches(null, null);
        assertTrue(result);
    }
    
    @Test
    void testMatchesWhenStandalone() {
        EnvUtil.setIsStandalone(true);
        boolean result = condition.matches(null, null);
        assertFalse(result);
    }
}
